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
by command, a following pet through a gate and by beam into another world, a caller up against
a shut iris, and a sign gate dialled by redstone. It checks where they arrive and that the trip
leaves nothing new running. Annotate
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

## A player on a real server

MockBukkit's player is simulated, and the boot tests in `ci.yml` run a real server with no player
on it: `travel-boot.sh` sends an item and a pig through a gate. `scripts/player-boot.sh` puts a
player on a real server. A [Mineflayer](https://github.com/PrismarineJS/mineflayer) bot joins,
and the bot, as a player, presses a gate's DHD, runs `/dial`, walks through, and then beams and
rides a ring pair; it rides a boat, a minecart and a horse through gates, and takes wolves along.
Each trip fails unless the bot comes out where it should. It checks what the
bot sees, not the plugin's state: the opening filling and emptying, and where the server puts it.

| Trip | What the bot does, and what must happen |
|---|---|
| `gate` | Two Standard gates: DHD, `/dial`, walk in; out at the partner, and the opening fills only after the kawoosh |
| `beam` | Saves a destination, beams to it from twenty blocks off; lands there facing the way it was saved |
| `ring` | Makes two circles of slabs a ring pair, walks into one; comes out in the other |
| `atlantis` | Builds two gates as a player does, `gate build Standard Atlantis` on a DHD button, `preview place`, `gate complete`; the frame is lapis, the chevrons light as sea lanterns, and it travels |
| `horizontal` | Two `Horizontal` gates flat in the floor; steps off into the opening after the kawoosh and comes out at the partner's arrival point |
| `lava` | A gate whose portal is set to lava; lava fills the opening after the kawoosh, it still travels, and the far gate shows nether portal |
| `iris` | Shuts a gate's iris at its lever and sees stone drawn across it; a dial without the code is refused; with the code it opens, and with the iris shut again walking in bounces it back ("Remote Iris is locked!"); opened, it travels |
| `mirror` | Makes two banners mirrors and waits for their rooms to be captured (15 seconds or so); the first's view is drawn with no gold behind its wall; right-clicked, it shows the second's room (a gold block); punched, it puts the bot at the second |
| `boat` | Gets into a boat on a lane of blue ice and drives it into an open gate; out at the partner still in the boat, and still in it two seconds later |
| `minecart` | Sits in a minecart on a rail line whose end stops short of the opening; powering the first rail launches it, it rolls off the end into the gate, and it must be on the far gate's line, still in the cart, a moment later |
| `mount` | Saddles and rides a tame horse the whole way: through a gate, then `/wormhole beam` into a ring, then that ring to its partner; still on the same horse after each. Each leg is checked on its own, and the trip names each that failed |
| `pet` | Wolves tamed to the bot beside a gate whose partner is in the nether. A sitting wolf stays put throughout. With `pets-follow-owner` off, a following wolf must not come when the bot beams there (vanilla brings one within a world, so the trip crosses worlds); with it on, one must come by beam, there and back, and one through the gate. Every leg runs, and the trip names each that failed |

`TRIPS=iris,mirror` runs only the trips named.

A ridden boat or horse is moved by its rider's client, which reports each step to the server;
Mineflayer does not simulate one, so `drive()` in `journeys.js` sends those steps itself, in a
straight line on the level. A minecart is the server's, so that trip powers rails instead.

```bash
bash scripts/fetch-server.sh paper 1.21.11 server.jar    # or download a Paper jar by hand
mvn -DskipTests package
bash scripts/player-boot.sh server.jar target/WormholeXTreme.jar 1.21.11
```

It needs Node 22 or newer, which Mineflayer 4.39 requires; the first run installs the bot into `scripts/player-test/node_modules`.
The version must be one Mineflayer speaks: 1.20.1 to 1.21.11 and 26.1 as of 4.39, not 26.2 or
26.3. The server listens on port 25599 in offline mode (`BOOT_PORT` changes it, for a second
server beside one being watched), on the boot tests' flat world with a grass floor rather than
bedrock (`BOOT_FLOOR`); mob spawning is turned off and anything that spawned is cleared.

**Watching it.** With `OBSERVE=1`, the bot waits for someone to join before it starts, and
for their answer after each trip; `OBSERVE_WAIT` sets both waits, in seconds (default 600):

```bash
OBSERVE=1 bash scripts/player-boot.sh server.jar target/WormholeXTreme.jar 1.21.11
```

On Windows, `scripts\watch-local.ps1` does all of it from PowerShell. It stops anything left
from an earlier local run, builds the plugin, downloads the Paper jar the first time, and starts
the server in `.local-server\run-<version>` with a fresh world. `-Version 1.20.4`,
`-Trips gate,boat`, `-Headless` and `-NoBuild` change what it does:

```powershell
.\scripts\watch-local.ps1
```

Where PowerShell will not run scripts, `powershell -ExecutionPolicy Bypass -File .\scripts\watch-local.ps1`
does the same.

Join `localhost:25599` from a Minecraft client of that version under any name. You are made a
spectator and moved to a spot facing each trip before it starts, and the bot says in chat what to
watch for. After each trip it asks whether you saw it; answer `y` or `n` in chat. The terminal ends
with a summary of each trip's automatic result and your answer, and a `n` fails the run just as a
failed check does. So does no answer in time, or the watcher leaving: a watched run that nobody
confirmed does not pass. `BOOT_DIR=somewhere` keeps the server folder and its `console.log`;
use a new folder each run, since a world already holding the gates makes every setup fail.

The **Player journeys** workflow (`player.yml`) runs the same thing without an observer on
Paper 1.20.4 and 1.21.11. It runs when started by hand from the Actions tab, and when the harness
itself changes. It does not run on pull requests yet.

What it does not cover yet: sign dialling, other worlds beyond the `pet` trip's gate and beam into
the nether (so a mirror's trip is within one world, with `mirror-per-world-limit` set to 0), a
horizontal gate's iris, the other shipped shapes and the Universe and MilkyWay groups, and what a
preview looks like, which Mineflayer can see only as entities; the `atlantis` trip uses a preview
but checks only the gate it places. Of vehicles and pets: a boat on water rather than ice, pigs,
camels, llamas and striders, a mount shared with a second player (which a beam deliberately
leaves behind, and which needs a second bot), pets by ring or mirror, and pets within one world,
which vanilla brings along by itself. An item and a mob dropped into a gate are `travel-boot.sh`'s,
with no player on the server. Each is another trip in `scripts/player-test/journeys.js`: set up
from the console, act as the player, then check where the bot is and what it sees.

### Facility (in progress)

`scripts/facility/` is the Wormhole Research Facility: a flat test campus that a tester walks
and drives from clickable chat, and that a bot drives from the same console. It never runs in
GitHub, not as a pull-request check, a release job or a manually dispatched workflow: it is a
local pre-release check. Before making a release, run `--selftest` locally on all three versions.

```bash
npm install --prefix scripts/facility
node scripts/facility/run-facility.js 26.1.2               # build the campus, hold for a tester
node scripts/facility/run-facility.js 1.21.11 --selftest   # prove it, exit 1 on any FAIL
node scripts/facility/run-facility.js --selftest --quick --versions 1.20.4,26.1.2   # in parallel
```

The launcher builds the plugin with Maven (offline, on a JDK 17 it finds; `--plugin <jar>` or
`--no-build` skip that), downloads the newest stable Paper build to
`.local-server/paper-<version>-facility.jar` (checked against PaperMC's published size and
SHA-256, and fetched again when a newer stable build is out; offline, or when PaperMC lists no
stable build, the cached jar is checked against the build recorded with it; `--paper-build <n>`
picks a build, checked the same way), and starts the server in `.local-server/facility-<version>/`
on port 25590 (`--port`) with a fresh world (`--keep-world` keeps it, and puts back any plugin
setting a killed run left changed); another `--port` gets its own folder,
`facility-<version>-<port>`, so two runs never share a world. It finds the Java a version needs
on its own (17 for 1.20.4, 21 for 1.20.5 to 1.21.x, 25 for 26.x); `--java` names one. `WX_ECHO=1`
prints the server's log as it runs (`0`, `false`, `no` and `off` do not). However the launcher
ends, the server goes with it: Ctrl+C stops it, a second Ctrl+C kills it, and a watchdog kills
the JVM if the launcher is itself killed. The spike does the same.

Held, it says when to join. You arrive in the atrium in adventure mode and are sent a Console
link; `!` does the same. Each wing is reached by the feature it tests, built by the plugin as a
session fixture (`lib/transit.js`, positions in `campus.ROUTES`):

- the gate `Ops`, north in the atrium, and `Hall` in the Gate hall's lobby: press a button on the
  dial console beside either (the console form of `gate dial`), or press the DHD and `/dial Hall`;
  the Ops console also dials Range and Annex;
- the ring pair `Ops` (east in the atrium) and `Lab` (just inside the Ring lab): step in and stand
  still; a rim in the floor marks each pad, since a paired ring's slabs are taken up;
- the beam pads `Atrium` (west in the atrium) and `BeamLab`: press the button by the pad and click
  the prompt, or say `/wormhole beam to BeamLab`. A command block cannot beam whoever pressed it:
  `beam to` refuses a sender that is not a player (a command block running `execute as @p`
  included) and `beam admin send` takes a player's exact name, not `@p`. So the button sends the
  nearest player a chat prompt whose click runs `beam to` as them;
- the mirror `Ops` on the atrium's south wall and `Optics` in the gallery lobby: walk up, right-click
  (each starts on the other), punch. `Range` and `Annex` hang on piers at the far sites, so every
  world has a mirror; the Range and the Annex also each have a Dial Ops button by their gate.

The tp plates are the fallback that works with the plugin down: eight in a row in the service
corridor under the mezzanine, and a plate home two blocks into each wing's corridor. Say "stop"
in chat, or press Ctrl+C, to end it.

Every coordinate is in `lib/campus.js`: wings, corridors, lanes, chambers, plates, forceload
rectangles. Move a wing or resize a room there and run it again; `wings/` compiles the campus
into a datapack function per wing and a reset per chamber, and refuses to build a layout whose
parts overlap or reach outside the forceloaded chunks. Each chamber's cell is built empty, with
its gallery, seat, door, pylon and board derived from its box. A chamber gets its tests by
adding a file to `chambers/` against the contract written at the top of `chambers/index.js`.
So far `c0` (the calibration cell in Ops), `g1` (the Test Stand), `g2` (the Shape Gallery), the
five ring chambers and the range tunnel, and the two beam chambers have them; the far gates
(Relay across the hall, Range in the nether, Annex in the End), the gallery's six gates and the
transit routes are built once a session as fixtures, and the Menagerie is stocked. A fixture is
permanent: a chamber's reset and cleanup leave it alone (`Facility.keepRings` spares the transit
ring pair from the ring chambers' "take down every pair" cleanup).

G1 builds its gate by the console form, flush with the floor, dials a far gate, and sends one
traveller through: Probe on foot, riding (horse, camel, pig, donkey, strider), in a cart or a
boat, with a pet following, a projectile from a bow, crossbow, hand, dispenser or you, a dropped
or dispensed or spilled item, an orb, an armour stand, a zombie, a swept llama, or an item frame
that must stay put. Its options (shape, group, chevrons, dial, spin, destination, irises, portal
and the rest) each carry their reason as hover text; the combinations that cannot be run are
refused with the reason. A check is a fact recorded during the run: what Probe's client was shown
in the opening, where Probe is, what a tick function in the datapack caught at the far exit on
the first tick it was there (position, motion, and whether it is the same entity), and what the
plugin said. The cells named `491-*` run a cart at a shut iris and track its front every tick.
Probe plays every player traveller in creative mode, except the trident, thrown in survival: a creative player keeps a thrown trident and never picks one up, so
"it came back" would hold whatever happened. A check that something was stopped is read after a
check that it was sent: the tick function counts each launch and keeps the first one's data (a
tipped arrow is checked to be one as it leaves), a shut iris is checked shut at its lever, a
vehicle at a shut iris must have run at it, and the tossed and dispensed items must all have left
Probe's hand or the dispenser and be accounted for, arrived or lying on this side, before "every
one came out" is read.

Each wing is also decorated (stage 3.6), from `wings/decor/<wing>.js`: a floor stripe in the
department's colour from the entrance to every door, and a wayfinding tab at each fork; in Ops a
compass rose, the Gate Room's two lit pylons and the Briefing Room's window; over G1's west
gallery an observation deck whose control room shows the G1 board; under R3's glass gallery the
Shaft Window, a light well open to the shaft; distance markers in the range tunnel; the
transporter bay and dispatch office; the Yard's lanterns, hay and depot roof; and runways,
dial-home consoles and blast walls or pillars at the far sites. The Transit tab in the console
(`!transit`) dials the Ops gate, beams you to either pad, and prints the routes' last results.

Decoration can never get in a test's way: `validateLayout` checks every block a `decorate`
function writes against every cell's clear volume, floor and footprint, the far and transit
gates (with their buttons, pits and aprons), every ring and beam pad outside a cell, the lanes,
the plates, the walk-in lines, the stage 4 mirror spots (M1's spare in the End too) and every
structural anchor, and every plaque or label it summons against the cells, gates and pads, where an
entity is in the way as much as a block; and refuses to write the pack if one overlaps, naming
both. It also refuses any block 1.20.4 does not know (read from `minecraft-data`), so a newer name
fails at compile time rather than as a function that silently fails to load on the older server.
The decoration is always built: the far sites' mirror piers and Dial Ops consoles are part of it
and carry tests.

The ring chambers (stage 3) pair two circles of slabs by the console form `ring build` (public)
or as Probe with `ring create` in each (private, Probe's), and send one traveller: Probe on foot,
Probe and Probe2 swapping ends in one instant, Probe on a horse, or a zombie, an item or a cart
fired from the console. Probe2 is a second bot, never opped and in adventure mode, for what needs
a player who is not an op. `r1` is the pair stand (patterns, six slabs, distances, private pairs
and who may use them, styles, the shortest countdown); `r2` hangs one ring from the ceiling (6
and 10 up pass, 11 and 2 are refused in the plugin's words) and checks the rings stood on the
floor; `r3` pairs the top of the 60-deep shaft with 20, 40 or 60 down, and at
`ring-max-link-height 30` the deeper ones are refused; `r4` lays each faulty circle and reads the
refusal back; `r5` edits a pair (a name announced on arrival, a pad light drawn, private,
allow, deny, owner); the range tunnel pairs 64, 128 and 250 apart and refuses 257. Timing: the
swap comes about 154 ticks after a ring is armed and the pair recharges for 30 s after the cycle.

The beam chambers send a traveller by `beam to`, `go`, `admin send` or `admin goto`. `b1` has
named pads, each saved facing its letter, a trap with no floor (the landing is one down) and a
blocked pad (one up), and pads in the Range and the Annex; the traveller is Probe2 (watched by
Probe, whose client must lose sight of it while it travels), Probe, Probe on a horse, or Probe2
with its wolf; the timing presets are measured against the plugin's timeline to within 0.25 s.
`b2` is the dispatch desk: `admin send` to a public destination, a player, coordinates in the
nether, and nobody; `admin goto` and `admin set` as an op and as Probe2 (refused); a player's own
place against another's (refused); `go` to a gate as an op and as Probe2 (refused); and the
cooldown, which Probe2 waits out and an op skips.

The self-test's `transit` section, after the fixtures and before the matrix, walks every route as
Probe2: the gate from the Ops console's `Hall` button (and both gates shut again within
`timeout-shutdown`), back by the Hall DHD and `/dial Ops`, the ring pair both ways (announced by
name, recharging on the way straight back), the beam out by the button and back typed, the Ops
gate to the Range and to the Annex and back by their Dial Ops buttons, and the mirror to Optics
and back. Each route's last result is on the Ops wall under TRANSIT. `--quick` walks one route per
feature.

The mirror chambers (stage 4) work the network of those four and one of their own. `m1` hangs
`Round` in its cell and sends Probe2 (or Probe) to each of the others: the approach line above the
hotbar names it, right-clicks walk the round by name (its `-start` first), the banner gives way and
the far room is drawn behind the wall, a punch lands the traveller in the far room facing out; with
the other probe standing by, a second click inside three seconds is held; with
`mirror-approach-message` off nothing is named; and at `mirror-per-world-limit 1` a second mirror in
the End is refused. `m2` builds the mirror hall down its west side (a solid wall, a gap one out, a
gap two out, a standing banner, two banners side by side) and reads `create`'s answer at each, and
checks a mirror survives an op's punch, `remove`, and the limit. `m3` shows a view is a snapshot: a
block put in the room after the capture is not drawn until `-capture`, and `-stamp` changes the
banner. The facility runs with `mirror-per-world-limit 6` (two in the overworld, room for a
chamber's own), put back at close. A mirror's first capture takes about 13 s at the default view
depth; the chambers wait on `mirror debug` until it is in memory.

There are two self-test profiles. The full one runs the whole matrix (315 checks counting
resets, 368 with stage 4's mirrors and cross-world rows) and takes about an hour on one version; it is the one to iterate on, on 1.21.11.
`--quick` runs everything else the same but only a short matrix: one walk, one cart, one horse,
one pet, a bow, a throw, a dispenser, a dropped item, one refusal, a gallery gate, the first
#491 cell, a ring walk and a swap, one refusal from each ring chamber, a ring edit, two beam pads
and two dispatches, a mirror round and its hold, a wall refusal, a capture and a stamp (the
banner's patterns are read under another key on 1.20.4), a horse to the Range, a pad in the End,
and one transit route per feature, in about 13 minutes. It is the cross-version check (text formats, entity ids, boats, the
1.20.4 teleport quirk): `--versions` runs each version as its own process, all at once, on ports
`--port`, `--port`+2 and so on. Each is tied to it as a server is to a launcher: Ctrl+C stops
them all, and if the launcher is killed outright its children and their servers go with it.

`--shards N` runs one version's matrix on N servers of that version at once, each on its own port
and folder with its own campus (`--port`, +2, +4, ...), and merges their reports into one summary
and one exit code; with `--versions` it is versions x shards servers, each tied to the launcher as
with `--versions` alone. Every cell runs exactly as it does alone. The split is by measured time,
not by count: every self-test writes each cell's seconds to
`.local-server/cell-times-<version>.json`, and the next split puts the longest cells first onto
the least-loaded shard (the checked-in `cell-times.json` is the fallback). The world, fixtures,
resets, empty, settings and faults sections run on every shard, since they guard that
shard's own world, so a sharded summary has more checks than a single run (722 at N = 4, with
the checks added in stage 4.5's review, which took 18.1 minutes); transit, plates, boards, players and console run once, on the first shard. On this machine
(32 threads, 64 GB) a full 1.21.11 run took 61 minutes on one server, 31.5 at N = 2 (peak 3.6 GB,
CPU 54%), 21.3 at N = 3 (5.3 GB, 65%) and 16.6 to 19.3 at N = 4 (6.0 to 7.7 GB, 61 to 90%); N = 4
is the one to use here. `--quick` on two versions at once takes about 13 minutes.

A cell whose purpose is not the shutdown timeout closes the gates it opened with the plugin's own
close (`gate force`) as soon as its checks are read, and then checks the end state: the plugin said
it closed them, and every end Probe can see, once seen drawn open (up to 8 s: a probe that has just
arrived is not shown the opening at once), is drawn shut within 5 seconds (a chamber's `shut`, run
by `runChamber` after `checks`). An end never seen open fails that check rather than passing as
"not drawn". The transit routes do the same, except Ops to Hall, which still waits for
`timeout-shutdown` because that is what it tests.

A cell that holds a session fixture (G2's gallery) has it put back after the reset `runChamber`
does before each run, and G2 checks both its gates stand whole before the trip. (Before, it dialled
a gallery the reset had floored over, and `g2 Horizontal` passed only because of it: walked to, a
flat gate stops Probe at the rim, so Probe is now dropped into it, as in G1.) The plugin draws a
gate's opening only to players within 64 blocks, so only ends within that are judged shut.

Players in the facility do not go hungry or get hurt: every player is given infinite Saturation
and Resistance 255 on joining and every five seconds after, and the player damage gamerules (fall,
fire, drowning, freezing) are off in all three worlds. Mobs are not shielded. The `players`
section checks a non-op Tester stays at full health and food after a fall and an arrow, that a
pig dropped ten blocks still comes down hurt, and reads the four gamerules back in each world (the
shield alone would keep Tester whole). The animals a plains chunk is generated with, which no
gamerule stops, are killed before the Menagerie is stocked.

A plugin fault that is a known plugin bug is reported as known, with its note, the way a matrix
cell's known failure is (`KNOWN_FAULTS` in `lib/server.js`): so far #540, the mirror capture that is
not written when two captures finish at once (`MirrorCapture.save` races on creating its folder;
fixed on main). It is known only with that cause: the stack trace printed after the line must be
the "could not create ...captures" from `MirrorCapture.save`, and the folder must be there now; the
same words with another cause (a full disk, a folder that cannot be made) are a fault. `--fixed 540`
counts it as a fault as well.

`--cells <names>` runs only the matching matrix cells: names separated by `|`, each matched
anywhere in a cell's name, or at its start with `^` and its end with `$` (`--cells '^491'`,
`--cells 'tipped arrow|trident'`). It is a plain match, not a regular expression. A cell the plugin is known to fail is
expected to fail by the name of its failing check and is listed at the end of the run as a known
plugin failure, never hidden; `--fixed 491` (with `--plugin` pointing at a jar carrying that fix)
expects those cells to pass instead.

#### Companion plugins (`--with`)

`--with <list>` installs companion plugins beside Wormhole: `viaversion`, `viabackwards`,
`dynmap`, `worldedit`, `worldguard`, `luckperms`, `vault`, or the sets `via`, `regions`
(WorldEdit and WorldGuard) and `permissions` (LuckPerms and Vault); what one needs comes with it.
Each is pinned by version and SHA-256 in `scripts/facility/companions.json`, per Minecraft
version. The launcher reads the plugin cache first (`--plugin-cache <dir>`, else
`WX_PLUGIN_CACHE`, else the nearest `.wx-plugins` folder beside the repository or a folder above
it: its `<version>` folder, then `any`), then what an earlier run downloaded
(`.local-server/companions/`), and only then downloads from the pinned official source: Modrinth,
GitHub releases, or for Dynmap 3.8 the Dynmap project's own build server (Modrinth has 3.8 only for
Forge and Fabric; SpigotMC is never used, since its downloads are not scriptable). A cached jar
under the pinned name that is not the pinned build is refused, not replaced. Each run prints every
companion's file, SHA-256, Java and where it came from, and records what it installed in
`plugins/.wx-companions.json`; a run without a companion takes out the jar an earlier run put
there (and never one it did not), and a fresh run also clears the data folders of the ones it
manages. A jar of the same name already in `plugins/` that no run installed and that is not the
pinned build is somebody's own: the run is refused rather than overwrite it. Only bare names in
the record are acted on, so an edited record cannot reach outside `plugins/`. A companion that cannot run on a version is refused by name before the server starts:
no Dynmap build supports 26.x (its newest version helper is 1.21.11), so Dynmap runs on 1.20.4
(3.7-beta-8) and 1.21.11 (3.8).

The JDK is the highest any jar needs, read from the newest class file in each (not the main
class: WorldEdit 7.4.5's main class is Java 21 and most of the rest Java 25, and WorldGuard 7.0.17,
Java 21 itself, needs it), Paper's own floor included. On Java 21, Paper refuses WorldEdit 7.4.5
and Wormhole only says WorldGuard is not installed; the facility counts a companion that failed to
load as a fault. Wormhole's switch for each integration (`worldguard-enabled`, `dynmap-enabled`)
is written into its `config.yml` before the start, since both are read only at enable, keeping the
file's line endings. The value it replaced is kept in the record, and a later run without that
companion puts it back, so a `--keep-world` run without `--with` is not left with an integration
switched on.

Dynmap's web map gets its own port, `8123 + (port - 25590)`, on 127.0.0.1 only, written into its
`configuration.txt` (from the jar's own template on a fresh run) and printed; the setup fails if
Dynmap does not say its web server started there and answer, and a failed bind is a fault. A hand
lab started by other means keeps its own configuration. `--op <names>` ops testers once the
server is up.

`scripts/facility/lab.ps1` opens a lab in its own window from a fresh clone (it installs the Node
modules the first time): 26.1.2 by default, its world kept unless `-Fresh`, with `-Version`,
`-Port`, `-Plugin <jar>`, `-Op`, `-With` and `-PluginCache`. `lab.sh` does the same in the current
terminal (`-v -P -p -o -w -c -f`).

Three desks on the Systems mezzanine run the companion checks, each refusing a run without its
companions. Their cells are in `companion-matrix.js`, marked `with` (they run only when every
companion named is installed) or `without` (a paired run: only with `--with`, and none of those
installed), so a self-test without `--with` runs none of them. Two runs cover them all on 1.21.11:

```bash
node scripts/facility/run-facility.js 1.21.11 --plugin <jar> --selftest --with dynmap --cells "^map |^regions |^perms "
node scripts/facility/run-facility.js 1.21.11 --plugin <jar> --selftest --with regions,permissions --cells "^map |^regions |^perms "
```

- The **Map Desk** (#236) reads Wormhole's Dynmap markers back with Dynmap's own console commands
  (`dmarker listsets`, `list`, `listareas`, `listlines`, `getdesc`; `lib/dynmap.js`), waiting up to
  8 s after each change: a gate's point, area and popup; a dial (idle while the chevrons lock, open
  with a cyan line once the wormhole forms, idle again once shut); a pair across worlds; a name and
  a network of `<b>x</b>` escaped in Dynmap and in the web map's marker file; ring ends, their line
  and an end's name; a public beam destination drawn and a player's own place never; a mirror, and
  a new name at the same banner; removal; a restart (every marker the plugin holds drawn once, a gate
  removed before the stop not among them); `map-show-rings false`; `dynmap-enabled false` (no
  layers, nothing logged); and the paired `absent` cell, `dynmap-enabled true` without Dynmap (one
  startup warning, on `KNOWN_BENIGN`, and gates work). A cell whose setting is read at enable
  restarts the server in place (`Facility.restart`: the same world, Probe back, the console
  listening again) and its cleanup restarts once more with the setting put back. The checklist's
  `/dynmap reload` is refused: Dynmap 3.7 and 3.8 have no reload subcommand. The icons at normal
  zoom and the popup's rendering are for the tester, at the web map.
- The **Region Desk** (#240) runs the checklist in G1's cell: `Guarded` on the Stand position inside
  region `gatetest`, the Relay as the gate with no region, and an empty region `buildtest` where
  Probe2 lays a Standard frame by hand (the blocks set from the console, the DHD pressed by Probe2)
  and stands a preview. Regions are written to WorldGuard's region file and loaded (`rg define`
  needs a player's WorldEdit selection); flags and members then change by `rg flag` and
  `rg addmember`. Probe2, never an op, is in the tester group `builder`. Cases 1 to 15 each a cell;
  `switched off` is P1 (`worldguard-enabled false` and a restart: WorldGuard calls `wormhole-use`
  an unknown flag and nothing is refused), and the paired `absent` cell P2 (`worldguard-enabled
  true` without WorldGuard: the plugin says so once, loads clean, and a gate works).
- The **Permissions Desk** puts Probe2 in each tester group (`lib/groups.js`: `visitor` uses gates,
  rings, beams and mirrors; `builder` also builds, by hand and by preview; `operator` also configures
  and manages, without being a server op) and tries a DHD on a gate Probe owns, a preview, a
  setting and a public beam destination: each allowed or refused ("You lack the permissions to do
  this.") exactly as the group says. With LuckPerms installed the console's Operations tab offers
  `Your tester group: [visitor] [builder] [operator] [default]` (or `!group <name>`), and the
  `console` cell checks that switch as Probe2.

What the companion stage found (the combined jar, #240 + #236 + #491 on main, 1.21.11):

- #236: a visible gate dialled from a hidden iris gate (`map-show-iris-gates false`) is drawn
  open, not idle, so the map shows it connected to something it does not show. `map iris hidden`
  expects this as a known failure.
- The #236 checklist's gate rename has nothing to run: `gate edit` has no `name` field, so a gate
  cannot be renamed (`map rename`, a known failure). Rings (`ring edit name`) and mirrors (made
  again at the same banner) do relabel.
- With a permissions plugin, a player holds only the nodes given, and a mirror is used under a
  gate's use check (`wormhole.use.sign` or `wormhole.use.dialer`, both `default: false`), where
  rings and beams have nodes that default to true: Probe2 with no group could not choose a mirror
  on the transit route. So Probe2 joins as a `visitor` whenever LuckPerms is installed.
- LuckPerms and WorldGuard answer several console commands (`lp ...`, `rg load`, `rg addmember`,
  an unknown `rg flag`) from another thread, after the command's fence: the desks wait for the
  answer line in the log instead.

Known plugin failures (stage 2), each expected by name in `matrix.js`:

- #536: a tipped arrow comes out of the far gate as a plain arrow: the plugin re-makes a projectile at
  the far end and does not copy the arrow's potion. The arrow is checked to leave the bow as a
  tipped arrow of slowness (`item: tipped_arrow` with `potion_contents` slowness on 1.21.11) and
  arrives as `item: arrow` with none.
- Not yet filed, the same family: a Loyalty trident comes out of the far gate as a plain trident
  (its `item` has no enchantments; only its `weapon` still has Loyalty III), sticks where it lands
  and never comes back. Thrown in survival away from any gate, the same trident is back in about a
  second. (While Probe threw in creative, "it came back" passed without it.)
- #537: an item tossed (Q) or dispensed into an upright opening mostly flies through its one block
  between two of the plugin's entity sweeps (every 20 ticks) and lands two to four blocks behind the
  gate. With the harness checked first (all five left Probe's hand or the dispenser, which is empty
  after five pulses, and all five are accounted for), on 1.21.11 0, 1 and 2 of five tossed came out
  of Relay and 0, 0 and 3 of five dispensed; on 1.20.4, 2 tossed and 0 dispensed. So "none of the
  dispensed arrive" is not always so: most do not. Only an item lying in the opening when a sweep
  runs is sure to be sent (as itself, name and enchantment intact: the lying-item cell passes). A
  broken hopper cart's drops scatter, so whether they are sent is chance; that cell is left out.
- #491: a cart run at a shut iris is stopped with half of it inside the drawn iris, because the
  plugin checks only the block the cart's centre is in (`491-A`, `-B`, `-C`, `-E`; fixed on
  fix/491-cart-shut-iris, where they pass with `--fixed 491`). Cell `491-D`, a boat its rider rows
  at the iris, is let about two blocks past the face before it is put back, on main and on the
  fix alike. The Horizontal control (`491-H`: a cart rolled onto a flat gate's shut iris, which is
  real blocks) passes on main; on the fix the cart is put down inside the opening, so `--fixed 491`
  expects that cell to fail and says why. `--fixed 536` and `--fixed 537` flip their cells too.

What stage 4 found (none of it a plugin fault):

- Mineflayer's `activateBlock` swings the arm after a right-click, and Paper reads a swing at a block
  the player is facing as a left click: on a mirror, a punch. The probe right-clicks without the
  swing, as a client does on a banner, and clicks the banner's cloth (against its wall) with the
  face towards it; Paper ignores a click on a wall banner's top face from a player facing it.
- Mineflayer only tells the server where the bot looks with its next move, so a bot that turns and
  stands still is still facing the old way: the probe sends the turn itself (`face`).
- The design's "setVisibleByDefault absent on plain 1.20" is really `Player.sendBlockUpdate`
  (`MirrorPackets`), from 1.20.1: below it a mirror's banner stays in front of its view. Every
  version the facility runs has it; M1 skips the banner check below 1.20.1 and says why.
- Paper keeps gamerules per world: mob spawning was off only in the overworld, and a ghast in the
  Range fireballed Probe2. The facility now sets them in all three worlds and clears what spawned.
- A console-dialled gate is drawn to a probe standing by it about ten seconds after the button
  (the dial's own lighting and whoosh), and a probe that has just arrived is not shown the opening
  at once: "not drawn" straight after arrival is not "shut".

What stage 3 found (none of it a plugin fault; the design had some of it wrong):

- `ring-min-separation` applies between different pairs, not between a pair's own two ends, so a
  pair 8 apart is not refused.
- A ring arms on a player's move inside it. A player standing still sends no moves, so after a
  trip it is carried back only once it moves, not when the cooldown ends. Naming an end means
  standing in it, which arms the pair; stepping out cancels the countdown.
- A beam's `beam-rise-ticks` only paces the rising column: the descent starts at the teleport
  (envelop + teleport-at-step), so the whole beam is envelop + teleport-at-step + descend + fade,
  52 ticks by default. `beam-descend-ticks` or a later `beam-teleport-at-step` makes it longer.
- The server kicks a player who is not an op for spam after about ten commands in a burst, so
  Probe2 sends only what a run needs. Probe2 rejoins if it is kicked, and the log says why.
- An arrival must match height as well as place: the shaft's two ends share x and z.

What stage 2 found about the harness itself:

- Before 1.21, `spawn-animals=false` in server.properties discards a summoned animal at once, and
  peaceful discards a summoned monster; the facility runs on easy with natural spawning off by
  gamerule instead.
- The plugin re-makes a projectile at the far gate as a new entity, so it carries no tag; the
  datapack's tick function catches anything new that appears at the far exit, and says whether it
  is the same entity.
- The portal and the iris are drawn to each client over air. A shut iris is solid to the client,
  so a player walking into it is stopped in front, not told "Iris is locked!".
- Mineflayer 4.39 misses a dismount (the server empties the vehicle's passenger list, which it does
  not read) and stops its physics ticks while riding; the probe handles both.
- An empty boat on land stops within two blocks of a push, so the boat cell starts close.

The self-test checks the world (every wing's sentinel and anchor blocks, every cell clear air),
the transit routes, every plate, the Ops boards as Probe's client sees them, the matrix with a
reset after each run (and a Run of a staged chamber, which is refused until its Reset), a non-op
tester's path through the console, every reset, that the plugin holds nothing the facility did
not make (its fixture gates, the transit ring pair and beam destinations, no beam places, no
mirrors), that each setting any cell changed is back, and that the plugin logged no fault.
Known-benign plugin lines are listed one by one in `lib/server.js`.

The spike, `spike.js`, is stage 0's proof of the four vanilla mechanisms the rest is built on: a
datapack function that builds a box, a `/trigger` console whose menu reaches a player who is not
an op and whose code reaches the bot, a text display that reads back as written, and a bossbar.
It takes one version and `--java`; `--hold` keeps the server up so a person can click the menu.

What it found:

- A server sends a player the scores of an objective only while the objective sits in a display
  slot, so a bare trigger objective never reaches the bot. The console puts `wx` in the sidebar
  of a team colour nobody is on: every client is sent the scores and none draws them.
- Mineflayer 4.39's `scoreUpdated` never fires from 1.20.3 on, so the bot reads the score
  packets itself.
- A real 26.1.2 client that is not an op runs `/trigger` from a menu click, and the bot reads
  each code; clicked by hand three times with `--hold`, re-armed between clicks.
- The click-event key names are the only switch a tellraw needs, but getting them wrong is
  silent from 1.21.5: the old keys are accepted and the click is dropped. A text display in the
  other era's form is silent too: blank on 1.20.4, raw JSON on the newer two. So the spike judges
  both by what the client receives, not by the server's reply.
- An out-of-range `pack_format` loads with no warning in the log, so the version table is checked
  against the `version.json` inside the server's own jar instead.
- Paper writes its console in the Windows code page unless told otherwise, which turns a `·` in
  a readback into a replacement character; the server is started with UTF-8 output.

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
quiet one that is inconvenient. Fifty-four at present; the one naming both `unchecked` and
`rawtypes` counts in each row. MockBukkit gets its own column because `src/mockbukkit/java`
compiles only under the `mockbukkit` profile, so a plain `mvn test` never sees those three:

| Suppresses | Main | Tests | MockBukkit | Why |
|---|---|---|---|---|
| `java:S3516` | 20 | – | – | Handlers always return `true`, because Bukkit reads it as "handled"; three are field setters behind an interface whose other implementations return `false`. |
| `java:S4144` | 7 | – | – | Events need an instance `getHandlers` and a static `getHandlerList` with the same body. |
| `java:S2589` | 5 | – | – | Null checks Sonar thinks cannot fire, kept for mocks that stub nothing, or for a seam documented to return null. |
| `java:S3077` | 4 | – | – | `volatile` on a function reference or an immutable snapshot swapped in whole. |
| `java:S1168` | 3 | – | – | Null means something an empty result cannot; each says what its caller does with it. |
| `unchecked` | 3 | 3 | 2 | Casts with nothing to check against: SnakeYAML's `Object`, reflection, generic captors; and a raw `BanList` inherited from MockBukkit's `ServerMock`. |
| `deprecation` | 2 | 1 | 1 | `getOfflinePlayer(String)` and `getDescription()`, whose replacements are Paper's alone, and a test stub of the first; the pre-1.20.4 `EntityDismountEvent`, the only one older servers fire. |
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
- Import a type rather than writing its package inline (`World`, not `org.bukkit.World`). A
  package-qualified name in code is only for a clash with another type of the same simple name;
  `Map.Entry` and `ConfigManager.ConfigKeys` are fine.
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
