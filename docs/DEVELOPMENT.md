# Development guide

Setting up, building, testing and the conventions this repository keeps. The plugin's own
documentation is in [guide/](guide/README.md) for server owners and [API.md](API.md) for plugin
authors; how each subsystem is designed is in [GATES.md](GATES.md), [RINGS.md](RINGS.md),
[BEAMS.md](BEAMS.md) and [MIRRORS.md](MIRRORS.md).

## Building and testing

JDK 17 and Maven 3.8+.

```bash
mvn test                    # JUnit 5 + Mockito; no live server needed
mvn -DskipTests package     # target/WormholeXTreme-<version>.jar
```

`package` writes the jar twice: once as `WormholeXTreme-<version>.jar`, and once as
`WormholeXTreme.jar`. They are byte for byte the same. The versioned one is what CI uploads and
what a release attaches, because a download should say which version it is; the unversioned one
is for anything local that has to keep pointing at the latest build — a symlink into a test
server's `plugins/`, a copy script — without being repointed every time the version moves.

Tests live in `src/test/java/`, mock the Bukkit API, and run against every supported Minecraft
version in CI, so anything that only works on one of them is caught there.

Tests in `src/mockbukkit/java/` load the whole plugin onto [MockBukkit](https://mockbukkit.org)'s
simulated server instead. Each MockBukkit is built for one Paper version and a newer Java, so
they need the profile, that JDK and that Paper API; against the default Spigot API they compile,
then fail with a linkage error. CI runs them twice, in the Paper 1.21.11 and 26.2 jobs:

```bash
mvn verify -Pmodern-api,mockbukkit -Dpaper.api.version=1.21.11-R0.1-SNAPSHOT   # JDK 21
mvn verify -Pmodern-api,mockbukkit -Dpaper.api.version=26.2.build.124-stable -Dmockbukkit.artifact=mockbukkit-v26.2 -Dmockbukkit.version=4.116.1 -Dmockbukkit.release=25   # JDK 25
```

Run `clean` when switching between the two, because Maven does not recompile for a change of
release alone. After 26.2, the 1.21 command fails in the ordinary tests with an
`UnsupportedClassVersionError`. After 1.21, the 26.2 command passes without having tested 26.2 at
all: the classes built for 1.21 simply run again.

MockBukkit's older line for 1.20 is left alone: it is abandoned, and in another package, so
`src/mockbukkit/` could not compile against both.

`JourneysOnMockServerTest` takes a player through a gate, a beam, a ring and a mirror, each set up
by command, and a following pet through a gate and by beam into another world. It checks where
they arrive and that the trip leaves nothing new running. Annotate
a class `@OnMockServer`, and start and stop the server with `MockServerSupport`:

- **They run in a JVM of their own**, by the annotation's `mockbukkit` tag, which `-Dtest` does
  not override. A Mockito test that touches `org.bukkit.Tag` with no server leaves the class
  unusable for the rest of its JVM, and every MockBukkit player needs it; the other way round,
  MockBukkit's server is global.
- **MockBukkit reports a call to a method it has not implemented as a skipped test**, not a
  failure. The annotation turns it into a failure, so a journey cut short cannot pass. It only
  sees what reaches the test thread; the plugin's own catches swallow one.
- **Asynchronous tasks run on the next tick, on the main thread.** MockBukkit runs them on a
  pool, and a task the pool scheduled back onto the main thread was sometimes lost: a mirror
  capture then never finished, and the journeys failed now and then.
- **"Nothing new running" waits on the tasks, not a number of ticks.** `settle` runs a
  minute, then until no one-off task is pending, up to 30 minutes; every repeating task left
  must have been running before the trip. A gate's shutdown is timed partly from the clock, so
  a fixed wait passed or failed with the machine's speed.
- **`MockServerSupport` stands in for the unimplemented methods a trip reaches**: `isPassable`,
  `isOccluding`, chunk tickets, `unloadChunkRequest`, a player's target block, line of sight and
  view distance, and the block data a mirror's view clones, turns and sends. Each is an
  approximation (passable is "not solid", a view is never really drawn), and one reaches into
  `WorldMock` by reflection, so a MockBukkit upgrade can break it. Add a stand-in there, not in a
  test.
- **Gate previews are not covered, nor how a mirror's view looks.** Previews spawn entities
  hidden per player, which MockBukkit cannot do; the plugin catches that and carries on. A
  mirror's capture is taken, 4 blocks deep, and its view worked out, but nothing checks either.
- **Time here is ticks, not the clock.** A limit measured in milliseconds, such as
  `max_open_seconds`, never passes.

Static state survives `MockBukkit.unmock()`, which a real server never sees because each load
gets a new classloader. A second load in one JVM logs every shape as a duplicate, so load the
plugin once per class, in `@BeforeAll`. `MockServerSupport.stop()` puts the settings back as they
were before the load and empties the gate, ring, beam and mirror registries, so the next
MockBukkit class in that JVM starts from the same state whichever order they run in.

The Sonar job builds without the profile, so it never analyses `src/mockbukkit/`. Check those
files with PMD before pushing; `generate-test-sources` is what adds the folder:

```bash
mvn generate-test-sources pmd:pmd -Dformat=csv -DincludeTests=true -Pmodern-api,mockbukkit -Dpaper.api.version=1.21.11-R0.1-SNAPSHOT
```

## Static analysis

- **SpotBugs** runs in CI and fails the build on what it finds. Locally:
  `mvn -DskipTests spotbugs:check`.
- **PMD** runs only when asked and never fails a build: `mvn pmd:pmd -Dformat=csv`, findings in
  `target/pmd.csv`. It answers, in about ten seconds, part of what SonarCloud would say minutes
  later on the PR. `pmd-ruleset.xml` records which rules were measured to agree with SonarCloud
  on this codebase and what it deliberately leaves out. A clean run is meaningful; a single
  finding is a lead worth confirming, not a verdict.
- **SonarCloud** is the authority, and CI fails a pull request that has open findings. Its
  quality gate also wants 80% coverage on new code, so a sweep or a rename trips that by
  construction; read the new-issue count rather than the tick.

## Warning suppressions

Every `@SuppressWarnings` carries its reason in a comment directly above it, or in the class
Javadoc for a class-level one. Add one only when the warning is wrong about this code, not to
quiet one that is inconvenient. Fifty-one at present; the one naming both `unchecked` and
`rawtypes` counts in each row. MockBukkit gets its own column because `src/mockbukkit/java`
compiles only under the `mockbukkit` profile, so a plain `mvn test` never sees those two:

| Suppresses | Main | Tests | MockBukkit | Why |
|---|---|---|---|---|
| `java:S3516` | 20 | – | – | Handlers always return `true`, because Bukkit reads it as "handled"; three are field setters behind an interface whose other implementations return `false`. |
| `java:S4144` | 7 | – | – | Events need an instance `getHandlers` and a static `getHandlerList` with the same body. |
| `java:S2589` | 5 | – | – | Null checks Sonar thinks cannot fire, kept for mocks that stub nothing, or for a seam documented to return null. |
| `java:S3077` | 4 | – | – | `volatile` on a function reference or an immutable snapshot swapped in whole. |
| `java:S1168` | 3 | – | – | Null means something an empty result cannot; each says what its caller does with it. |
| `unchecked` | 3 | 3 | 1 | Casts with nothing to check against: SnakeYAML's `Object`, reflection, generic captors. |
| `deprecation` | 1 | – | 1 | `getOfflinePlayer(String)` and `getDescription()`, whose replacements are Paper's alone. |
| `java:S2583` | 1 | – | – | A null check that never fires on a server; a mock player with no UUID would throw without it. |
| `java:S6905` | 1 | – | – | `SELECT *` from a legacy database whose columns vary by version. |
| `rawtypes` | – | 1 | – | Alongside `unchecked`, for an `ArgumentCaptor` of a generic collection. |
| `java:S1612` | – | 1 | – | A method reference would cast its receiver early, outside `assertThrows`. |

When the table and the code disagree, recount; the second line totals each column:

```bash
grep -rn '@SuppressWarnings' src --include=*.java | grep -v '{@code'
grep -rn '@SuppressWarnings' src --include=*.java | grep -v '{@code' | cut -d/ -f2 | sort | uniq -c
```

## Minecraft versions

The supported range is 1.20 through 26.3. The floor is `Material.CALIBRATED_SCULK_SENSOR`, which
gate detection switches on and 1.19.4 lacks; the top is the newest stable release.

The plugin compiles against the **oldest** API it supports, not the newest. A plugin built
against an old API runs on newer servers; one built against a new API can call something an
older server has never heard of, and nothing catches that until a player reports a crash.
Compiling against the floor makes the compiler enforce it. That says nothing about a newer server
*removing* something, so CI also builds and tests against every newer supported version.

| Where | Example | What it means |
|---|---|---|
| `pom.xml` `spigot.api.version` | `1.20.4-R0.1-SNAPSHOT` | The API this jar is compiled against. `R0.1` is Bukkit's API revision. |
| `plugin.yml` `api-version` | `1.20` | The oldest server that will load the plugin. Major-minor only. |
| The `server-api` matrix in `ci.yml` | `1.20` – `26.3` | What is actually built and tested against. |

The compile target is 1.20.4 rather than 1.20 because `EntityDismountEvent` moved from
`org.spigotmc.event.entity` to `org.bukkit.event.entity` there, and 1.20.4 is the only version
carrying both. There is a small listener for each and only the loadable one is registered; a
server with neither loses the ability to stop a rider dismounting mid-transit, and says so in the
log. CI jobs on either side of the move use the `legacy-api` and `modern-api` profiles to leave
out the listener that cannot compile; the shipped jar uses neither.

To add a new Minecraft version:

1. Build against it: `mvn verify -Dspigot.api.version=<version>-R0.1-SNAPSHOT`.
2. If it passes, add it to the `server-api` matrix in `.github/workflows/ci.yml` and to the table
   in [guide/SERVER.md](guide/SERVER.md#compatibility).
3. Leave `spigot.api.version` and `api-version` alone unless you are dropping old versions.

If it fails, the compiler names what was removed. Both boundaries found so far were a single
symbol, and one was fixable in a line.

## Coding conventions

`.github/copilot-instructions.md` has the full set. The ones most often got wrong:

- Java 17, Allman-style braces, 4-space indentation, `final` on every local and parameter that
  is not reassigned.
- Anonymous `Runnable` classes for scheduled tasks, not lambdas: they reschedule themselves and
  mutate retry state through the array-holder idiom. Lambdas and method references are used
  freely everywhere else.
- Catch `RuntimeException`, not `Throwable`, except where cross-version compatibility needs a
  `NoSuchMethodError` caught on purpose, and say so in a comment.
- Log through `WormholeXTreme.getThisPlugin().prettyLog(Level, String)`. The three-argument
  overload adds the plugin version to the tag and is for startup and shutdown lines only; never
  pass it `false`.
- `MaterialUtils.isWallSign`, `isButton` and `isAirMaterial` cover every variant, so nothing
  tests block types one at a time or compares against `Material.AIR`. `isAirMaterial` compares
  the three constants rather than calling `Material.isAir()`, which goes through
  `org.bukkit.Registry` from 1.20.6 on and needs a running server.
- A gate's sign material comes from its shape's `SIGN_MATERIAL=` key; nothing hardcodes
  `OAK_WALL_SIGN`.
- Storage is YAML only: one file per gate (`StargateYamlManager`), one per world for ring pairs
  (`RingYamlManager`), one each for beams and mirrors. A legacy SQLite database is read by
  `LegacyDatabaseImporter` from `/wormhole gate import`.

## Writing conventions

- **British spelling, in prose and in player-facing text.** `dialled`, `dialling`, `colour`,
  `traveller`, `centre`, `behaviour`, `licence`, `recognise`, `grey`. This is settled — the
  repository is already consistent and converting it was considered and rejected, because a
  large share of the words that look convertible are not prose at all. Do not "correct" them.
- **Three kinds of exception, which are not dialect choices and must stay exactly as they are:**
  - **Bukkit's API.** `setCancelled`, `isCancelled` and `Cancellable` are interface members this
    plugin overrides.
  - **Anything persisted or typed.** `Colours` is a key in saved mirror YAML, `colour` is a
    subcommand argument, and the gate gallery writes `*-dialled.svg`. Renaming any of these
    breaks live servers, saved data, or command blocks.
  - **Attributes defined by a spec.** SVG and CSS use `fill`, `stop-color`, `color`.
- **`CHANGELOG-ORIGINAL-2011.md`, `LICENSE` and `gpl.txt` are verbatim.** Historical record and
  licence text written by other people. Never reflow, respell or tidy them.
- **The galleries are generated.** The gate, ring, beam and mirror drawings in `docs/images/` and
  the tables between the `<!-- ...:start -->` markers in the design documents come from
  `scripts/render_*_sheets.py`, and a test fails until they are re-run after the source changes.
- **Plugin-site listing copy lives in [`docs/listings/`](listings/).** Its facts are in
  `listings/shared.md` and each site's fields and markup in its own file. It prints no count a
  release can change — not settings, test classes, CI legs, shapes or mirror looks — because such
  a figure goes stale where nobody is looking. Only the version, the supported range and the Java
  versions are numbers there, and a live badge carries the rest.

## Submitting changes

Create a feature branch from `main` and open a pull request; nothing is committed to `main`
directly. Run the tests locally first, and add tests where the change touches behaviour. A
user-facing change — a command, a setting, a fixed bug — gets a line in `CHANGELOG.md` under the
unreleased version, and often a change in the guide beside it.

## Versioning

Three numbers, and what decides each one is what a server owner has to do to upgrade, not how
much work went into it.

- **Patch** (`1.7.1`) — fixes only. Swap the jar and carry on.
- **Minor** (`1.8.0`) — features, settings and behaviour changes, including anything that needs
  an **Upgrading:** note at the top of its changelog entry. A shape may gain a marker, a config
  key may gain a default, a gate may need regenerating; none of that changes what a file that
  already exists *means*.
- **Major** (`2.0.0`) — the shape or data format changes meaning, or the events and methods in
  [API.md](API.md) break. Reserved for the case where a file that parsed yesterday describes
  something different today.

**This is a change in practice, not a description of it.** 1.7.1 reshaped `Massive.shape` and
told operators to regenerate every `Massive` gate, which is a minor by the rule above. It went
out as a patch because there was no rule to consult. From 1.8.0 on there is.

Two things that follow from it, worth knowing before filing an issue:

- **The plugin's version says nothing about Minecraft's.** The supported range lives in the
  README badge and in [Minecraft versions](#minecraft-versions), and moves on its own schedule.
  `1.10.0` after `1.9.0` is ordinary, and is not a claim about Minecraft 1.10.
- **The pom carries the version being worked towards, with `-SNAPSHOT`.** Work lands under a
  top changelog heading marked `(unreleased)`, and the pom says, say, `1.9.0-SNAPSHOT` while that
  is what is being built; the release PR drops the `-SNAPSHOT` and dates the heading. The release tag is what sets the real number: `release.yml` runs
  `versions:set` from the tag, so nothing downstream reads the pom's development version.

  It used to carry the *last released* version instead, which meant a jar built mid-cycle
  reported a number that was already out and said nothing about what was in it. `/version`
  still prints the build time, which is what tells two builds of one version apart; the
  `-SNAPSHOT` is what stops a development jar claiming to be a release.
