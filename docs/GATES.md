# Stargates — Design

How a gate is detected, stored, dialled, drawn and travelled through, and why each of those
works the way it does. The [README](../README.md) is the server owner's guide and says what
everything does; this says why. Rings are the other half of the plugin and have their own
document, [RINGS.md](RINGS.md). Plugin authors want [API.md](API.md).

A gate is a named, addressable structure: a frame the player builds and leaves standing, a
sign, a DHD, an iris, redstone hooks and a network. Rings are the deliberate opposite of all
of that — [RINGS.md](RINGS.md) opens with the comparison.

## Contents

- [Anatomy](#anatomy)
- [Shapes](#shapes)
- [Palettes are separate from shapes](#palettes-are-separate-from-shapes)
- [Detection](#detection)
- [Building](#building)
- [Storage](#storage)
- [Networks](#networks)
- [Dialling](#dialling)
- [Timers](#timers)
- [The iris](#the-iris)
- [The portal is drawn, not built](#the-portal-is-drawn-not-built)
- [Animation](#animation)
- [Sound](#sound)
- [Travelling](#travelling)
- [Everything that is not a player](#everything-that-is-not-a-player)
- [Permissions](#permissions)
- [Commands](#commands)
- [Config](#config)
- [Layout](#layout)
- [What the tests guard](#what-the-tests-guard)

## Anatomy

A gate is one `Stargate` object holding the world positions of everything the shape marked:

| Part | Marker | What it is |
|---|---|---|
| Frame | `[S]` | The ring itself, and what the palette is identified by |
| Chevrons | `[C]` | Frame blocks built from a second material, so they read as chevrons before they light |
| Portal | `[P]` | Air until the gate opens, then the drawn event horizon |
| Name sign | `:N` | Always placed; shows the gate's name, network and owner |
| Dial sign | `:D` | Optional; makes the gate sign-dialled rather than `/dial`-only |
| DHD | `:A` | The button or lever that activates it |
| Iris lever | `:IA` | Optional; without it the gate cannot take an iris |
| Player arrival | `:EP` | Where a traveller's feet land |
| Minecart arrival | `:EM` | Where a cart's wheels land |
| Lights | `:L#n` | What lights during the dialling sequence, in order |
| Woosh | `:W#n` | The wave layers of the opening animation |
| Redstone | `:RA` `:RD` `:RS` | Where the redstone components for activation, dialling and sign cycling go |

Everything else about a gate — its name, owner, network, iris code, target — is state on the
object rather than something built.

## Shapes

Shapes live in `plugins/WormholeXTreme/GateShapes/` as `.shape` files, eleven of them
shipped. A shape is a stack of numbered layers, each a grid of bracketed cells, and a
handful of `KEY=value` lines. The user-facing format is documented in the
[README](../README.md#shapes); the design notes are these.

**Layers, rather than one grid.** A gate is a 3D object even when it looks flat: the DHD
stands off the frame, redstone sits behind it, and the woosh pushes out in front. One layer
is the degenerate case, not the model.

**An unrecognised `KEY=` line is ignored, not an error.** Shape files outlive the plugin
version they were written for, in both directions. The same applies to a `*_MATERIAL=` line
naming a block this server does not know: the setting is left alone and the palette default
stands, rather than the whole shape failing to load.

**A shape may pin its own materials.** `PORTAL_MATERIAL`, `IRIS_MATERIAL`,
`STARGATE_MATERIAL`, `LIGHT_MATERIAL`, `SIGN_MATERIAL` and `CHEVRON_MATERIAL` override the
palette for that shape. `CHEVRON_MATERIAL` defaults to null rather than to the frame
material, because every gate standing in every world today has frame material in its chevron
cells — a shape that does not ask for distinct chevrons has to go on accepting exactly what
it accepted before.

**The redstone markers accept two conventions and both are right.** Written bare, `[RA]`,
the cell is not a frame block: it is empty space above one, and the redstone goes in it.
Written `[S:RA]` the cell *is* the frame block, so the redstone belongs one block higher.
Every shipped shape uses the bare form; the other is what older shapes used and still works.
Both exist for the same reason — landing the component on a cell nothing is built in.

## Palettes are separate from shapes

A shape describes geometry. A `MaterialGroup` describes what that geometry is made of — the
Standard obsidian gate, the Atlantis lapis one — and they are separate on purpose.

Before the split, every material variant needed its own `.shape` file duplicating the whole
layout. Detection scans every registered shape in turn, so each variant added a full extra
geometry scan to every detection attempt. A server offering twenty palettes paid for twenty
scans per click.

Groups are resolved instead with one map lookup keyed on the frame material actually found
in the world, so twenty palettes cost nothing per detection. The first group declared in
`config.yml` is the default, and the maps are replaced wholesale on reload behind volatile
references, so readers never lock.

Chevron cells are held apart from frame cells for the same reason: the palette is identified
by the first frame block found, and a chevron in that list would have a gate fronted with
lamps resolve to the lamp palette, or to none at all.

## Detection

A player clicks a button or lever. Every loaded 3D shape is tried against that position, and
the best match wins.

**The cheap test runs first.** `isPossibleGateFrameMaterial` asks two O(1) sets — the
materials any loaded shape builds frames from, and the materials any configured group is
keyed on — and rules the position out before any geometry scan is paid for.

**More than one shape routinely matches the same build.** Detection reads only frame and
portal cells, and a sign-dial shape puts its DHD where its plain twin writes `[I]`, so
anything built as `StandardSignDial` also satisfies `Standard`, and `Horizontal` and
`HorizontalSignDial` are byte-identical once markers are stripped. `beatsBestMatch` ranks
them, most significant test first:

1. **A dial sign was actually found.** Only a shape carrying `:D` looks for one, and finding
   one proves the player built a sign gate. Without this test `HorizontalSignDial` could
   never be detected: `Horizontal` came back first and then overwrote the player's dial sign
   with its own name sign.
2. **`REDSTONE_ACTIVATED=TRUE`.** No shipped pair needs this now, but custom shapes can
   still be written as redstone twins.
3. **More frame blocks.** The shape accounting for more of what is actually built is the
   more specific description of it. This is what settles `MinimalSignDial` against
   `Minimal`.
4. **Shape name.** Nothing left to separate them, so decide by something stable. Shapes live
   in a `ConcurrentHashMap` keyed by name: iteration order is arbitrary, and adding a twelfth
   shape resizes the table and reshuffles all of it. A server should not get a different gate
   for adding an unrelated custom shape.

Tests 3 and 4 were both bugs first. Before them, which of two matching shapes won came down
to where their names happened to hash.

## Building

Two steps, because a gate needs a name and the plugin cannot ask for one mid-click.

1. `/wormhole gate build <shape>` remembers which shape this player is building.
2. They lay the frame and click the DHD position. Detection runs; the result is stashed in
   `incompleteStargates`, keyed by the player.
3. `/wormhole gate complete <name> [idc=CODE] [net=NETWORK]` names it, registers it, places
   the name sign and lever, saves it, and fires `StargateCreatedEvent`.

Every block of the gate goes into `allGateBlocks` — a flat `Location -> Stargate` map, which
is what `getGateFromBlock` reads on the move path — and into `GateSpatialIndex`, which
buckets gate block locations by chunk for area queries such as "is there a gate near here".

`/wormhole gate refresh` puts the player in refresh mode; their next DHD click re-detects the
geometry from scratch, keeping the name, owner, IDC and network, and re-saves. No blocks are
touched, and no removal event is raised — a refresh is not the gate going away, so listeners
are not told to discard what they know about it.

## Storage

One YAML file per gate, in
`plugins/WormholeXTreme/WormholeXTremeDB/gates/`:

```yaml
Name: Base
OwnerUUID: 069a79f4-44e9-4726-a5be-fca90e38aaf5
OwnerName: Justin
Network: ""
WorldName: world
WorldEnvironment: NORMAL
GateShape: Standard
GateData: <base64>
```

The readable fields are what a server owner might want to edit or grep. `GateData` is the
geometry: every block position, the arrival points, the facing, the flags, packed as bytes.

**`GateData` carries nine save versions.** Files written by any Wormhole X-Treme since
version 3 still load. This is legacy weight the rings deliberately did not inherit — they are
plain YAML from the start — but the gates cannot shed it without stranding worlds.

**Version 9 exists because version 8 wrote `Material.ordinal()`.** An ordinal is a property
of the enum's declaration order in the Bukkit jar the gate was saved against, and that order
shifts whenever Minecraft adds or removes a block. A gate saved on one server version and
read on another came back with a different material — obsidian becoming glass, an iris
becoming air — with nothing to show for it. Version 9 writes length-prefixed names. A
material that genuinely no longer exists resolves to null and falls back to the shape or
palette default.

**Loading tolerates damage per file.** The gates directory is read at startup, and one
corrupt file taking the whole load down would lose every gate on the server. A file that
will not parse is logged and skipped.

**Owner is a UUID, with a legacy path.** `OwnerUUID` is what is written; a file old enough
to predate it names the owner in `Owner` as a plain name. A stored `OwnerName` equal to the
owner id is not a name — it is what an old save bug wrote — so it is treated as absent and
resolved again, which heals the file.

Gates whose arrival point still sits inside the ring have it moved clear on load, reported in
one line. A legacy SQLite database left over from an older Wormhole X-Treme is noticed at
startup and announced; `/wormhole gate import` reads it in through `LegacyDatabaseImporter`.

## Networks

A `StargateNetwork` is a name and two lists: every gate on it, and the sign-dialled subset.
Gates with no network form the implicit public pool and see each other.

The pool matters because it is what a dial sign cycles through: a named-network gate sees
only peers on the same network, a networkless one sees every other networkless gate. The
list is sorted by name, so the order a click walks is the same order a saved index is read
against.

## Dialling

Four ways in, all reaching the same handshake: the DHD button or lever, a dial sign, a
redstone signal, and `/dial`.

**The sign shows four lines**, the gate's own name and three destinations:

```
-GateName-
PreviousGate
>CurrentGate<
NextGate
```

Right-click steps forward, left-click back. The selected line is wrapped in markers as well
as coloured, so it still reads as chosen to a colourblind player or on a server that has
turned sign colours off.

**A gate's selection is two things, and only one of them is saved.** The index is stored with
the gate; the `Stargate` at that index is not — it is worked out from the network when the
sign is clicked. So a freshly loaded gate had a sign in the world naming a destination, an
index agreeing with it, and no destination object at all, and pressing its button dialled
nothing. Resolving it has to happen *without* advancing the index: the first attempt at a fix
pretended somebody had clicked, which moved the selection on by one, so the first press after
a restart dialled nothing and the second dialled the gate *after* the one the sign showed.

**The handshake activates the local end first.** The target is not assigned until local
activation succeeds, because the local activation path clears the target — assigning first
caused NPEs and aborted dials. If the local end fails, nothing is left half-connected. Once
both ends are up, the destination's chunks are pre-loaded so travellers do not fall through
ungenerated terrain.

A dial is refused when the target's iris is closed, when the target is already active, or
when another active gate already points at it. `/wormhole gate force` bypasses those.

## Timers

Three, and they interact.

| | What it bounds |
|---|---|
| `TIMEOUT_ACTIVATE` | How long a lit gate waits for a destination before giving up |
| `TIMEOUT_SHUTDOWN` | How long an open wormhole stays open after it was last dialled |
| `MAX_OPEN_SECONDS` | How long a wormhole may stay open at all, measured from when it first formed |

The maximum always wins. The shutdown timeout restarts on every dial, so without a cap
anything re-triggering a gate on a schedule — a minecart crossing a detector rail every few
seconds — would hold it open indefinitely and lock everyone else out.

That cap is also what made `redstone-extend-open-time` safe. A signal landing on an already
open gate used to do nothing at all, deliberately, for exactly that reason. It now pushes the
shutdown task back without touching the gate's own open timestamp — no re-dial, no
animation, no target lookup — so a signal can buy more time but not unlimited time.

**The activation timeout deactivates by gate identity, not by player.** Removing "whatever
gate is currently mapped for this player" is wrong the moment the same player activates a
second gate before the first one's timer fires: it stole the second gate's still-pending
activation out of the map and acted on that instead. The gate that really timed out kept its
chevrons lit forever, and the unrelated second gate was switched off early and lost its own
cleanup too.

## The iris

The iris is a shield, and unlike the portal it is **real blocks**. It has to stop things, and
a drawing cannot. Opening it on an active gate returns the interior to air with the portal
drawn over it, which also clears the iris blocks.

A gate can only take an iris if its shape marks `:IA`. The lever toggles it; `/wormhole gate
edit <gate> idc <code>` sets the deactivation code, and the default state is remembered.
Applying a state that is already true is silent rather than announcing an iris that did not
move.

## The portal is drawn, not built

The event horizon exists only in each nearby client's copy of the chunk. The server's blocks
stay air.

That is what lets a water gate not drown anyone and a lava gate not burn them, and it means
nothing is left standing in the world if the server stops mid-opening. It costs three things,
each handled:

- **The server has no way to ask a client what it is showing.** So what was sent is
  remembered per player, which is the only way to know what needs taking back.
- **Anything handing a client a fresh copy of a chunk erases the drawing.** Portals are
  redrawn on join, on world change, on chunk change, and after a teleport.
- **The client and server disagree about physics.** The client simulates water and floats the
  player upward; the server sees them climbing through open air and kicks them for flying.
  Nothing can make the two agree — the block genuinely is not water — so flight is allowed
  for exactly as long as the player is inside the portal, and withdrawn on the way out. Only
  from players this granted it: someone in creative keeps what they came in with.

The arrival splash — a moment of water shown to a traveller as they come out — is the same
mechanism, and deliberately brief. It is the one drawing here that makes the client's world
*less* solid than the real one, so it is only sent where the eye is in open air.

## Animation

**Chevrons light one at a time**, on `:L#n` order, over the activation sequence. A shape with
three lighting steps climbs the same distance as one with seven, in bigger steps.

**The woosh is waves.** A shape that authors `:W#n` markers says exactly what each wave is. A
shape that does not falls back to `WOOSH_DEPTH`, or a per-gate override, and wave *n* is
derived on demand as the portal face pushed *n+1* blocks along the gate's facing. Deriving it
rather than storing it at detection time keeps it out of the save file and makes
`/wormhole gate edit <gate> woosh` take effect on the very next opening.

Waves are drawn to nearby clients and undrawn by showing what is really there, so there is no
original to remember and none to get wrong.

Two bugs here are worth keeping in mind, because both were invisible until described:

- The retraction ended one wave early, every time, on every completed opening. The shallowest
  layer — the one right behind the portal — stayed lit as woosh material for as long as the
  gate stayed open. Reported as the event horizon having an extra layer in it.
- A gate can close mid-woosh, but the already-scheduled continuation still fires. Without a
  guard it found the counters that shutdown had just reset to zero, read that as a fresh
  opening, and replayed the kawoosh on a gate that had already closed.

## Sound

Sounds are stored as **names** and played through the overload that takes one, never resolved
to a `Sound` constant. The sound type has been moving toward a registry-backed one, and a
registry cannot be asked about before the server has started. A name also passes straight to
the client, so a resource pack can supply its own with no code involved, and a name the client
does not know is silent — which is what it does with an unknown sound anyway.

Everything fails quietly. A gate that cannot make a noise should still dial.

- **Chevrons pitch upward through the sequence**, spread across however many lighting steps
  the shape has, so a gate audibly works towards something. The step number comes from the
  lighting iteration the animator is already counting, so the sound cannot drift out of step
  with the lights.
- **The kawoosh is a splash, pitched down to 0.7.** It was `block.end_portal.spawn` for one
  release: one of the loudest samples the client has, and a low boom besides, which at this
  plugin's gate volume made an opening gate the loudest thing on the server and nothing like
  the water it is meant to be.
- **The open-wormhole hum is much quieter than everything else.** It is a background, not an
  event, and it repeats every few seconds.

## Travelling

The move path, in the order it is asked. Everything before the teleport can refuse, and
nothing has moved until the last step.

1. **Did the player cross a block boundary?** If not, stop.
2. **Is the destination block part of a gate?** One map lookup. If the player's own block is
   not, and they are riding something living, look under the mount instead — a camel is tall
   enough that the rider clears the portal while the camel stands in it.
3. **Is that gate open, and is this block its portal?**
4. **Does the gate hold a target?** A gate with none is either the far end of somebody else's
   wormhole or one that was lit and walked away from. The first is an exit, and walking into
   it from outside is refused, so a wormhole cannot be used as a door in both directions. The
   second has nowhere to send anybody, so its ring is just a ring.
5. **Permission**, if `wormhole-use-is-teleport` is on.
6. **Did they just arrive from this gate?** Refused, so a traveller does not bounce straight
   back. The chat line is throttled to once every two seconds per gate — the move is cancelled
   either way, only the repetition is skipped.
7. **Per-player cooldown**, if enabled. Checked here, but *applied* only once the traveller
   has actually gone. Setting it at the check spent the cooldown on a trip that had not
   happened and might still not.
8. **Can they afford the fare?** Checked here so the refusal comes in the right order, but the
   money does not move until the trip is certain.
9. **Is the far iris closed?** They are pushed back to their own arrival point.
10. **Same-world only**, if configured.
11. **Find a safe landing spot** at the far end.
12. **`StargatePlayerTravelEvent`.** Every check this plugin makes has passed and nothing has
    moved, which is the only honest point to let another plugin object.
13. **Charge the fare.**
14. **Teleport.**

**A cancelled trip stops the travel and nothing else.** Cancelling a move event returns the
player to where the move started, so what that does depends entirely on where they were.
Someone walking in is returned to the block outside, which is the intent. Someone already
standing in the portal would be returned into the portal, and so would their next move, and
every one after it — they could not walk out, and the server ends it by dropping them. So
only a player arriving from outside is physically held; one already inside is left free to
walk away, having simply not been sent anywhere.

**A rider travels with what carries them.** Whatever the player is riding — horse, camel,
boat, pig, strider — goes with them and is put back together after landing. Minecarts are the
exception: they raise `VehicleMoveEvent`, so the vehicle listener owns them, teleports them
in place with passenger state preserved, and fires `StargateMinecartTeleportEvent` with the
old cart and the new. A cart does not survive a gate; it is removed and a fresh one spawned.

While a player stands in a portal their air is refilled every move, so a water-material gate
does not drown them.

## Everything that is not a player

Two mechanisms, because two problems.

**`GateEntityScanner` sweeps for things that linger** — dropped items, wandering mobs — which
generate no event when they drift into a portal. Per tick interval it does one entity query
per *active gate*, not one per portal block: a Standard gate has 21 portal blocks, so the
naive version issued 21 spatial queries per gate and over a thousand across a server with
fifty open wormholes. Everything that does not depend on the entity is computed once per gate.

**`ProjectileGateTracker` watches projectiles individually**, because a sweep cannot see them
at all. Portal blocks are air, so an arrow crosses the ring in about a tick and carries on.
Polling every twenty ticks almost never catches it, and when it did the arrow had already
landed — which is why arrows appeared to trickle out of the destination rather than fly.

Even a per-tick position check is not enough: a drawn bow puts an arrow at roughly three
blocks per tick and a portal is one block thick, so sampling position steps clean over the
gate. So what is checked is the *path*: each tick, the segment from the previous position to
the current one is walked in half-block steps, and the crossing happens if any point on it
lies in an open portal. Cost scales with projectiles in flight, not with gates.

## Permissions

`WXPermissions` answers one question — may this player do this to this gate — through four
gates of its own, in order:

1. **An operator may do anything.** Written as a blanket allow rather than a list of the
   permission types that happen to exist today. It used to be a switch naming all ten with
   `default: return false`, so an eleventh type would have been silently denied to operators,
   and the failure would have looked like a misconfigured permissions plugin.
2. **A gate with no owner is public** for the everyday actions. Gates built before ownership
   was recorded have none, and locking everyone out of them would strand them.
3. **The owner may use and manage their own gate**, holding no node at all.
4. **Otherwise, nodes.** In simple mode — no permissions plugin — anyone may use, dial and
   travel, and build, remove and config need op, which step 1 already settled.

**Every gate-facing check is two nodes, not one:** the node for the action, and admission to
the network the gate is on. Holding `wormhole.use.dialer` is not admission to a private
network. Public admits everyone.

The full node list is in the [README](../README.md#permissions).

## Commands

`/wormhole gate <verb>` is the shape people type:

```
build <shape>            start building; then click the DHD position
complete <name> [idc=][net=]   name and register what you built
list [network]           gates you can see
remove <gate>            take it down
edit <gate> <field> <value>
regenerate <gate>|-all   redraw signs, levers and arrival points
refresh                  next DHD click re-detects the geometry
go <gate>                teleport to a gate
force <gate>             dial past the usual refusals
import                   pull gates out of a legacy database
shapes [reload|validate] list, reload or check the shape files

  edit fields:  portal | iris | light   materials
                woosh                   animation depth
                group                   palette
                redstone                true|false
                custom                  true|false
                idc                     iris deactivation code
                owner                   hand the gate over
```

Gates had fifteen top-level commands while rings had one with verbs under it; this is the
gates catching up, and a new verb now costs a line rather than another name at the top level.
Every verb hands straight off to the handler that already owned it, and the old flat names
(`/wormhole build`, `/wormhole idc`, …) stay registered as hidden entries, so nothing in a
command block or a script breaks.

## Config

The gate-facing settings in `config.yml`:

```yaml
permissions-support-disable: false
permissions-auto-fallback: true      # simple mode when no Vault provider is found
redstone-extend-open-time: true      # a signal pushes the shutdown back, within the maximum
sign-dial-match-material: true
sign-glowing-text: false
sign-color-gate-name: DARK_AQUA      # and -network, -owner, -selected, -neighbour
gate-material-groups:                # the palettes; first declared is the default
```

Timers and sounds are set with `/wormhole config <setting>`: `timeout-activate`,
`timeout-shutdown`, `max-open-seconds`, `use-cooldown-enabled`, `use-cooldown-seconds`,
`same-world-only`, `entity-scan-interval-ticks`, `gate-sounds-enabled`, `gate-sound-volume`,
and one `gate-sound-*` key per sound.

## Layout

```
model/Stargate.java                one gate: every block position, and all its state
model/StargateManager.java         the registries: gates, blocks, networks, open gates
model/GateSpatialIndex.java        gate blocks bucketed by chunk, for area queries
model/StargateNetwork.java         a name and its gates
model/StargateShape.java           one shape's materials and derived positions
model/Stargate3DShape.java         the layered form, and the file parser
model/StargateShapeLayer.java      one layer's cells and markers
model/StargateShapeRegistry.java   the loaded shapes, and known frame materials
model/MaterialGroup.java           one palette
model/MaterialGroupRegistry.java   the palettes, keyed by frame material
model/StargateDialManager.java     the sign UI and the two-sided dial handshake
model/StargateLifecycle.java       activation, shutdown, timers, iris state
model/StargateAnimator.java        chevron lighting and the woosh
model/StargateBlockSetup.java      block placement, portal drawing, arrival splash
model/GateSounds.java              what a gate sounds like
model/GateSerializer.java          GateData, save versions 3 to 9
model/StargateYamlManager.java     one file per gate
model/LegacyDatabaseImporter.java  pulling gates out of an old database
logic/StargateHelper.java          detection: matching a build against every shape
logic/ShapeFileValidator.java      checking a .shape file before it is trusted
GateEntityScanner.java             loose entities standing in an open portal
ProjectileGateTracker.java         arrows, by the path they travelled
WormholeXTremePlayerListener.java  the move path
WormholeXTremeVehicleListener.java minecarts and boats
command/handlers/GateCommand.java  /wormhole gate <verb>
events/GateEvents.java             what other plugins are told
```

## What the tests guard

Named for what breaks if they fail, not for the class they cover.

- **Detection picks the right shape** and rejects a near-miss build
  (`GateDetectionTest`, `ShapeMatchPreferenceTest`, `UnlitChevronTest`).
- **A gate round-trips through YAML** with its geometry intact, and one broken file does not
  cost the rest (`GateYamlRoundTripTest`).
- **Materials survive a Bukkit version change** — names, never ordinals
  (`GateSerializerTest`, `LegacySaveVersionTest`).
- **A wormhole runs one way.** Walking into the exit end is refused; walking out of it is not
  (`GateOneWayTest`, `GateEntryRefusalTest`).
- **A cancelled `StargatePlayerTravelEvent` stops the trip without trapping the traveller**
  (`PlayerTravelEventTest`).
- **The dial sign's saved index is resolved on load without advancing it**
  (`DialSignTargetRestoreTest`).
- **A re-dial cannot push a gate past its maximum open time** (`GateMaxOpenTimeTest`,
  `ShutdownDelayTest`).
- **The iris does not entomb anybody**, and its lever lands where the shape said
  (`IrisDoesNotEntombTest`, `IrisLeverPlacementTest`).
- **The woosh draws and undraws every wave** (`WooshWaveTest`, `StargateAnimatorTest`).
- **A portal is redrawn for a client that lost it** (`PortalVisualRefreshTest`).
- **The arrival point is outside the portal** (`ArrivalIsOutsideThePortalTest`,
  `GateArrivalPointTest`).
- **A rider leaves with their mount**, and a minecart's passenger is preserved
  (`WormholeXTremePlayerListenerMountTest`, `WormholeXTremeVehicleListenerTest`).
- **Portal flight is granted only while inside, and only taken back from those it was given
  to** (`PortalFlightExemptionTest`).
- **A projectile crossing between ticks is still caught** (`ProjectileGateTrackerTest`).
- **Gate management is behind a permission**, and network admission is checked separately
  from the action node (`WormholeCommandPermissionTest`).
