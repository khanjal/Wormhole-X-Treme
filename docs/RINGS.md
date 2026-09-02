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
| Identity | Player-chosen name | Generated id, each end optionally named |
| Range | Cross-world, config permitting | Same world, always |

## Patterns

There are exactly two, and they are hardcoded. No `.shape` file format — a file format for
two fixed arrays is dead weight.

The odd pattern **is the Standard gate's ring** — the same profile, `3,5,7,7,7,5,3`, lying
flat instead of standing up. The even one is a size down from it, for rooms that cannot
spare seven blocks in both directions.

What makes them read as circles rather than as squares with clipped corners is that each
corner turns through **two diagonal steps** rather than one. That needs a diameter of at
least six: at five, two steps collapses the shape into a diamond with almost no standing
room, and the only usable five-wide ring has a single-step corner that looks like an
octagon. So the two sizes on offer are the small round ring and the gate's own.

Odd — 7 across, disc profile `3,5,7,7,7,5,3`:

```
. . # # # . .
. # · · · # .
# · · · · · #
# · · + · · #
# · · · · · #
. # · · · # .
. . # # # . .
```

16 perimeter blocks, 21 interior. Has a true centre block.

Even — 6 across, disc profile `2,4,6,6,4,2`:

```
. . # # . .
. # · · # .
# · + · · #
# · · · · #
. # · · # .
. . # # . .
```

12 perimeter blocks, 12 interior. Centre is a 2x2, and `+` marks the corner of it the ring
is anchored to.

`#` is the perimeter: what the player lays in slabs, and what animates. `·` is the
interior: the trigger volume and the region that travels. The two never overlap, which
matters — a block cannot be both a thing that animates and a thing that holds a passenger.

The player lays **only the perimeter**. The interior is left entirely alone — it is a ring
of slabs, not a disc, and whatever is already inside it (floor, carpet, a rail) stays.

Offsets are stored as integer `(dx, dz)` pairs from an anchor block. For the odd pattern the
anchor is the centre, giving offsets in `-3..+3`. For the even pattern there is no centre, so
the anchor is the low-x/low-z block of the central 2x2, giving the asymmetric range `-2..+3`.

Neither table is written out. Each pattern is described by its row widths alone, and which
cells are perimeter and which are interior is derived: a cell is on the outline when any of
its four orthogonal neighbours is not part of the disc, which is simply what "outline" means.
So changing the sizes later, or adding a third pattern, is a one-line edit to a profile.

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
the player has already laid twenty slabs.

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
    Created: 1756771200000
    Access: PRIVATE
    Allowed: [11111111-2222-3333-4444-555555555555]
    A: {X: 128, Y: 64, Z: -310, Orientation: FLOOR, Pattern: ODD, Ring: STONE_SLAB, Light: GLOWSTONE, Style: CONCURRENT, Name: Base}
    B: {X: 512, Y: 31, Z: 88, Orientation: CEILING, Pattern: EVEN, Ring: DEEPSLATE_TILE_SLAB, Light: SEA_LANTERN, Style: SEQUENTIAL, Name: Tower}
```

A `Style` written at the pair level rather than on each end is how files from before it moved
look, and is read as the fallback for both, so those pairs keep behaving exactly as they did.

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
player-initiated: create, remove, rename, recolour. Nothing writes on the travel path.

Only the anchor, pattern and orientation are stored. The footprint is derived — storing 16
or 20 block coordinates that are a pure function of three fields would just be something
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
place, and `/wormhole ring list` reads it back with names. If a location index ever
genuinely becomes necessary it belongs in memory, where one already is.

**Rings are not named.** The id is short random hex, used for the filename, for
`/wormhole ring remove <id>`, and in log lines. Nothing addresses a ring by name at
runtime, because point-to-point pairing means there is nothing to address. Each *end* may
be named, which is what a listing reads back and what a traveller is told they are heading
for; the pair itself has no name of its own.

Format is plain YAML from the start. `GateSerializer` carries nine versions of legacy
binary baggage; there is no reason to inherit that.

## The cycle

```
IDLE        a move event inside either interior arms the pair
COUNTDOWN   the pattern lights up, counting down out loud — ABORTS if both interiors empty
DEPLOY      rings rise — COMMITTED, no abort, runs to completion
HOLD        the finished stack stands still for a second
FLASH       light runs down through the stack, then the swap
            then back up through it, with the travellers already there
HOLD        rings stand a moment more
RETRACT     rings return nearest-first
LINGER      rings gone, pad still lit for a second
COOLDOWN    pair refuses all triggers

            the pad is lit from COUNTDOWN through to the end of LINGER
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

A ring is seven or eight blocks across, so getting clear of one — over the interior and
across the perimeter — is around four blocks from the middle, close to a second at walking
pace. The abort window is only real because the countdown comfortably exceeds that.

`rings.countdown` defaults to 60 ticks and is floored at 30. Below that the abort window
stops being meaningful and rings begin taking people who were only walking past.

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

## Where each layer sits

The player lays the template **on top of** the floor, so the slabs occupy the space above it
rather than the floor itself. That one fact fixes three heights:

| | Height | Why |
|---|---|---|
| Countdown lights | one block **into** the surface | the pattern belongs in the floor, not hanging above it |
| Ring plane (template, first frame of the rise) | the space the slabs were laid in | rings come up out of the lit pattern |
| Passenger volume | from the ring plane into the room | that is where a standing player's feet are |

A ceiling ring mirrors all of it: lights go up into the ceiling, rings descend from the
space below it, passengers stand underneath.

Lighting the ring plane instead would put the pattern floating a block above the ground with
the rings starting inside it, which is why the lights and the rings sit in different layers
rather than sharing one.

This is also the one place the animation is allowed to replace a solid block. A light set
into a floor has a floor block in the way by definition, so the air-only rule that protects
everything else would mean the pattern never appearing at all. It is a handful of blocks the
player themselves marked out as a ring, each one remembered and put back at the end.

## Materials

A ring has two, and they do different jobs:

| | Default | Constraint | Shown |
|---|---|---|---|
| Ring | the slab it was laid in | **Must be a slab** (`minecraft:slabs`) | The travelling rings, during deploy and retract |
| Light | `rings.default-light-material` | Any placeable block | The perimeter during the countdown |

The ring material is not really a default at all: it is read off the template, so the config
value only applies if detection ever cannot say. The light has nothing to read, so it takes
the configured default and is changed with `edit` if wanted.

Whether something is a slab is asked of the game's own `minecraft:slabs` tag, so a data pack
that adds one gets a ring material for free. Where there is no registry to ask — in a unit
test, or before the server has finished starting — it falls back to the name, which is exact
for every slab the game ships, so the fallback is a worse *source* rather than a wrong
answer.

Lights have no equivalent tag. Minecraft has no light-emitting group and Bukkit cannot read a
light level from a `Material` at all, so the suggestions are written out by hand. That list
only has to look right: a drawn ring emits nothing whatever it is made of.

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

## What a ring tells you

A trip says six things, and where each is said matters as much as what it says.

| When | Message |
|---|---|
| You walk in | *Transport rings engaging — travelling to Tower. Step clear to cancel.* |
| Each second | *Transport in 3 seconds…* |
| Everyone leaves | *Transport rings powering down.* |
| The rings commit | *Rings deploying. Hold still.* |
| You arrive | *Arrived at Tower.* |
| Too soon after a trip | *Rings recharging. Ready in 42 seconds.* |

Where a name is set, the messages use it — you are told where you are going as you walk in
and where you have got to when you land. Rings with no name fall back to *Transport rings
engaging* and *Transport complete*, which is all there is to say about a place with no name.

All of those go to the **action bar**, not chat. A ring speaks once a second while counting
down and again when it fires, which in chat would be six lines per trip scrolling away
whatever the player was reading. Above the hotbar it is a status that replaces itself and
then goes — which is what all of it is.

Chat is kept for the one message a player has to act on and might otherwise miss: being
turned away from a pair that is not theirs.

**Messages are sent on entering a ring, never on every step taken inside one.** The move path
runs on each block boundary crossed, so a player wandering about on a pad that is recharging
would otherwise be told so several times a second. Whether a step was an entry is decided by
looking up where they came *from* as well as where they are — no timers and no remembered
state. Arming is deliberately not filtered this way: it still happens on any move inside,
which is what lets somebody who stays put after a trip be carried back once the cooldown
passes.

Refusals say which of the two reasons applies, because a player standing on a silent pad
deserves to know whether it will fix itself. *Recharging* ends by itself; *already in use*
and *private* do not.

### A refused ring shows itself

An idle ring is invisible, which is the point of it — the pad reads as ordinary floor until
it fires. That works against a player the moment a ring turns them away: they are told it is
recharging while standing on ground that looks like every other patch of ground, with no way
to tell where the thing is or how much of it they are in.

So a refusal briefly lights the pattern for that one player. It is the same drawing the
countdown uses, sent only to them and taken back after `rings.outline-ticks`, so nobody else
sees a ring flicker and nothing is written to the world. `rings.outline-on-refusal` turns it
off.

Shown for recharging and already-in-use. **Not** for a private pair: somebody being turned
away from a ring that is not theirs has no business being shown its extent, and being told
plainly that it is private is enough.

## Names

Each end can be called something — `/wormhole ring edit name Tower`, standing in the ring you
mean.

**The name belongs to the end, not the pair**, because the useful thing to say is where
somebody is *going*, and that is a different answer depending on which end they walked into.
One label on the pair could never do that.

It also reads better in a listing. Two end names give *Base to Mine*, which says which two
places are joined; a single pair label gave *Mine Line*, which only said that somebody had
named it. So the listing text is now derived from the two names rather than stored, and can
never disagree with them. A pair with one end named still says something useful; a pair with
neither falls back to its id, which is all there is to go on.

Naming by id is refused rather than applied to both ends. Calling both ends the same thing
would defeat the point of having them, so the command asks you to stand in the one you mean —
the only field where an id is not accepted.

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

## The pad stays lit

The lights come on with the countdown and stay on through everything — the rings rising, the
transport, the rings coming home — and go out a second *after* the last one has sunk back
into the floor.

Putting them out when the rings start rising would have the pad go dark at exactly the moment
it does the thing it was lit for. And putting them out on the same tick the last ring lands
reads as the whole thing being switched off rather than as the rings finishing; a beat later
reads as powering down. `rings.lights-linger-ticks` is that beat.

Mechanically this is why the drawing is kept in two sets rather than one. The rings are
replaced wholesale every frame; the lights are drawn once and outlast all of it. They never
overlap — the lights sit a block inside the surface and the rings travel out of it — so
neither has to know about the other.

## Rings are drawn, not built

**Nothing in a cycle changes the world.** The lights and the travelling rings are sent to
nearby clients as block changes and the server's own blocks are never touched, the same way
a gate draws its portal.

Making them real looked simpler and was worse in three separate ways. A server stopped
mid-cycle would keep the rings for good, since nothing would be left running to take them
down. Block-logging plugins would record a floor being replaced on every single trip. And
for the few seconds a ring stood there, its glowstone and slabs were ordinary breakable
blocks — mine one and you got a free glowstone, and the restore then skipped it and left a
permanent hole in the floor.

Drawing has none of those, and it makes putting things back trivial rather than delicate:
since the real blocks were never touched, undoing a drawing is just showing the client what
was always there. There is nothing to remember, nothing to restore in the right order, and no
need to check whether somebody changed a block underneath us. It also means a ring can be
drawn straight over whatever is in its way and still look like a complete ring, where placing
real blocks had to skip those positions and leave gaps.

It costs what a gate's portal costs: the drawing only exists for those it was sent to, and
anything handing a client a fresh copy of the chunk erases it. Rings are also not solid, so
nobody can stand on a rising ring or be shoved by one — which is the right behaviour, and one
less hazard to design around.

## Animation

Five rings end up **half a block of clear air apart** — one block centre to centre, since a
slab is half a block thick — with the lowest hanging half a block clear of the floor rather
than resting on it. Top to bottom that is five blocks of headroom.

They **travel further apart than they land**. On the way up there is a full block of clear
air between rings; the finished stack has half a block. Nothing compresses them — the leader
reaches its place and stops while the ones behind are still climbing, so the gaps close from
the top down, one at a time, as each ring arrives. Writing that as a compression step would
have been a second motion to keep in step with the first, for an effect that falls out of
rings simply stopping when they get there.

Because rings stop where they land, the finished stack is also the highest anything ever
gets — so five blocks of headroom is the whole requirement.

There are **two ways they get there**, both of which the show uses. `rings.default-style` picks the default and
`/wormhole ring edit style` changes one pair.

- **Concurrent** (type `fast`) — several climbing at once. A ring leaves the plane as soon
  as the one in front is a clear block above it, so what rises out of the floor is an evenly
  spaced column. They arrive in order, top first. Quicker, and the commoner look.
- **Sequential** (type `slow`) — never more than one in flight. The first out flies all the
  way to the furthest position and stops; only then does the next emerge.

Players type `fast` or `slow`; `concurrent`, `sequential`, `quick`, `flowing`, `stepped` and
`staged` all work too. The **stored** value stays `CONCURRENT` or `SEQUENTIAL` deliberately,
because those name what the setting actually does — how many rings are in the air at once —
and that stays true whatever the tick rate is. Naming the setting by speed would have it
claim the same ground as `rings.deploy-ticks`, which really is the speed knob, and the two
could then contradict each other: `slow` with `deploy-ticks: 1` is not slow.

They differ *only* in when a ring leaves the plane. Where each ends up, how far it travels
and how they come home are identical, so this is one number rather than two animations.

Style belongs to the **end**, like the materials do. Nobody watches both at once — a
traveller is at one of them, and by the time they can see the other they have arrived — so
a base can deploy differently from the outpost it connects to. The two still have to finish
together, and that is arranged by waiting for the slower of them rather than by forcing them
to match; a ring that has arrived holds its place anyway, so the wait costs nothing to draw.

The finished stack then **stands still for a second before anybody moves**, and again after.
Taking people the instant the last ring stops reads as the teleport interrupting the rings;
letting them arrive, hold, and only then flash reads as the rings doing it. Those two pauses
are most of what makes the effect read as a transport rather than as blocks moving, and are
why `HOLD` is a phase either side of the swap rather than the swap following the deploy
directly.

Retract is the reversal, so the stack loosens back out to a block apart as it comes down.
The **nearest ring goes home first** and the one that flew highest is the last to leave. This needs no code of its own: retract is deploy played backwards, and a sequence
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

## The transport flash

With the stack up and still, the light runs through it one ring at a time — **twice, once
each side of the transport.** It goes down as the travellers are taken and back up as they
arrive, so the two sweeps read as a departure and a landing rather than as one effect played
twice. Then the rings stand a beat and come home.

Only the departure is configured. `rings.flash-direction` is `TOP_DOWN` by default, as the
show does it, and the arrival is always its opposite. Two separate settings could be pointed
the same way, and the landing would then look like a second departure rather than the answer
to one. `rings.flash-ticks` is how long each ring stays lit.

Whoever has just arrived sees the upward sweep from its beginning at their end, because the
far stack has been standing there lit the whole time waiting for them.

The lit ring is drawn **over** the stack rather than instead of it, so the rings that are not
lit stay exactly where they are and nothing appears to move while the light passes.

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
  settle-ticks: 20           # stack stands still this long before the teleport
  hold-ticks: 20             # and this long after the light finishes, before retracting
  flash-ticks: 3             # how long each ring stays lit as the light passes
  flash-direction: TOP_DOWN  # departure; the arrival always runs the other way
  outline-on-refusal: true   # light the pattern for somebody a ring turns away
  outline-ticks: 40          # and for how long
  lights-linger-ticks: 20    # pad stays lit this long after the last ring is home
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
/wormhole ring list                       your pairs, by name where set
/wormhole ring remove [id]                remove both ends
/wormhole ring edit <field> <value>       edit the ring you are standing in
/wormhole ring edit <id> <field> <value>  edit both ends of that pair
/wormhole ring allow <player> [id]        let somebody use it
/wormhole ring deny <player> [id]         stop them
/wormhole ring owner <player> [id]        hand the pair to somebody else

  fields:  ring <material>    the travelling slabs; must be a slab   per end
           light <material>   the countdown lights                   per end
           name <text>        what this end is called                per end
           access public|private                                     per pair
           style fast|slow                                            per end
```

Everything adjustable lives under one `edit` verb rather than a subcommand per field. Gates
grew a separate top-level command for each — `portalmaterial`, `irismaterial`,
`lightmaterial`, `wooshdepth` — which is four registry entries, four usage strings and four
completers saying the same thing four ways. `edit` takes the field as an argument and stays
one entry however many fields rings end up with.

**Whether an id is given is what selects the scope**, and it reads the way people work.
You are usually standing in the ring you want to change, having just walked to it to look
at it, so the id is omitted and only that end changes. Naming a pair by id means you are
somewhere else and thinking about the pair as a whole, so both ends change. `name` is the
exception in the other direction: it is refused with an id, because calling both ends the
same thing would defeat the point of having names at all.

Working out which ring you are standing in costs nothing — it is the same `RingIndex`
lookup the move path makes.

**Every field completes its own values.** `ring` offers only slabs, because a slab is the
only thing the command will accept — offering anything else would be offering a mistake.
`light` offers any placeable block. `access` and `style` offer their words, and `name` is
whatever the player wants.

That is worth having because nobody remembers how `polished_deepslate_brick_slab` is spelled,
and there are dozens of slabs and several hundred blocks to choose from. The awkward part is
that `edit` takes an optional pair id, so the field sits at one of two positions and the
value at one of two more. The completer decides from the word *before* the one being typed
rather than from the argument count, which gets both forms right without having to know which
one it is looking at.

Pair ids are deliberately not completed. A tab completer is not told who is asking, so the
choice was between listing every pair on the server and listing none, and none says less than
it should rather than more.

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

## Events for other plugins

`RingTravelEvent` fires once per travelling player and is cancellable. Cancelling takes that
player out of the trip and leaves everyone else in it: the rings still fire, and they stay
put while the others go. There is no way to cancel a whole cycle from it, because by that
point the rings are up and coming down again regardless.

**The timing is the part that matters.** It fires after both ends have been read and before
either has been written, so a listener always sees the trip as it was before any of it
happened — never a half-finished one with the people from one end already standing in the
other. That is the same ordering the swap itself depends on, and the event is asked inside
it rather than around it.

It fires only for players. Mobs, items and vehicles travel as cargo and raise nothing, so
cancelling stops a person and not the world around them.

The event is reached through the same `Surroundings` seam as everything else, rather than
being fired from the cycle directly. That keeps the cycle free of Bukkit and lets the rule
that a refusal *drops a passenger* rather than *cancelling the trip* be tested without a
server.

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
model/ring/RingMessages.java       what a ring tells the people standing in it
model/ring/RingOutline.java        showing a refused player where the ring is
events/RingTravelEvent.java        cancellable, once per travelling player
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
6. Rings climb a clear block apart and finish half a block apart, the gaps closing from the
   top down, and nothing ever rises above where the top ring settles.
7. A full cycle changes no real block, and leaves nothing drawn behind.
8. The flash touches every ring exactly once, and the arrival sweep runs back the way the
   departure came.
9. A cancelled `RingTravelEvent` drops that passenger and carries everyone else, and is
   asked only after both ends have been read.
10. The pad stays lit from the countdown until after the rings are home, and the rings can
    be taken down without taking the lights with them.
11. Cooldown is shared per pair, and the landing settle-move does not re-fire it.
12. Overlapping footprints are refused at create, including against gate blocks.
13. Pattern matching picks the right one of the two, and rejects a near-miss circle.
14. A pair round-trips through YAML with its footprint correctly re-derived, and lands in
   the file for its world.
15. A damaged entry in a world file is skipped with a log line, and the rest still loads.
16. An entity on a perimeter block is nudged inward at deploy-start and travels, and is
   left alone when the interior has no room for it.
17. Pairing refuses a second endpoint placed in a different world, and says why.
18. `edit` without an id changes only the end the player is standing in; with an id it
    changes both, and a non-slab ring material is refused either way.
19. A private pair refuses a stranger, carries the owner and the people they named, and
    leaves an unpermitted player standing while everyone else goes.
20. A stored pair with a missing or unreadable access field loads private, never public.
