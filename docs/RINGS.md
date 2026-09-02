# Transport Rings — Design

Status: built on `feature/rings` and ready to try in game. This document records the
decisions, and stays the place they are argued about.

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
| Range | Cross-world, config permitting | Same world, always |

## Patterns

There are exactly two, and they are hardcoded. No `.shape` file format — a file format for
two fixed arrays is dead weight.

Both are rasterised the same way the Standard gate's ring is (`Standard.shape`, Layer#1) —
cells whose centre falls inside the circle — just at a smaller diameter. Standard is 7
across and has room to cut its corners at two levels, profile `3,5,7,7,7,5,3`. At 5 and 6
there is only room for one cut, which is not a simplification of the construction but the
same construction with fewer rows.

Odd — 5 across, disc profile `3,5,5,5,3`:

```
. # # # .
# · · · #
# · · · #
# · · · #
. # # # .
```

12 perimeter blocks, 9 interior (3x3). Has a true centre block.

Even — 6 across, disc profile `4,6,6,6,6,4`:

```
. # # # # .
# · · · · #
# · · · · #
# · · · · #
# · · · · #
. # # # # .
```

16 perimeter blocks, 16 interior (4x4). Centre is a 2x2.

`#` is the perimeter: what the player lays in slabs, and what animates. `·` is the
interior: the trigger volume and the region that travels. The two never overlap, which
matters — a block cannot be both a thing that animates and a thing that holds a passenger.

The player lays **only the perimeter**. The interior is left entirely alone — it is a ring
of slabs, not a disc, and whatever is already inside it (floor, carpet, a rail) stays.

Offsets are stored as integer `(dx, dz)` pairs from an anchor block. For the odd pattern
the anchor is the centre, giving offsets in `-2..+2`. For the even pattern there is no
centre, so the anchor is the low-x/low-z block of the central 2x2, giving the asymmetric
range `-2..+3`.

```
ODD   perimeter  dz=-2: dx -1,0,1   dz=-1..1: dx -2,2      dz=+2: dx -1,0,1
      interior   dz=-1..1, dx=-1..1

EVEN  perimeter  dz=-2: dx -1..2    dz=-1..2: dx -2,3      dz=+3: dx -1..2
      interior   dz=-1..2, dx=-1..2
```

These are data. Changing the sizes later, or adding a third pattern, is a table edit.

## Construction

1. Player lays the perimeter in slabs on top of the floor (or under the ceiling).
2. Player stands anywhere inside it and runs `/wormhole ring create`.
3. The plugin reads the template.
4. The slabs are consumed and the surface returns to exactly what it was. The ring is
   invisible from this point on.

The slabs are a template, not structure. Nothing is left behind and nothing is placed — the
footprint reads as ordinary floor.

### The template says more than its shape

Detection reads four things out of it, and only the first is obvious:

- **Which pattern**, by matching one of the two.
- **Where the anchor is.** Nobody stands exactly on the centre block, so the player's
  position is not the anchor — every interior square is tried as a candidate place for them
  to be standing, and the anchor is worked back from that.
- **What the ring is made of.** The slab they chose becomes the ring's material, so a ring
  laid in deepslate rises in deepslate with no command run. This is the same idea as a gate
  taking its palette from the material its frame is actually built from, and it is what
  makes per-end materials the default rather than something you have to go and set.
- **Which surface it is set into.** A slab resting on a floor is a bottom slab; one hung
  under a ceiling is a top slab. So orientation is a fact stated by the template rather than
  a guess from what happens to be above or below it.

A floor ring's slabs sit in the player's own block layer, but a ceiling ring's hang some way
above their head, so the search runs upward from the player's feet as far as `rings.reach`.

Four things are refused, each with its own message, because "no ring found" sent to someone
looking at a ring they can plainly see would send them hunting the wrong problem: no circle
at all, a circle of mixed slab types, a circle whose slabs do not all face the same way, and
a circle that has been filled in. Only the ring's own slab counts as filling it — a carpet
or a rail inside the circle is nobody's business.

Detection is pure and reaches the world through a two-method probe, so all of it is
testable without a running server.

Creation is two-step, because a ring is meaningless without its partner. The first `create`
stashes a pending endpoint keyed by the player; the second, at the far site, completes the
pair. This mirrors `incompleteStargates` / `completeStargate` in `StargateManager`. The
pending endpoint is persisted in `rings/pending.yml`, keyed by player, so a restart
mid-build does not silently eat the player's slabs. It sits outside the world files because
it is not yet a pair, and it records its own world so the second `create` can refuse an
endpoint in a different one — with a message saying so, rather than a silent failure after
the player has already laid sixteen slabs.

Orientation, once read, flips two things rather than one: the direction the slabs travel,
and where the trigger volume sits — below a ceiling ring rather than above it. Arrival at a
ceiling ring is the floor beneath it, not the ring plane.

## Storage

**Rings do not cross worlds.** Both ends of a pair are always in the same world, by design
rather than by config. Gates remain the long-haul option; rings are local transport. That
matches the fiction, and it removes a whole class of problem — no pair can be half-loaded,
no endpoint can reference a world that no longer exists, and `World` is a property of the
pair rather than of each end.

**The pair is the stored object, not the ring**, and every pair in a world lives in that
world's single file:

```
plugins/WormholeXTreme/WormholeXTremeDB/rings/<world>.yml
```

```yaml
World: world
Pairs:
  7f3a1c2e:
    Owner: 069a79f4-44e9-4726-a5be-fca90e38aaf5
    OwnerName: Justin
    Label: ""
    Created: 1756771200000
    Access: PRIVATE
    Allowed: [11111111-2222-3333-4444-555555555555]
    Style: CONCURRENT
    A: {X: 128, Y: 64, Z: -310, Orientation: FLOOR, Pattern: ODD, Ring: STONE_SLAB, Light: GLOWSTONE}
    B: {X: 512, Y: 31, Z: 88, Orientation: CEILING, Pattern: ODD, Ring: STONE_SLAB, Light: GLOWSTONE}
```

The `World` field inside the file is authoritative, not the filename. World names can
contain characters a filesystem will not take, so the name is sanitised on the way out and
never parsed back on the way in.

Storing the pair rather than two rings removes three problems at once: there are no
dangling partner references, no second resolution pass on load (unlike gate networks), and
no orphan ring that exists but goes nowhere. Deleting the file removes both ends.

One file per world, rather than one per pair, for three reasons that point the same way.

It makes the storage layout enforce the design rule: a pair cannot span worlds because
there is nowhere to write one that does.

It answers the only real cost in loading. Startup reads go from one per pair to one per
world, and reading files is what dominates — the indexing itself is tens of thousands of
map insertions, which is microseconds. A thousand pairs across three worlds is three reads.

And it makes world lifecycle trivial. A world that is not loaded has its file skipped, so
its rings do not exist this session rather than sitting in an index nothing can reach. A
world that is deleted takes its rings with one file.

The cost of a shared file is that a bad write loses a world's rings rather than one pair's,
and that is handled the way gates already handle it: dump to a temp file and `ATOMIC_MOVE`
it into place, so a partial write is never visible. Loading tolerates damage per entry — a
pair that will not parse is logged and skipped, and the rest of the world still loads.
Rewriting the whole file on every change is not a concern because ring writes are rare and
player-initiated: create, remove, relabel, recolour. Nothing writes on the travel path.

Only the anchor, pattern and orientation are stored. The footprint is derived — storing 12
or 16 block coordinates that are a pure function of three fields would just be something
else to keep in sync.

### Why pairs are keyed by id and not by coordinates

Keying pairs on their coordinates would buy nothing, because **nothing looks a ring up by
key at runtime.** Every world file is read once at startup into `RingIndex`, and from then
on the move path is a hash against an in-memory chunk bucket. There is no disk access on the
hot path to make faster, and an admin standing in a ring is answered from that same index.

It would also cost something. A pair has two endpoints, so a coordinate key either encodes
both and becomes unreadable, or encodes one and quietly implies a ring is the unit of
storage after we decided it is not. Identity in commands and log lines stops being stable
the moment anything about the ring's position changes.

If the real want is seeing what is there, a world file already lists every pair in one
place, and `/wormhole ring list` reads it back with labels. If a location index ever
genuinely becomes necessary it belongs in memory, where one already is.

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
COUNTDOWN   lights show in the ring's pattern — ABORTS if both interiors go empty
DEPLOY      rings rise one at a time — COMMITTED, no abort, runs to completion
FLASH       snapshot both interiors in one tick, swap
HOLD        rings stand stacked for a beat, travellers already gone
RETRACT     rings return nearest-first, every block restored
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

The interior is 3x3 or 4x4, so getting clear of it — across the interior and over the
perimeter — is roughly 2 to 3 blocks, well under a second at walking speed. The abort
window is only real because the countdown comfortably exceeds that.

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

## Materials

A ring has two, and they do different jobs:

| | Default | Constraint | Shown |
|---|---|---|---|
| Ring | the slab it was laid in | **Must be a slab** | The travelling rings, during deploy and retract |
| Light | `rings.default-light-material` | Any placeable block | The perimeter during the countdown |

The ring material is not really a default at all: it is read off the template, so the config
value only applies if detection ever cannot say. The light has nothing to read, so it takes
the configured default and is changed with `edit` if wanted.

The ring material is constrained and the light one is not. The rise is built out of slab
halves — a bottom slab fills the lower half of its block, a top slab the upper half — and
that is the only way to move half a block per frame. A full block would silently cost the
animation its resolution, which is the entire visual effect, so a non-slab is refused at the
command rather than accepted and quietly disappointing.

The light is just a block that appears in the ring's own pattern and goes away again, so it
has no such requirement.

**Both are stored per end, and the two ends are meant to differ.** A ring in a stone base
and its partner in a deepslate mine should each look like where they are, so the material
belongs to the end rather than to the link. Nothing about a pair requires its two halves to
match.

That is what shapes the `edit` command below: standing in a ring edits that ring, and
naming a pair by id edits both. Proximity means precision.

**These are not `MaterialGroup` palettes**, and an earlier draft of this document was wrong
to say they would be. A gate's palette is identified by the material its frame is actually
built from, which works because a gate's frame is permanent. A ring is invisible when idle
and has no frame to read, so there is nothing to identify a group by. Two plain config
defaults plus per-ring overrides is the whole of it.

## Access

A pair is `PRIVATE` or `PUBLIC`, plus a list of players named by the owner. Private means
the owner and whoever they have named; public means anyone.

**Access belongs to the pair, not to an end**, and that is not filing convenience. Both ends
fire together and everything in both interiors swaps in the same instant, so there is no way
to authorise half of it — a pair whose ends disagreed would be one you could leave by and
not return to, which is not a setting anybody wants and not a state the swap can express.
This is exactly the opposite of materials, and for a reason worth keeping straight: a
material is cosmetic and local, so each end can look like the room it is in. Access is
functional and about the link.

`mayUse` governs two things, not one: **arming a cycle and being carried by one**. A private
ring is not a free ride for whoever happens to be standing in it when the owner uses it.
Everyone in the volume is checked at the moment of the swap, and anyone not allowed simply
stays put while the rings close and open around them.

Only players are subject to any of this. Mobs, items and vehicles travel as cargo, which
costs nothing to allow, because they cannot arm a ring in the first place — the trigger is a
player move, so a private pair only ever fires because somebody permitted made it fire.

**Private is the default**, which is the opposite of how gates behave and fits what rings
are: a personal point-to-point link somebody built between two places they own, rather than
public network infrastructure. `rings.default-access` flips it for a server that runs
rings as shared transport.

Access **fails closed** everywhere it can fail. A stored pair with no access field — which
is what every pair written before this existed looks like — loads as private, and so does
one whose access field is unreadable. Reading either as public would silently publish
somebody's private link on upgrade, and that is the one mistake here that cannot be undone
once people have started using it.

Revoking the owner does nothing, because their access comes from ownership rather than from
the list. There is no sequence of commands that leaves a pair nobody can use or change.

## Animation

Five rings end up **half a block of clear air apart** — one block centre to centre, since a
slab is half a block thick — with the lowest hanging half a block clear of the floor rather
than resting on it. Top to bottom that is five blocks of headroom.

Each ring **overshoots its place by half a block, hangs there a frame, and drops back onto
it**: the small settle a heavy thing makes when it arrives. That is the only thing in the
whole animation that ever goes above the finished stack, so it costs half a block of
headroom and nothing else — five and a half in total.

There are **two ways they get there**, both of which the show uses. `rings.default-style` picks the default and
`/wormhole ring edit style` changes one pair.

- **Concurrent** — all five on their way at once, the next leaving the plane once the one
  in front has risen a block, so the stack rises as a group and settles together. Quicker,
  and the commoner look.
- **Sequential** — strictly one at a time. The first out flies all the way to the furthest
  position and settles; only then does the next emerge.

They differ *only* in when a ring leaves the plane. Where each ends up, how far it travels
and how they come home are identical, so this is one number rather than two animations.

Style belongs to the pair, not to an end — the same reasoning as access. Both stacks have to
be up for the swap, so ends with different timings would leave one standing and waiting on
the other.

They then **stand still** for a couple of seconds with the travellers already gone. That
pause is most of what makes the effect read as a transport rather than as blocks moving, and
it is why `HOLD` is a phase rather than the swap being followed straight by the retract.

Retract, being the reversal, lifts the stack that same half block before bringing it down —
the settle read backwards, which looks like the rings unlatching before they go. The
**nearest ring goes home first** and the one that flew highest is the last to leave. This needs no code of its own: retract is deploy played backwards, and a sequence
that went out furthest-first returns nearest-first on its own. Writing it as a reversal
rather than a second sequence also means the two can never disagree and strand a slab.

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
transit (`Chunk.addPluginChunkTicket`, released on retract). Same-world pairing keeps this
to one case: there is no such thing as a pair whose far end is in a world that is not
loaded, because if the world is unloaded neither end exists and nobody is standing in one
to trigger it. With that handled, `rings.max-link-distance` can default to `0`
(unlimited within the world).

**Quota** is `rings.max-pairs-per-player`, bypassed by `wormhole.ring.unlimited`.

`ConfigManager.isSameWorldOnly()` does not apply to rings — they are same-world
unconditionally, whatever that setting says about gates.

## Config

```yaml
rings:
  countdown: 60              # ticks; see the floor documented above
  cycle-cooldown: 1200       # ticks, per pair
  deploy-ticks: 2            # ticks between animation frames
  hold-ticks: 40             # how long the stack stands before retracting
  max-pairs-per-player: 10
  min-separation: 8          # blocks, centre to centre
  max-link-distance: 0       # 0 = unlimited; distance itself costs nothing
  default-ring-material: STONE_SLAB   # fallback only; normally read from the template
  default-light-material: GLOWSTONE
  default-access: PRIVATE    # what a newly built pair starts as
  default-style: CONCURRENT  # or SEQUENTIAL; how the stack comes out
  reach: 4                   # block layers of passenger volume, from the ring plane
```

## Commands and permissions

Registered in the `SubCommands` table, which drives dispatch, tab completion and help from
one declaration.

```
/wormhole ring create                     build the pad you are standing in; twice to pair
/wormhole ring cancel                     discard a pending first endpoint
/wormhole ring list                       your pairs, by label where set
/wormhole ring remove [id]                remove both ends
/wormhole ring edit <field> <value>       edit the ring you are standing in
/wormhole ring edit <id> <field> <value>  edit both ends of that pair
/wormhole ring allow <player> [id]        let somebody use it
/wormhole ring deny <player> [id]         stop them
/wormhole ring owner <player> [id]        hand the pair to somebody else

  fields:  ring <material>    the travelling slabs; must be a slab   per end
           light <material>   the countdown lights                   per end
           label <text>       display only                           per pair
           access public|private                                     per pair
           style concurrent|sequential                                per pair
```

Everything adjustable lives under one `edit` verb rather than a subcommand per field. Gates
grew a separate top-level command for each — `portalmaterial`, `irismaterial`,
`lightmaterial`, `wooshdepth` — which is four registry entries, four usage strings and four
completers saying the same thing four ways. `edit` takes the field as an argument and stays
one entry however many fields rings end up with.

**Whether an id is given is what selects the scope**, and it reads the way people work.
You are usually standing in the ring you want to change, having just walked to it to look
at it, so the id is omitted and only that end changes. Naming a pair by id means you are
somewhere else and thinking about the pair as a whole, so both ends change. `label` is a
property of the pair and ignores the distinction; standing in either end sets it.

Working out which ring you are standing in costs nothing — it is the same `RingIndex`
lookup the move path makes.

```
wormhole.ring.build       create and pair rings                  default: op
wormhole.ring.use         travel by a ring you are allowed on    default: true
wormhole.ring.admin       use and manage any pair                default: op
wormhole.ring.unlimited   bypass the per-player quota            default: op
```

Ring permissions are checked by `RingPermissions`, not by `WXPermissions`. The gate class is
built around gates — its checks take a `Stargate`, consult its network, and fall through
owner and network rules that mean nothing here. Rings have no networks, dialers or signs, so
running them through it would mean adding cases that ignore most of their own arguments.
Four nodes and an operator bypass is the whole requirement.

Being named on a private pair's allow list lets somebody **travel** by it. It does not let
them recolour, rename, give away or delete it — managing a pair stays with its owner and
with staff.

### Handing a pair to somebody else

`/wormhole ring owner <player> [id]` transfers ownership, written for staff building rings
for a player but equally a gift between players.

The quota is checked **against the recipient**, because a transfer that skipped it would let
anyone past their limit by having a friend build the ring and hand it over.

The previous owner is not quietly kept on the allow list. Staff who built a ring for
somebody should not be left with standing access to it, and a player who gave one away and
wants to keep using it can be added back by its new owner — whose call that is, not ours. On
a private pair the command says so plainly rather than letting them discover it by walking
into a ring that no longer works for them.

The allow list itself belongs to the pair rather than to its owner, so people who were
already using a ring do not lose access because it changed hands.

## Layout

```
model/ring/RingPattern.java        the two offset tables, generated from row widths
model/ring/Ring.java               one endpoint: anchor, orientation, pattern, materials
model/ring/RingOrientation.java    floor or ceiling, and what that flips
model/ring/RingPair.java           the persisted unit: id, owner, access, style, two ends
model/ring/RingAccess.java         public or private
model/ring/RingStyle.java          concurrent or sequential deploy
model/ring/RingPhase.java          where a pair is in its cycle
model/ring/RingTemplate.java       reading a ring out of laid slabs
model/ring/RingManager.java        registry, pending endpoints, placement rules
model/ring/RingIndex.java          block lookup for the move path
model/ring/RingAnimator.java       where every travelling ring is on every frame
model/ring/RingCycle.java          one run: phases, the swap, block restore
model/ring/RingPassenger.java      what the swap needs to know about a traveller
model/ring/RingPermissions.java    the four nodes
model/ring/RingTransit.java        driving a cycle on the server clock
model/ring/RingYamlManager.java    load and save world files
model/ring/BukkitRingWorld.java    the one point of contact with a real world
model/ring/BukkitRingPassenger.java
command/handlers/RingCommand.java
```

The split that matters is the last few. Everything above `RingTransit` is pure or reaches
the world through a two-method interface, so the ordering of the swap, the frame arithmetic,
the restore bookkeeping and the placement rules are all testable with no server running.
`RingTransit` and `BukkitRingWorld` are what is left once that is taken out, and they are
deliberately dull — scheduling and block placement with no decisions in them.

## What is reused, and what is not

Reused:

- The chunk-bucketing approach of `GateSpatialIndex` — ring detection is on the move path
  and must be a hash lookup, not a scan.
- `StargateAnimator`'s save-original/restore discipline for animated blocks.
- The `SubCommands` registry, `WXPermissions`, the `GateEvents` fire pattern,
  `findSafePlayerLocation`, and `StargateYamlManager` as a persistence template.

Not reused:

- `Stargate` itself. 2,127 lines of DHD, sign, iris, redstone, network and woosh state,
  approximately none of which a ring has.
- `WXPermissions`, for the reason given under Access.
- `GateSerializer`, for the legacy-versions reason above.
- The `.shape` format, for the two-fixed-patterns reason above.
- `GateEntityScanner`'s per-tick sweep, replaced by one call at the flash.

## Test priorities

In rough order of how much they would hurt to get wrong:

1. The swap is atomic — entities from A never appear in B's snapshot.
2. Abort during countdown restores every block at both ends.
3. Deploy cannot be aborted, and an empty committed cycle completes cleanly.
4. Both styles build the same stack, run to their own length, and return nearest-first.
5. A packed block position survives the round trip at every height in the world, including
   the negative ones — the restore path unpacks these to decide which block to put back.
6. A ring overshoots its place by exactly half a block and drops back, and nothing else in
   the animation ever goes higher than the settled stack.
7. Cooldown is shared per pair, and the landing settle-move does not re-fire it.
8. Overlapping footprints are refused at create, including against gate blocks.
9. Pattern matching picks the right one of the two, and rejects a near-miss circle.
10. A pair round-trips through YAML with its footprint correctly re-derived, and lands in
   the file for its world.
11. A damaged entry in a world file is skipped with a log line, and the rest still loads.
12. An entity on a perimeter block is nudged inward at deploy-start and travels, and is
   left alone when the interior has no room for it.
13. Pairing refuses a second endpoint placed in a different world, and says why.
14. `edit` without an id changes only the end the player is standing in; with an id it
    changes both, and a non-slab ring material is refused either way.
15. A private pair refuses a stranger, carries the owner and the people they named, and
    leaves an unpermitted player standing while everyone else goes.
16. A stored pair with a missing or unreadable access field loads private, never public.
