# Transport Rings — Design

Status: design, not implemented. This document records the decisions behind the ring
subsystem so they can be reviewed before any code exists.

Rings are a second way to travel, alongside stargates. They are deliberately *not* a
variant of a gate: a gate is a named, addressable, dialable structure with a DHD, an iris,
a sign, redstone hooks and a network. A ring has none of that. It is an unnamed,
invisible, permanently-paired pad in a floor or ceiling that fires when someone stands in
it.

| | Stargate | Ring |
|---|---|---|
| Addressing | Dial any gate by name | Fixed pair, no addressing |
| Orientation | Vertical (mostly) | Horizontal — floor or ceiling |
| Activation | Button, sign, redstone, `/dial` | Walk into it |
| Direction | One way per dial | Both ends fire together |
| Appearance | Permanent structure | Invisible until it fires |
| Identity | Player-chosen name | Generated id, optional label |

## Patterns

There are exactly two, and they are hardcoded. No `.shape` file format — a file format for
two fixed arrays is dead weight.

Both are taken from the Standard gate's ring outline, which cuts its corners at two levels
rather than one. The odd pattern is that outline exactly (`Standard.shape`, Layer#1); the
even pattern is the same construction one block wider in each axis.

Odd — 7 across, disc profile `3,5,7,7,7,5,3`:

```
. . # # # . .
. # · · · # .
# · · · · · #
# · · · · · #
# · · · · · #
. # · · · # .
. . # # # . .
```

16 perimeter blocks, 21 interior. Has a true centre block.

Even — 8 across, disc profile `4,6,8,8,8,8,6,4`:

```
. . # # # # . .
. # · · · · # .
# · · · · · · #
# · · · · · · #
# · · · · · · #
# · · · · · · #
. # · · · · # .
. . # # # # . .
```

20 perimeter blocks, 32 interior. Centre is a 2x2.

`#` is the perimeter: what the player lays in slabs, and what animates. `·` is the
interior: the trigger volume and the region that travels. The two never overlap, which
matters — a block cannot be both a thing that animates and a thing that holds a passenger.

The player lays **only the perimeter**. The interior is left entirely alone — it is a ring
of slabs, not a disc, and whatever is already inside it (floor, carpet, a rail) stays.

Offsets are stored as integer `(dx, dz)` pairs from an anchor block. For the odd pattern
the anchor is the centre. For the even pattern there is no centre, so the anchor is the
low-x/low-z block of the central 2x2, giving offsets in `-3..+4` rather than `-3..+3`.

These are data. Changing the sizes later, or adding a third pattern, is a table edit.

## Construction

1. Player lays the perimeter in slabs on top of the floor (or under the ceiling).
2. Player stands in the ring and runs `/wormhole ring create`.
3. The plugin tests both patterns at the player's position and takes the one that matches.
4. The slabs are consumed and the surface returns to exactly what it was. The ring is
   invisible from this point on.

The slabs are a template, not structure. Nothing is left behind and nothing is placed — the
footprint reads as ordinary floor.

Creation is two-step, because a ring is meaningless without its partner. The first `create`
stashes a pending endpoint keyed by the player; the second, at the far site, completes the
pair. This mirrors `incompleteStargates` / `completeStargate` in `StargateManager`. The
pending endpoint is persisted as `pending-<uuid>.yml` so a restart mid-build does not
silently eat the player's slabs.

Orientation is inferred, not asked for: slabs resting on a floor make a `FLOOR` ring, slabs
hung under a ceiling make a `CEILING` ring. A ceiling ring flips two things — the direction
the slabs travel, and where the trigger volume sits (below the ring rather than above it).
Arrival at a ceiling ring is the floor beneath it, not the ring plane.

## Storage

**The pair is the stored object, not the ring.** One file per pair, both endpoints inside
it:

```
plugins/WormholeXTreme/WormholeXTremeDB/rings/7f3a1c2e.yml
```

```yaml
Id: 7f3a1c2e
Owner: 069a79f4-44e9-4726-a5be-fca90e38aaf5
OwnerName: Justin
Label: ""
Created: 1756771200000
A:
  World: world
  X: 128
  Y: 64
  Z: -310
  Orientation: FLOOR
  Pattern: ODD
  Material: STONE_SLAB
B:
  World: world
  X: 512
  Y: 31
  Z: 88
  Orientation: CEILING
  Pattern: ODD
  Material: STONE_SLAB
```

Storing the pair rather than two rings removes three problems at once: there are no
dangling partner references, no second resolution pass on load (unlike gate networks), and
no orphan ring that exists but goes nowhere. Deleting the file removes both ends.

Only the anchor, pattern and orientation are stored. The footprint is derived — storing 16
or 20 block coordinates that are a pure function of three fields would just be something
else to keep in sync.

**Rings are not named.** The id is short random hex, used for the filename, for
`/wormhole ring remove <id>`, and in log lines. Nothing addresses a ring by name at
runtime, because point-to-point pairing means there is nothing to address. `Label` is
optional and exists only so `/wormhole ring list` can read `Base <-> Deep Mine` instead of
two hex strings.

Format is plain YAML from the start. `GateSerializer` carries nine versions of legacy
binary baggage; there is no reason to inherit that.

## The cycle

```
IDLE        a move event inside either interior arms the pair
COUNTDOWN   glowstone phase — ABORTS if both interiors go empty
DEPLOY      slabs travel — COMMITTED, no abort, runs to completion
FLASH       snapshot both interiors in one tick, swap
RETRACT     slabs travel back, every block restored
COOLDOWN    pair refuses all triggers
```

Abort is confined to the countdown. Once the rings start deploying the cycle runs to the
end regardless of who leaves.

This is what keeps the animation tractable. The abortable phase is a single flat set of
block replacements with one restore map, and the phase with all the moving parts cannot be
interrupted. Restore-on-abort and restore-on-retract are the same code path, exercised in
one direction only.

An empty committed cycle is legal and expected: everyone left during the countdown but too
late to abort, so the rings deploy, flash, send nothing, and retract.

### Countdown length is a constraint, not a preference

The interior is 3x3 or 4x4, so clearing it from the centre is roughly 1.5–2.5 blocks — well
under a second at walking speed. The abort window is only real because the countdown
comfortably exceeds that.

`rings.countdown` defaults to 60 ticks. Below roughly 20 the abort window stops being
meaningful and rings begin taking people who were only walking past. Treat that as a
documented floor rather than a free config value.

## Trigger and re-arm

The whole move path is:

1. A move crosses a block boundary; the gate lookup fails.
2. `RingIndex` hash lookup on the chunk key — is this block in a ring interior?
3. Pair is `IDLE` and off cooldown? Start the countdown.

The ring check sits *after* the gate check in `handlePlayerMoveEvent` so gates keep
priority, and ring creation refuses any footprint touching gate blocks
(`StargateManager.isBlockInGate`), so the two can never contend for the same block.

Re-arm is purely move-driven. There is no scheduled occupancy re-check and no polling: a
player who is teleported and then stands perfectly still does not restart the cycle. A
player who is moving does, once the cooldown has passed, and bounces back — which is the
intent. Nothing in the ring subsystem runs unattended.

**No per-player arrival guard is needed.** Gates need `isPlayerRecentArrivalFrom` because
they would re-trigger within milliseconds of arrival. Rings do not: the cooldown is shared
across both ends of the pair, so the settle-move on landing is refused, and re-firing
requires the player to still be there and moving a full cooldown later.

A player who lands, stands still for ten minutes and then walks off does not get taken
back: their first move arms the countdown, but they clear the interior long before it
elapses and the cycle aborts. Someone who moves *within* the interior and stays there does
travel, which is correct.

## Teleport semantics

Both interiors are snapshotted **in the same tick, before anything moves**. Doing it any
other way lets A's arrivals leak into B's set and bounce straight back.

The snapshot happens at the flash, not at deploy-start. The rings are a volume that closes
on whatever is inside it when it closes: walking out genuinely saves you, walking in late
genuinely catches you.

Everything in the interior travels — players, mobs, dropped items, vehicles. One
`getNearbyEntities` call per side at the moment of the flash. Rings need no equivalent of
`GateEntityScanner`'s per-tick sweep, because there is exactly one instant at which
occupancy matters.

### Standing on the ring rather than in it

You have to be *within* the ring to travel, and the perimeter is not within it. But someone
standing on a perimeter block is in the worst possible spot: that column is about to fill
with rising slabs, so leaving them there means they are shoved, suffocated, or simply left
behind for a reason invisible to them.

So at deploy-start — the moment the perimeter starts being written, not at the flash —
every entity standing on a perimeter block is nudged one block inward, toward the anchor,
to the nearest free interior block. They clear the animation and they travel with everyone
else. If no interior block is free they are left where they are and the animation skips
their column, on the same not-a-block-we-own principle that governs every other write.

Arming stays interior-only: stepping on the ring's edge does not start a countdown, because
the perimeter is a threshold you cross rather than a place you stand. The nudge only
applies to a cycle already underway.

## Animation

Half-block resolution comes from slab type rather than position. A `BOTTOM` slab fills the
lower half of its block and a `TOP` slab the upper half, so a travelling ring steps
`(y,BOTTOM)` then `(y,TOP)` then `(y+1,BOTTOM)`. "A block of space between them" means
concurrent rings sit three half-steps apart.

Each frame restores the previous positions before placing the next, using the same
save-original/restore bookkeeping the woosh already uses
(`StargateManager.getOpeningAnimationOriginalMaterials()`). Any position that is not air is
skipped rather than overwritten — the animation must never eat a player's build, and must
never restore a block someone changed underneath it.

Ceiling rings run the same sequence with the travel direction inverted.

## Limits

Three knobs solving three different problems. The count is the least important of them.

**Footprint overlap is the real hazard, not density.** Dozens of rings in a small area cost
nothing to look up — the index is chunk-bucketed, so it is one hash hit per block crossing
regardless. What breaks is two footprints touching: a player between them is inside two
trigger volumes, and two animations write the same blocks and restore each other's
originals. So overlap of footprint *or* interior is refused outright at create, with
`rings.min-separation` (default 8, centre to centre) on top for breathing room.

**Distance is not a cost; unloaded chunks are.** A 20,000-block teleport costs the same as a
20-block one. The actual failure is the far end sitting in an unloaded chunk when the cycle
fires — the animation writes blocks into an unloaded chunk and the arrival lands in
ungenerated terrain. So the partner's chunks are force-loaded for the duration of the
transit (`Chunk.addPluginChunkTicket`, released on retract) and the swap is refused
outright if the partner's world is not loaded. With that handled,
`rings.max-link-distance` can default to `0` (unlimited).

**Quota** is `rings.max-pairs-per-player`, bypassed by `wormhole.ring.unlimited`.

Cross-world pairing honours the existing `ConfigManager.isSameWorldOnly()`, so rings and
gates behave the same way.

## Config

```yaml
rings:
  countdown: 60              # ticks; see the floor documented above
  cycle-cooldown: 1200       # ticks, per pair
  deploy-ticks: 2            # ticks between animation frames
  max-pairs-per-player: 10
  min-separation: 8          # blocks, centre to centre
  max-link-distance: 0       # 0 = unlimited
  default-material: STONE_SLAB
```

## Commands and permissions

Registered in the `SubCommands` table, which drives dispatch, tab completion and help from
one declaration.

```
/wormhole ring create              build the pad you are standing in; twice to pair
/wormhole ring cancel              discard a pending first endpoint
/wormhole ring list                your pairs, by label where set
/wormhole ring remove <id>         remove both ends
/wormhole ring label <id> <text>   optional, display only
/wormhole ring material <id> <m>   what the ring becomes when it rises
```

```
wormhole.ring.build       create and pair rings          default: op
wormhole.ring.use         travel by ring                 default: true
wormhole.ring.remove      remove your own pairs          default: true
wormhole.ring.remove.all  remove anyone's pairs          default: op
wormhole.ring.unlimited   bypass the per-player quota    default: op
```

## Proposed layout

```
model/ring/Ring.java             one endpoint: world, anchor, orientation, pattern, material
model/ring/RingPair.java         the persisted unit: id, owner, label, two endpoints, state
model/ring/RingPattern.java      the two offset tables, perimeter and interior
model/ring/RingManager.java      registries by id and by block, pending endpoints
model/ring/RingIndex.java        chunk-bucketed interior lookup for the move path
model/ring/RingAnimator.java     glowstone phase, slab travel, restore
model/ring/RingTransit.java      per-pair state machine, cooldown, the atomic swap
model/ring/RingYamlManager.java  load and save pair files
command/handlers/RingCommand.java
events/RingTravelEvent.java
```

## What is reused, and what is not

Reused:

- The chunk-bucketing approach of `GateSpatialIndex` — ring detection is on the move path
  and must be a hash lookup, not a scan.
- `StargateAnimator`'s save-original/restore discipline for animated blocks.
- `MaterialGroup` and the `gate-material-groups` config, rather than a parallel palette.
- The `SubCommands` registry, `WXPermissions`, the `GateEvents` fire pattern,
  `findSafePlayerLocation`, and `StargateYamlManager` as a persistence template.

Not reused:

- `Stargate` itself. 2,127 lines of DHD, sign, iris, redstone, network and woosh state,
  approximately none of which a ring has.
- `GateSerializer`, for the legacy-versions reason above.
- The `.shape` format, for the two-fixed-patterns reason above.
- `GateEntityScanner`'s per-tick sweep, replaced by one call at the flash.

## Test priorities

In rough order of how much they would hurt to get wrong:

1. The swap is atomic — entities from A never appear in B's snapshot.
2. Abort during countdown restores every block at both ends.
3. Deploy cannot be aborted, and an empty committed cycle completes cleanly.
4. Cooldown is shared per pair, and the landing settle-move does not re-fire it.
5. Overlapping footprints are refused at create, including against gate blocks.
6. Pattern matching picks the right one of the two, and rejects a near-miss circle.
7. A pair round-trips through YAML with its footprint correctly re-derived.
8. An entity on a perimeter block is nudged inward at deploy-start and travels, and is
   left alone when the interior has no room for it.
