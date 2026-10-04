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
So far `c0` (the calibration cell in Ops), the five gate chambers `g1` to `g5`, the five ring
chambers and the range tunnel, the two beam chambers and the three mirror chambers have them; the
far gates
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
depth; the chambers wait on `mirror debug` until it is in memory. Every mirror banner hangs at head
height, feet + 1, as a player builds one (stage 5 moved them all down a block).

Stage 5 is the deep gate work. `assets/Lab.shape` is a test shape, never shipped: the launcher
copies it into the test server's `shapes/gate/`. It has the cells no shipped shape carries (`[C]`,
`[S:C]`, `[RS]`), dials by sign, and frames in diamond, which no configured group claims, so the
plugin derives a `Diamond` group from it at startup (`gate-material-groups-autodiscover`).
`lib/gatebuild.js` builds a gate as a player does: Probe stands where `gate build <shape> [group]`
puts the preview on the site, then `gate preview place`, or lays every block `gate preview needs`
lists (with a wool scaffold where a block has nothing to go against), hangs a dial sign, presses
the button and runs `gate complete`. With it G1's `built preview` and `built hand` are real, which
makes the sign-dial shapes and Lab.shape buildable and their dials testable: the sign right-clicked
on and left-clicked back, a lever by `[RD]`, and Lab.shape's sign turned by pulses on `[RS]`. Every
G1 dial with chevrons is watched from Probe's client, and the chevrons must lock in the order of the
shape's `:L#n` cells (G8): each wave's last turn to lit before the kawoosh, in order, all lit.

`g3`, the Automation Bay, is a StandardSignDial gate built by preview with one peer, dialled by a
lever, a button, a pressure plate, a detector rail, a line of repeaters and a comparator by its
DHD; a lamp by the lever the plugin puts at `[RA]` must light while it is open and go out when it
shuts. `timeout-shutdown 0` keeps it open until somebody goes through (a dropped signal changes
nothing; the design's "the signal drop shuts it" is not what the plugin does or documents), and
presses every 2 s hold it open past a 5 s shutdown and never past `max-open-seconds`. A command
block runs each console form with `~` (`gate build`, `ring build`, `ring fire`, `mirror create`)
and without (`gate dial`, `gate force`, `beam admin send`), judged by its `LastOutput` and the
world. `g4`, the Build Bench, works through the preview actions one a run (layer, chevrons, dhd,
material, iris, activate, share to Probe2, place, a preview on a standing gate's DHD that fills a
knocked-out block, clear) and builds by hand, judged by what Probe is told, what `needs` lists and
the block displays its client (and Probe2's) is shown. `g5`, the Iris Chamber, has two gates twenty
apart, IrisA (Atlantis, a yellow glass iris) and IrisS (Standard, stone). Its checks are on the
block-change packets a watcher is sent in the opening: closing and opening must come in the
plugin's steps for each style (`lib/iris.js` works them out the way `IrisSweep` does), cell for
cell, a step every `gate-iris-step-ticks`, merged to `max(2, gate-iris-sweep-max-ticks / step)`
bands; on an open gate with its iris shut, the front is shown the iris in the plane and the horizon
(water, or ice behind a glass iris) a block behind it, behind is shown the water in the plane and
the iris a block toward the DHD, and the side the iris alone; an arrow at a shut iris is taken
away; a block put into the shut opening is refused.

The Facility Logbook (`lib/logbook.js`) is a written book every player is given on joining: page 1
its contents, each entry turning to its page; Your runs, Bot runs (each run PASS, FAIL or KNOWN,
the detail in its hover), a page per wing with a Go for the wing and each chamber (`/trigger`, so a
non-op can), Transit, and how to use the facility. A written book cannot change, so after every run
each holder's copy is put back in the slot they keep it in, never added to and never dropped. A
player who no longer holds one is not given another unasked: `!book` and the console's [Logbook]
replace the copy they hold, or give one in the first free slot if they hold none (or say there is
no room). Each section runs to as many pages as it needs, at most 14 lines of about 18 characters
to a page, so nothing is cut off. Its pages are NBT before 1.20.5, the
`written_book_content` component from it (with JSON-string pages to 1.21.4: unverified, since no
tested version takes that path), and SNBT pages with snake_case click keys from 1.21.5
(`text.bookItem`). The self-test's `logbook` section judges what a non-op Tester's client holds.

There are two self-test profiles. The full one runs the whole matrix (over 700 checks on one
server at stage 6, 600 of them the matrix's cells and their resets) and takes about an hour on one version; it is the one to iterate on, on 1.21.11.
`--quick` runs everything else the same but only a short matrix: one walk, one cart, one horse,
one pet, a bow, a throw, a dispenser, a dropped item, one refusal, a gallery gate, the first
#491 cell, a ring walk and a swap, one refusal from each ring chamber, a ring edit, two beam pads
and two dispatches, a mirror round and its hold, a wall refusal, a capture and a stamp (the
banner's patterns are read under another key on 1.20.4), a horse to the Range, a pad in the End,
a gate built by hand and one by preview, a lever and a command block at G3, a G4 activate and
share, three G5 iris cells, seven of S1's (the setting audit, bad values, gate and beam sounds,
the permission fallback and nodes, a ring's defaults), and one transit route per feature, in about
17 minutes. It is the cross-version check (text formats, entity ids, boats, the
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
shard's own world, so a sharded summary has more checks than a single run (981 at N = 4 with
stage 6's Systems console, which took 24.8 minutes on a jar built from main, peak 6.8 GB); transit, plates, boards, players and console run once, on the first shard. On this machine
(32 threads, 64 GB) a full 1.21.11 run took 61 minutes on one server, 31.5 at N = 2 (peak 3.6 GB,
CPU 54%), 21.3 at N = 3 (5.3 GB, 65%) and 16.6 to 19.3 at N = 4 (6.0 to 7.7 GB, 61 to 90%); N = 4
is the one to use here. `--quick` on two versions at once takes about 17 minutes (17.0 at stage 6, peak 3.7 GB).

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
counts it as a fault as well. A jar built from main since #540 (97741c8e) cannot print that line
at all, since the losing capture now finds the folder made and carries on, so the known entry only
matters for an older jar; give such a jar's run `--fixed 540`, and a return of the race is a fault.

`--cells <names>` runs only the matching matrix cells: names separated by `|`, each matched
anywhere in a cell's name, or at its start with `^` and its end with `$` (`--cells '^491'`,
`--cells 'tipped arrow|trident'`). It is a plain match, not a regular expression. A cell the plugin is known to fail is
expected to fail by the name of its failing check and is listed at the end of the run as a known
plugin failure, never hidden; `--fixed 491` (with `--plugin` pointing at a jar carrying that fix)
expects those cells to pass instead.

#### Systems (S1, stage 6)

`s1`, the Systems console on the mezzanine, audits the plugin's settings, sounds and permissions.
It is written for the plugin with #550's fixes (same-world-only for every crossing, every setting
applied at once, sign colours checked): against a jar without them, the cases that depend on them
fail, loudly.
It is a desk with no cell, so a case that needs a gate builds `Sys` on G1's Stand position and its
cleanup runs G1's reset, as the Permissions Desk does; ring cases use the Concourse floor (and R2's
and the tunnel's, put back by their resets), a beam the transit pads. Each group of cases is a file
in `chambers/s1/`, and every setting a case changes goes through `Config`, so it is put back after
the run like a chamber's `needs.config`. A false check also prints what it saw (`s1 <case>: false:
...`), since the summary names only the check.

- **The console** (`console.js`). Every setting the running plugin has, read off its own
  `config.yml` and counted against what `wormhole config` lists ("...and N more"), answers
  `wormhole config <name>` in both spellings. The jar's list is compared with this source tree's
  (`lib/settings.js` reads `DefaultSettings.java`), and a difference, a jar from another commit, is
  said in the log by name rather than failed on. `gate-sound-volume`, `GATE_SOUND_VOLUME` and
  `Gate-Sound-Volume` are one setting. `ring-sound` and `ring_sound` list exactly the names that
  hold it. A word for a switch, a word or a fraction for a number, and an unknown name for each
  fixed set (spin, iris animation, log level, ring access, style, slab and block, and the sign
  colours) are each refused in the plugin's own words and change nothing. An unknown setting is named as one, and a change is
  in `config.yml` at once.
- **Sounds** (`sounds.js`), as the packets Probe's client is sent (`lib/sounds.js`; a bot cannot
  hear). A dial of `Sys` by console plays the activation once, seven chevrons climbing in pitch
  from 0.8 to 1.5, the lock with the last at 0.8, the kawoosh at 0.7, the hum while open at 0.4 of
  `gate-sound-volume`, the iris shut (0.8) and open (1.0), and the shutdown; `gate-sounds-enabled
  false` silences all of it, `gate-sound-volume 0.5` sets every one, and a renamed sound plays as
  its new name (`none` plays nothing). Each trip also has the console play a chime where its sound
  comes from (the gate, a ring end, the beam pad), which the recorder must hear, so a deaf or
  out-of-range recorder fails rather than reading as silence. A ring trip is heard as the traveller hears it: at the near
  end the open, four rings climbing 0.8 to 1.4 and the flash out at 1.4; at the far end the flash
  in at 1.0, four rings back down and the close (each end plays its own, and sixteen blocks off the
  other is out of hearing). A beam plays charge, depart and arrive once each, depart to arrive 20
  ticks apart. Each `*-sounds-enabled false` leaves the same trip silent. Mirrors play no sound of
  their own, so they have no case.
- **Gate settings** (`gates.js`): `timeout-activate`, the use cooldown, `same-world-only` (a dial
  to another world refused; and set while a wormhole to the Range is open, a walker and a cart's
  rider each refused and told once, the cart put back at its own gate, against the same cart at
  the default, which crosses), the
  preview limits and lifetime, the default iris animation and dial spin, the arrival splash,
  `log-level FINE`, and the name sign's colours and glow. Where a change could read as nothing,
  the same thing is done first without it: the default draws the splash, sweeps the iris, rests
  on the top chevron, stands a preview.
- **Ring settings** (`rings.js`): the defaults a pair Probe builds takes (access, style, pad light,
  flash), each limit's refusal and the same pair made once it is raised (separation, pairs per
  player, ceiling drop, link distance), the barrier outline a recharging pair shows and how long,
  and the timings of a trip read off its sounds and lights (deploy, settle, flash, hold, linger).
- **The rest** (`more.js`): the mirror proximity distance, view depth and fog; metrics and
  CoreProtect with neither installed; and settings that once waited for a restart, now in force at
  once: the hum's interval (heard), the entity scan interval (an item lying in an open gate waits
  for the rescheduled sweep, against one sent at the default), and placeholders and economy (each
  says at once that its plugin is missing; the start before said neither).
- **Permissions** (`permissions.js`), by Probe2, never an op, with no permissions plugin: the
  plugin falls back to its simple mode at start, so Probe2 may use a gate, `/wormhole list` and the
  compass, but not preview, configure or remove (and `go` to a gate is no gate to it). With
  `permissions-auto-fallback false`, which the plugin follows at every check, it is held to the
  nodes and their `plugin.yml` defaults, so the DHD, the list and the compass are refused too; an
  op is not. With
  `wormhole-use-is-teleport true` it cannot walk through an open gate, and with false it can. These
  refuse a run with LuckPerms installed: the Permissions Desk covers that.

A matrix cell may also hold `settings` of its own on top of its chamber's, with `because` saying
what they are meant to stop: `g1 wolf to the Range, pets-follow-owner false` expects the wolf, alive,
not to come along (into the nether, since within one world vanilla brings a following wolf to its
owner anyway), against `g1 wolf to the Range`, the same trip at the default, where it does. Every
pet cell first checks the pet is there and alive, so a dead or missing pet fails as that.

`.local-server/plugins-extra/` (or `--plugins-extra <dir>`) is the design's drop folder: any jar
in it is copied into the test server's `plugins/` at start and recorded in
`plugins/.wx-extras.json` with its SHA-256 as written, so a later run without it takes it out,
while it is still those bytes, and never a jar it did not write (one of the same name that no run
copied is refused, or left alone if it is the same bytes; a companion's name is refused). It is for what `--with`
does not pin: an economy plugin for Vault, say. Tried with ViaVersion: copied in and loaded, then
taken out by the next run without it.

Every setting, and where it is changed and its effect seen:

| Settings | Where |
|---|---|
| `log-level`, `coreprotect-enabled`, `metrics-enabled`, `placeholders-enabled`, `economy-enabled`, `permissions-auto-fallback`, `wormhole-use-is-teleport` | S1 (`log level`, `coreprotect`, `metrics`, `integrations at once`, the permission cases) |
| `pets-follow-owner` | `g1 wolf to the Range, pets-follow-owner false` |
| `timeout-activate`, `use-cooldown-*`, `same-world-only`, `gate-preview-*`, `gate-arrival-splash-ticks`, `gate-dial-spin`, `gate-iris-animation`, `entity-scan-interval-ticks` | S1 (gate cases; `scan at once`) |
| `gate-sounds-enabled`, `gate-sound-volume`, `gate-sound-kawoosh`, `gate-sound-chevron`, `gate-sound-ambient-ticks`; the other gate sound names heard at their defaults | S1 (sound cases, `hum at once`) |
| `sign-glowing-text`, `sign-color-gate-name`, `-network`, `-owner` | S1 `sign colours`, `sign colour bad` |
| `ring-default-access`, `-style`, `-light`, `-flash`, `ring-min-separation`, `ring-max-pairs-per-player`, `ring-max-ceiling-drop`, `ring-max-link-distance`, `ring-outline-*`, `ring-deploy/settle/flash/hold/lights-linger-ticks`, `ring-sounds-enabled` | S1 (ring cases) |
| `beam-sounds-enabled` | S1 `beam sounds off` |
| `mirror-proximity-distance`, `mirror-view-depth`, `mirror-fog-at-depth` | S1 (mirror cases) |
| `timeout-shutdown`, `max-open-seconds`, `redstone-extend-open-time` | G3 |
| `gate-iris-step-ticks`, `gate-iris-sweep-max-ticks` | G5 |
| `ring-countdown-ticks`, `ring-cooldown-ticks`, `ring-max-link-height` | R1, R5, R3 |
| the beam timings, `beam-use-cooldown-*` | B1, B2 |
| `mirror-per-world-limit`, `mirror-approach-message` | M1, M2 |

Not changed by any case, and why:

- `permissions-support-disable`: off by default, and with no permissions plugin the simple mode
  comes from the fallback anyway; the node cases switch the fallback off instead. #550 retires
  `help-support-disable` (nothing read it), and makes `ring-default-material` the slab a ring is
  drawn in when its own is one the server lacks, which no case stages.
- `economy-use-cost`, `economy-build-cost`, `beam-economy-use-cost`: every cost is 0 without Vault
  and an economy plugin. `plugins-extra` can supply them; no case is written for them, since none
  was available here to prove one against.
- `gate-iris-horizon-ticks` and `mirror-proximity-ticks`: sweep periods, rescheduled at once like
  the hum's and the scan's (which are measured); G5's layer cells see the horizon drawn at the
  default.
- `gate-material-groups-autodiscover`: the first start adds Lab.shape's Diamond group with the
  default; false matters only for a shape no group claims, and an added group stays in `config.yml`.
- `sign-color-selected`, `sign-color-neighbour`, `sign-dial-match-material`: only a sign-dial gate
  shows them, and only a player's build makes one (the console refuses it); G1's and G3's sign
  cells read the dial sign's text, not its colours or wood.
- `ring-reach`: how far up `ring create` looks and how deep a floor ring carries; no case varies it.
- `ring-sound-refused`: played only when a transport cannot start on entering (a blocked end),
  which no case stages; the other ring and beam sound names and volumes are heard at their
  defaults.

What stage 6 found, on a jar built from main (eb86f209, 1.21.11); #550 fixes the first three, and
the cells now expect its behaviour:

- `same-world-only` stopped a player walking into a gate to another world but not one riding in:
  a cart carried Probe to the Range, and a dial to another world was not refused at all. With
  #550 the dial is refused, and a wormhole open when the setting is turned on refuses every
  crossing, the cart put back at its own gate (`s1 same world dial`, `s1 same world while open`).
- Settings changed by `wormhole config` that waited for a restart, though the guide says a change
  needs no reload and no restart: the hum's interval was measured unchanged (one hum in five
  seconds at 20 ticks); by the source the same was so of the entity scan, the iris horizon, the
  mirror sweep, the permission fallback, placeholders and economy. With #550 each applies at once
  (`s1 hum at once`, `s1 scan at once`, `s1 integrations at once`, the node permission cases).
- The sign colours were not checked: `sign-color-gate-name PINK` was "now PINK", and a sign
  written after showed its default colour. With #550 it is refused, naming the sixteen colours
  (`s1 sign colour bad`).
- `help-support-disable` and `ring-default-material` were read by nothing; #550 retires the one and
  puts the other to use.
- By the source, not seen in game: `log-level` sets the level of the server's own logger, not the
  plugin's (`WormholeXTreme.applyLogLevel`).
- `use-cooldown` has no op bypass: Probe, an op, is refused too, as the source has it.
- A preview's expiry is silent: it is gone within a minute and ten seconds of
  `gate-preview-minutes 1`, with nothing said.

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
pinned build is somebody's own: the run is refused rather than overwrite it (one byte for byte the
pinned build is adopted, and taken out by a later run without it). Only bare names in
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
switched on. The install owns these two switches, not the settings journal: a cell that changed one
and was killed before putting it back leaves it journalled, and the next start takes it out of the
journal (as the value to go back to) before `recover()` could turn it against what is installed.
`npm test --prefix scripts/facility` runs those rules without a server.

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

Stage 5's three known plugin failures are fixed by #546, so `g3 detector rail`, `g4 lenient` and
`g5 the puller watches the sweep` now expect a pass, and a jar from before it fails them:

- The build guide took a lenient `[S:C]` chevron for a strict `[C]`: `needs` asked for the
  chevron block there and the guide marked the frame block wrong, though detection takes either,
  so a gate built with the frame block there completed and the guide never said it was built.
- The player who pulled an iris lever never saw the sweep: their arm swing near a drawn iris
  redrew it whole a tick later, without asking `StargateIrisAnimator.isSweeping`.
- A detector rail by the DHD never dialled on Paper 1.21.11: Paper raises its `BlockRedstoneEvent`
  with the old current 15 as well as the new (`DetectorRailBlock.checkPressed` passes the new
  state twice; 1.20.4 and 26.1.2 report 0 then 15), and the listener took only a rise from 0.

Stage 2's (#536 and #537 are fixed on main by #542 and #543, so their cells now expect a pass:
the self-test is set for a jar built from current main, and an older jar fails them):

- #536: a tipped arrow comes out of the far gate as a plain arrow: the plugin re-makes a projectile at
  the far end and does not copy the arrow's potion. The arrow is checked to leave the bow as a
  tipped arrow of slowness (`item: tipped_arrow` with `potion_contents` slowness on 1.21.11) and
  arrives as `item: arrow` with none.
- #536 too: a Loyalty trident comes out of the far gate as a plain trident (its `item` has no
  enchantments; only its `weapon` still has Loyalty III), sticks where it lands and never comes
  back. Thrown in survival away from any gate, the same trident is back in about a second. (While
  Probe threw in creative, "it came back" passed without it.) #542 brings it back on Paper; on
  Spigot, which has no loyalty API, it keeps its enchantments but cannot return.
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
  expects that cell to fail and says why.

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
tester's path through the console, the Logbook, every reset, that the plugin holds nothing the
facility did not make (its fixture gates, the transit ring pair and beam destinations, no beam
places, no mirrors), that each setting any cell changed is back, and that the plugin logged no
fault.
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

#### Seeing the facility (`--viewer`, `--shots`, `--schematics`)

The campus was designed from coordinates; these show it. None of them runs unless asked, so the
self-test is as it was without them.

```bash
node scripts/facility/run-facility.js 1.21.11 --viewer           # hold, with a browser view of Probe
node scripts/facility/run-facility.js 1.21.11 --shots all        # a PNG per vantage point, then stop
node scripts/facility/run-facility.js 1.21.11 --shots gate-room,range --selftest
node scripts/facility/run-facility.js 1.21.11 --with worldedit --schematics ../my-schematics
```

`--viewer` serves [prismarine-viewer](https://github.com/PrismarineJS/prismarine-viewer) (pinned in
`scripts/facility/package.json`) on Probe, on 127.0.0.1 only, at port 3007 + (game port − 25590)
(3007 on the default port; `--viewer-port` picks another; a port under 1024 is refused). `/`
orbits round Probe (drag to turn, scroll to zoom); `/first/` looks out of Probe's eyes. The
launcher prints both addresses, and the viewer stops with it. A request whose Host, or whose
Origin when a browser sends one, is not 127.0.0.1 or localhost is refused, so a web page open
elsewhere cannot drive it. Blocks are drawn; text displays (the boards and plaques) are not.
After a restart (the Map and Region Desks) it serves the new Probe on the same port.

prismarine-viewer 1.33.0 has textures and models up to 1.21.4 and draws a newer 1.x with the
newest of that 1.x: 1.20.4 with its 1.20.1 assets, 1.21.11 with its 1.21.4. It has nothing for
26.x, so `--viewer` and `--shots` refuse 26.1.2 by name before a server starts. The servers number
their block states differently from those assets (1.21.5 onwards inserted blocks), so
`lib/viewer.js` translates every chunk and block update, by block name and properties, into the
assets' numbering. The two blocks renamed in between (`short_grass` was `grass`, `iron_chain` was
`chain`) are drawn by their old names; a block the assets do not have at all (1.21.11's shelves
and copper chests, 1.20.4's tuff bricks and crafter, say) is drawn as stone, and the launcher
lists them by name. Every block the facility builds is a 1.20.4 one, so the campus is drawn
whole. Its renderer draws 256 blocks of height, from y 0 (the world before 1.18), so the page is
shown the world raised by the dimension's depth below 0: the overworld from -64 to 191 is drawn,
and the launcher says once if anything above 191 was left out.

`--shots` flies Probe to each vantage point in `campus.SHOTS` (`all`, or names separated by
commas; one or more a wing: `gate-room`, `atrium`, `systems-mezzanine`, `gate-hall`,
`gate-hall-deck`, `g1-control`, `ring-concourse`, `shaft-window`, `beam-lab`, `b1-pads`,
`mirror-hall`, `mirror-optics`, `m1-round`, `menagerie`, `range`, `annex`), opens the viewer's
first-person page in a headless browser and saves `.local-server/shots/<version>/<name>.png`
(1280 × 720). A shot is taken once three frames a second apart are the same with no chunk sent to
that page between them (two would take a pause in SwiftShader's meshing for the end), and it
counts only if the page was sent chunks and at least 5% of the frame is not the page's empty
sky: blank frames are the same too. The page names itself in its address, so only its own
chunks count, whatever other viewer pages are open. A shot that fails those is saved all the
same, named in the output, and fails the run, whether the run then stops, holds or self-tests
(where each shot is a check in a `shots` section). `WX_SHOTS_SETTLE_MS` shortens the wait, to
see a failed shot fail a run. It prints the paths at the end. A
cell's gallery looks through tinted glass, which is all a seat beside it shows, so B1 and M1 are
seen from inside, high by the gallery wall. The browser is an installed Chrome or Edge (Edge
comes with Windows), driven by `puppeteer-core`, which downloads nothing; `WX_BROWSER` names
another Chromium, such as a `chrome-headless-shell`. WebGL is drawn by SwiftShader, in software,
so a machine with no GPU draws the same picture. Shots are taken after the fixtures, so the
gates, rings and pads are in them; then `--selftest` runs, `--viewer` holds, or, with neither,
the launcher stops. A vantage point is feet position, yaw (0 south,
−90 east) and pitch (down is positive), so a new one is a line in `campus.SHOTS`.

`--schematics <folder>` pastes WorldEdit schematics (Sponge `.schem`, as `//schem save` writes
them) after the build and before Probe joins: the placements in `campus.SCHEMATICS` and in
`<folder>/placements.json`, each `{ "file": "x.schem", "at": { "x": 0, "y": 0, "z": 0 },
"rotation": 90, "dim": "minecraft:overworld" }` (rotation 0, 90, 180 or 270, clockwise as
`//rotate`; `dim` defaults to the overworld), the files read from the folder. It needs `--with
worldedit`. `at` is where the schematic's origin goes: where its maker stood for `//copy`, so
the box it fills runs from there as it did from them. **A paste replaces everything in its box,
air included**: it is a plain `//paste`, never `-a`, since a designer's empty space is meant. So
before the server starts, each schematic's whole box where it would land is checked against every
volume the decoration guardrail protects (`wings/decor/guard.js`: cells and their clear volumes,
footprints, pads, rings, gates, lanes, plates, walk-in lines, mirror spots), and a box reaching
into any of them, by so much as a block, is refused with what it reaches into. That covers what
the tests use, not the campus itself: a box over a room's wall, a corridor or a doorway passes and
replaces it, so a set piece placed there can still wall off a route a test walks. WorldEdit pastes them from the console, with no player and no API:
`//world <world>`, `//pos1 x,y,z`, `/schem load`, `//rotate`, `//paste` (one slash fewer on
1.20.4, whose console keeps the slash 1.21.11's drops; the launcher tries `//world`, then
`/world`). The console has no position, so the paste puts the origin at pos #1; WorldEdit
7.4.5's `//toggleplace` refuses the console outright. `/schem load` reads the file off the main
thread and says so later, so the launcher waits for that file's "loaded" line. A paste that
fails is a setup problem, and fails a `--selftest`. Where a box lands was checked against
WorldEdit itself: a schematic copied with its origin at the far corner, saved by 7.4.5 (format
3) and 7.2.20 (format 2), then pasted plain and turned 90 and 270, landed exactly in the boxes
`lib/schematics.js` works out (`scripts/facility/test/fixtures/`). A placement may carry
`"minVersion": "1.21.11"`: a run on an older version leaves it out and says so.

#### Watching the self-test (`--watch`)

To stand in the lab and see the bots run the cells:

```bash
node scripts/facility/run-facility.js 1.21.11 --selftest --watch            # waits for anyone
node scripts/facility/run-facility.js 1.21.11 --selftest --watch YourName --cells '^r1'
.\scripts\facility\lab.ps1 -Version 1.21.11 -Watch -Quick                     # its own window
scripts/facility/lab.sh -v 1.21.11 -W -n YourName -q
```

The self-test builds the campus as usual, then says to join and waits (for that name, or for
whoever joins first). From then on, before each matrix cell you are moved to its chamber's
vantage point (`campus.WATCH`), facing the cell, and told in chat which cell it is, what it
expects, what to look for and what each option it sets means; after it, its PASS, FAIL or
KNOWN. Each other section (transit, plates, console and the rest) is announced from a vantage
over the atrium. `lab.ps1 -Watch` and `lab.sh -W` start from a fresh world, since the
self-test's first checks are that every cell is as built.

A watcher must not change a result, and the plugin takes no account of game mode: it counts
any player within `mirror-proximity-distance` (16) of a mirror (which holds a second click and
keeps the round where it was), carries any player standing in a ring, and sends any entity in a
gate's opening. So everyone who joins while a watched self-test runs, other than Probe, Probe2
and Tester, is a watcher: in spectator mode with night vision, tagged `wx_watcher`, never
welcomed (no adventure mode, no atrium, no Logbook), and refused by the console, whose clicks
would otherwise run a chamber or change a menu under the test. A tick function (`wx:watcher`)
puts a watcher who strays more than `WATCH_LEASH` (3) blocks from the vantage marker back on it,
and `test/watcher.test.js` checks every vantage point stays, leash and all, 20 blocks from every
mirror banner, out of every ring's volume and outside every gate a cell builds (each check
was mutated into failing). The facility's own `@p` selectors (the tp plates and the beam
pads' prompt) leave out `wx_watcher`, and G1's `tester` launcher never picks a watcher. So a
mirror's view stays a banner to a watcher: it opens only within 16 blocks. A watcher who leaves
is let go and the run carries on; one who comes back is put back where the run is, and one who
joins before the self-test starts is held over the atrium. What the leash cannot stop is a click,
or speed: a spectator who clicks Probe, or picks a player from the spectator menu, is carried
along with it until the next tick's leash puts them back, and one sprint-flying at full speed
covers about four blocks in the tick before it does, more than the test's one-block margin. So
look around, but do not click the bots or race off mid-cell. When the self-test ends the
watchers' tag and night vision are taken away, and a welcome takes away any tag (and, with no
watched run going, any leash marker) a killed run left in the kept world. A watcher step that fails (a
console command timing out) is logged and the self-test goes on as an unwatched one would.

`--watch-bot` is `--watch` with a stand-in client, `Watcher`, that joins as a person would and
stays where it is put; it works with `--versions` and `--shards` (one per server), to show a
watcher changes nothing. On 1.20.4, 1.21.11 and 26.1.2 the full self-test with one gave the same
result for every check as without one (the PR has the comparison).

#### Design mode (`--design`, `--design-check`, `--design-export`, `--design-import`)

For the designer the facility is being decorated by (`design/facility/BRIEF.md`, "Run it
yourself"): `lab.ps1 -Design -Op Name -Plugin <jar>` (`lab.sh -d -o Name -p <jar>`), or

```bash
node scripts/facility/run-facility.js --design --op YourName --plugin WormholeXTreme.jar
node scripts/facility/run-facility.js --design-check      # the server stopped: start, check, stop
node scripts/facility/run-facility.js --design-export     # the server stopped: start, export, stop (--full: the worlds too)
node scripts/facility/run-facility.js 1.21.11 --design-import .local-server/exports/facility-design-2026-10-01.zip --selftest --quick
```

Design mode runs 1.21.11 only, adds WorldEdit, and keeps its world in
`.local-server/design-1.21.11/` (`-<port>` on another port), a folder no test run uses. It listens
on 127.0.0.1 only unless `--design-open` (`-Open`, `-O`); online-mode is off, so an open server
lets anyone who reaches it join under any name, an op's included. The first session runs with the
whitelist on and nobody opped: it builds the campus, then the session fixtures (the menagerie
unstocked; Probe is whitelisted and opped for them, then leaves, and is deopped in a `finally`
whose result is checked), fills the air of every protected volume and each board's block with a
placeholder (pink stained glass for test volumes, green for the rest), saves a baseline schematic
of each export area (each `campus.FORCELOAD` rectangle grown by 16, y −63..95 in the overworld)
in `wx-design/baseline/`, writes `wx-design/state.json`, and only then turns the whitelist off
and ops the `--op` players. A start that finds no state starts the folder over (worlds, ops,
whitelist); one that finds Probe still opped deops it. Later sessions keep the world as it is.
Ops are put in creative on joining; the server's default is creative and peaceful. The plugin jar
is `--plugin`, else the one the folder already has, else a Maven build (the launcher cannot fetch
a release jar).

"Protected" here is the union over every supported version from 1.21.11 on
(`version.SUPPORTED`, `design.maskVersions()`): the boxes of `wings/decor/guard.js` and the boards,
on each version a design is pasted on. The same union is marked, checked and masked.

An op's `check` in chat (or `--design-check`) saves the world, makes every player a spectator
(their modes come back after), copies each area with WorldEdit's console (`//copy -e`,
`/schem save`) and compares it with the baseline (`lib/design.js`, `compareArea`):
- a block that differs by name inside a protected volume (a placeholder, or air where one was,
  is fine);
- in a chamber's 2-block skin: an active part that is not the campus's (rule 3's list: redstone
  of every kind, rails, signs, buttons, levers, plates, doors, trapdoors, fence gates, bells,
  lightning rods, TNT, jukeboxes, lecterns, banners, hoppers, pistons, water, lava, fire), a
  campus active part or block with data changed in its full state or data (what using it changes,
  `powered`, `open`, `lit` and `triggered`, aside), and a campus block replaced by air; re-cladding
  the floor with another block is not flagged;
- anywhere, a block no design may hold that the campus did not put there (command blocks,
  spawners, trial spawners, vaults, structure, jigsaw and test blocks), and a designer's block
  entity with a click event in its text;
- a new entity other than an item frame or armour stand, either of those in a volume or skin, or
  carrying data outside the whitelist (`design.ENTITY_KEYS`: no Passengers, Tags or UUID). Dropped
  items, falling blocks and experience orbs are not looked at.

The first eight go to chat; the whole report to `wx-design/check.txt`. `export` (or
`--design-export`) does the same pass and writes `.local-server/exports/facility-design-<date>.zip`
(`lib/zip.js`, no tool needed): per area a schematic with **structure void at every protected
position**, and at every campus block a design may not hold (the campus builds those itself);
every placeholder, structure void and designer's forbidden block elsewhere turned to air; the
block entities of kept blocks with every click event taken out of their text (JSON strings and
compounds alike, books in lecterns and chests included); only the designer's item frames and
armour stands outside volumes and skins, their data cut to the whitelist; `placements.json` with
each area at its corner, `"guarded": true, "minVersion": "1.21.11"`; `manifest.json`; and
`check.txt`. That is a few MB. `export full` (`--full`, `-Full`, `lab.sh -d -e -f`) adds the three
worlds under `worlds/`, read with saving off.

A hand-edited zip is treated as hostile (review of PR #551). Every palette entry must be a
canonical state (`minecraft:name[key=value,...]`, lower case: WorldEdit would default a missing
namespace and lower-case a name, so `command_block` or `minecraft:Spawner` would otherwise slip
past a name check, and an entry it cannot parse is pasted as air); the mask must be exactly
`minecraft:structure_void`, since `minecraft:structure_void[waterlogged=false]` would be parsed as
air and wipe the volume; the DataVersion must be 1.21.11's (4671) or newer, so no data fixer
rewrites text on load; clicks are found by parsing every JSON string and walking its keys, so an
escaped `\u0063lickEvent` is found; an item frame must hang (`block_pos`, or `TileX/Y/Z`) in the
block its position is in, and that block is the one checked; end portals, end gateways, nether
portals, shriekers that can summon, hives with bees and loaded dispensers, droppers and crafters
are refused like command blocks; and at every skin position an active block or a block entity
must be structure void (export masks the campus's own there). `--design-import` refuses a
placements.json with any placement that is not `"guarded": true` with a `minVersion`, and a zip
whose `check.txt` does not say "No problems" unless `--accept-check-problems`. A gzip that unpacks
past 256 MB, an entry running past the zip's end, and a block-data number past five bytes or the
palette are refused. An entity in two overlapping areas is exported by the first area only.

A guarded placement is how a whole wing's box can be pasted when it holds cells, pads and gates:
the guardrail refuses an ordinary box that touches one, since a plain paste replaces everything in
it. A guarded one is pasted `//paste -e -m !minecraft:structure_void`, so WorldEdit leaves every
protected position as the campus built it. A design zip is untrusted: before the server starts,
`--schematics` reads each guarded schematic whole (Sponge version 3 only, origin at its minimum
corner, palette indices each used once and in range) and refuses it unless every position the
guardrail protects on that run's version holds structure void, and it holds no placeholder, no
forbidden block, no click event, and no entity but an item frame or armour stand with whitelisted
data outside the volumes and skins. `--design-import <zip>` unpacks into
`.local-server/imports/<name>/` (a plain name; never `imports/` itself) only `placements.json`,
`manifest.json`, `check.txt` and the `.schem` files, never the worlds, reading the zip a piece at a
time: every entry name must be a plain relative path (no `..`, absolute path, drive letter or
backslash) or nothing is written, each entry is inflated to no more than the size it declares (at
most 1 GB), and a Zip64 zip is refused. It prints the manifest's strings cleaned of control
characters, warns if the Minecraft version or the facility commit differs from this checkout's, and
runs the export as `--schematics`, adding WorldEdit on 1.21.11 and later; on 1.20.4 every
placement is left out and the run is the plain campus.

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
