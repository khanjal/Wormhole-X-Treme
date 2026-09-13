# Development Guide

This document describes how to set up a development environment for Wormhole X-Treme, run tests, build artifacts, and follow repository conventions.

## Prerequisites
- Java 17 (JDK)
- Maven 3.8+
- IDE (IntelliJ IDEA, VS Code, or Eclipse)

## Build
- To run tests and build the plugin jar:

```bash
mvn -DskipTests=false test
mvn -DskipTests=true package
```

The resulting shaded JAR will be in `target/`, named from the version in `pom.xml` (e.g. `target/WormholeXTreme-<version>.jar`).

## Tests
- Unit tests use JUnit 5 + Mockito. Run all tests with `mvn test`.

## Static analysis
- SpotBugs runs in CI and can be run locally with `mvn -DskipTests=true spotbugs:check`. It fails
  the build on what it finds.
- PMD runs only when asked, and never fails a build:

```bash
mvn pmd:pmd -Dformat=csv
```

  Findings land in `target/pmd.csv`. It exists to answer, in about ten seconds, part of what
  SonarCloud would say minutes later on the PR. The rules it runs are the subset measured to
  agree with SonarCloud on this codebase, and `pmd-ruleset.xml` records that measurement, what
  it covers, and what it deliberately leaves out. A clean run is meaningful; an individual
  finding is a lead worth confirming, not a verdict.
- SonarCloud remains the authority. Its quality gate wants 80% coverage on new code, so a sweep
  or a rename trips it by construction -- read the new-issue count rather than the tick.

## Minecraft versions

The supported range is 1.20 through 1.21.10, and both ends were found by building against every
published `spigot-api`. The floor is `Material.CALIBRATED_SCULK_SENSOR`, which gate detection
switches on and 1.19.4 lacks.

The plugin compiles against the **oldest** API it supports, not the newest. A plugin built
against an old API runs on newer servers; one built against a new API can call something an
older server has never heard of, and nothing catches that until a player reports a crash.
Compiling against the floor makes the compiler enforce it. That says nothing about a newer server
*removing* something, so CI also builds and tests against every newer supported version.

| Where | Example | What it means |
|---|---|---|
| `pom.xml` `spigot.api.version` | `1.20.4-R0.1-SNAPSHOT` | The API this jar is compiled against. `R0.1` is Bukkit's API revision. |
| `plugin.yml` `api-version` | `1.20` | The oldest server that will load the plugin. Major-minor only. |
| The `server-api` matrix in `ci.yml` | `1.20` – `1.21.10` | What is actually built and tested against. |

The compile target is 1.20.4 rather than 1.20 because `EntityDismountEvent` moved from
`org.spigotmc.event.entity` to `org.bukkit.event.entity` there, and 1.20.4 is the only version
carrying both. There is a small listener for each and only the loadable one is registered. CI jobs
on either side of the move use the `legacy-api` and `modern-api` profiles to leave out the listener
that cannot compile; the shipped jar uses neither.

To add a new Minecraft version:

1. Build against it: `mvn verify -Dspigot.api.version=<version>-R0.1-SNAPSHOT`.
2. If it passes, add it to the `server-api` matrix in `.github/workflows/ci.yml` and to the table
   in [guide/SERVER.md](guide/SERVER.md#compatibility).
3. Leave `spigot.api.version` and `api-version` alone unless you are dropping old versions.

If it fails, the compiler names what was removed. Both boundaries found so far were a single
symbol, and one was fixable in a line.

## Coding conventions
- Java 17, Allman-style braces, 4-space indentation.
- Use anonymous `Runnable` classes for scheduled tasks, not lambdas -- they reschedule themselves and mutate retry state through the array-holder idiom. Lambdas and method references are used freely elsewhere (tab completion, `computeIfAbsent` suppliers, `FilenameFilter`). See `.github/copilot-instructions.md` for the full convention.
- Use `WormholeXTreme.getThisPlugin().prettyLog(Level, String)` for logging. The three-argument overload adds the plugin version to the tag and is for startup and shutdown lines only; never pass it `false`.

## Submitting changes
- Create feature branches from `main`, and open a PR. Nothing is committed to `main` directly, including small fixes.
- Run tests locally and ensure build passes before creating PR.

## Common tasks
- Run tests:

```bash
mvn -DskipTests=false test
```

- Build package (skip tests):

```bash
mvn -DskipTests=true package
```

- Format code: follow repo style (IDE formatting constrained by project conventions).

## Notes
- See [guide/SERVER.md](guide/SERVER.md) and the pages beside it for usage and commands.
- For long-running changes (SpotBugs, CI changes), coordinate with maintainers.
