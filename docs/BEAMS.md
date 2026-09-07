# Beaming — Design

How a beam destination is stored, resolved and travelled to, and why the sequence is built the
way it is. The [README](../README.md#beaming) is the server owner's guide; this says why. The
other two ways to travel have their own documents, [GATES.md](GATES.md) and
[RINGS.md](RINGS.md).

A beam is the third way to travel, and the only one with no structure at all. A gate is a
building; a ring is a pair of pads in a floor. A beam destination is a **single named point**
somebody stood on once — no blocks, no partner, nothing to walk into.

| | Stargate | Ring | Beam |
|---|---|---|---|
| What exists in the world | A built frame | Two invisible pads | Nothing |
| Unit | One gate, dialled to another | A permanent pair | A single point |
| Started by | Button, sign, redstone, `/dial` | Walking into it | A command |
| Direction | One way per dial | Both ends together | One way, no return |
| Range | Cross-world, config permitting | Same world, always | Cross-world, always |
| Who can go | Network and node | Owner and allow list | Public list, or your own places |

## Contents

- [Destinations](#destinations)
- [Storage](#storage)
- [Resolving a name](#resolving-a-name)
- [The sequence](#the-sequence)
- [Why the traveller stays physically present](#why-the-traveller-stays-physically-present)
- [Hiding is not invisibility](#hiding-is-not-invisibility)
- [Arriving blind](#arriving-blind)
- [Active and frozen are two states](#active-and-frozen-are-two-states)
- [Mounts](#mounts)
- [Timings are clamped, once](#timings-are-clamped-once)
- [The pure half and the dull half](#the-pure-half-and-the-dull-half)
- [Terrain drifts](#terrain-drifts)
- [When a sequence fails](#when-a-sequence-fails)
- [Cost and cooldown](#cost-and-cooldown)
- [Sound](#sound)
- [Permissions](#permissions)
- [Commands](#commands)
- [Config](#config)
- [Layout](#layout)
- [What the tests guard](#what-the-tests-guard)

## Destinations

Two kinds, and the difference is only which registry holds them:

- **Public destinations** — curated by staff with `/wormhole beam admin set`, reachable by
  anyone with `wormhole.beam.use`.
- **Places** — one private set per player, made with `/wormhole beam place set`, reachable
  only by the player who made them.

`BeamDestination` carries no access field of its own. Which map it lives in is what makes it
public or private, so there is no third state to keep consistent with the first two.

**Only a public destination can have its own cost.** A place is reachable only by the player
who made it, so setting a cost on one would be them choosing what to pay themselves — there is
no `place cost` command, and a place's cost stays null (inherit the global default) for its
whole life. The field lives on the shared type rather than on a public-only subtype so that
`BeamManager` and `BeamYamlManager` never have to know which kind they are holding.

## Storage

Everything in one file:

```
plugins/WormholeXTreme/WormholeXTremeDB/beam.yml
```

```yaml
Public:
  Spawn:   {World: world, X: 128.5, Y: 64.0, Z: -310.5, Yaw: 90.0, Pitch: 0.0}
  Market:  {World: world, X: 40.5, Y: 71.0, Z: 12.5, Yaw: 0.0, Pitch: 0.0, Cost: 25.0}
Places:
  069a79f4-44e9-4726-a5be-fca90e38aaf5:
    Mine:  {World: world_nether, X: -88.5, Y: 31.0, Z: 204.5, Yaw: 180.0, Pitch: 0.0}
```

**One file, not one per world** — the opposite of what rings do, for the reason rings did it.
A ring pair is same-world by design, so sharding by world made the storage layout enforce the
rule. Beaming is deliberately cross-world capable, so there is no equivalent rule to enforce
and nothing to gain from the split. A single file is also the honest size for what this holds:
a server's public list plus every player's places, not a structure that grows with distance.

Yaw and pitch are stored, so a destination faces the way its author was facing. Written
through a temp file and `ATOMIC_MOVE`, the same as gates and rings, so a partial write is never
visible.

`Cost` is written only when a destination has an override, and read back as **null rather than
zero** when absent — the difference between "inherit the default" and "explicitly free" is real
and both are expressible. A malformed `Cost` is ignored rather than failing the whole entry.

## Resolving a name

One method, `BeamTravel.travelTo`, shared by `/wormhole beam to` and `/wormhole go`, so the two
commands cannot quietly disagree about what a name means.

**Your own places are checked first, then the public list.** A player who names a place after a
public destination gets their own.

The return value carries a distinction that matters: it is **true when the name resolved to
something**, whether the trip started or was refused, and **false only when nothing anywhere is
named this**. That is what lets `/wormhole go` fall through to try a gate without this method's
refusal messages getting in the way — and what stops a permission problem being reported as a
typo.

`/wormhole go` tries a gate first and a beam destination second, so a gate name always wins.
Either way the traveller arrives through the beam sequence: `go` to a gate beams you to its
arrival point rather than walking you through the ring.

A destination whose world is not currently loaded says so, rather than loading a world as a
side effect of somebody typing a name.

## The sequence

Four phases, matched beat for beat against the reference footage: a glow gathers and appears
to absorb the traveller; they and the light leave in a column; the column arrives at the far
end and deposits them; it fades.

| Phase | Default | What happens |
|---|---|---|
| **Envelop** | 12 ticks | A dense `END_ROD` burst at body height, brightening, **tracking the traveller** — they can still walk, turn and react. They vanish partway through, at tick 6. |
| **Rise** | 18 ticks | The envelope opens into the full-height column, rooted where they stood when they vanished, at constant brightness, climbing away. The real teleport fires at tick 12 — mid-rise. |
| **Descend** | 20 ticks | The same column arrives from above at the destination and settles. The traveller is already physically there. |
| **Fade** | 8 ticks | The instant the column settles the traveller is revealed, standing inside the light, and the column collapses on a deliberately quick tail. |

Two of those are worth stating plainly because they are what the sequence is built around.

**The traveller is not frozen during the envelope.** Somebody in the reference footage is still
free to move right up until they are taken, so this is too. The vanish tick is the moment that
changes: the freeze, the hide, and the departure column's fixed root all take hold on that one
tick.

**The real teleport fires mid-rise, not at the end.** The traveller has had most of the
departure in view before leaving, and the remainder plays out at the origin with nobody there.

Delivery reads as an arrival, not a second build-up — hence the short fade rather than a
mirror of the envelope.

One asymmetry does not come from mirroring the two ends: the destination track could in
principle be staged entirely independently of the player, but the origin track cannot. The
teleport has to wait on it, at least partly, rather than firing the moment they vanish.

## Why the traveller stays physically present

The "disappear into a beam, then reappear out of one" read relies on a real API property:
**hiding is observer-relative.** A hidden player still sees their own surroundings and any
particles normally; they are only withheld from *other* players' clients.

So the traveller is never removed from the world. They stay physically present — hidden from
everyone else, frozen in place — through the tail of the rise, and the teleport moves a player
who is standing right there the whole time.

## Hiding is not invisibility

`Player#hideEntity(Plugin, Entity)` is what other players see, and it is the one that matters.

This was `PotionEffectType.INVISIBILITY` alone to begin with, which is the obvious tool and does
not do the job. Invisibility hides a player's *body* and nothing else, so a held sword, worn
armour, a shield and an elytra all keep rendering exactly where they were. **A traveller
carrying anything never dissolved into the column at all** — their equipment stayed standing in
it, in the shape of a person, which is the one read the whole sequence exists to sell.
`hideEntity` stops the entity being sent to that client, so equipment, nameplate and hitbox go
with it. It is plain Spigot API, not Paper-only, across the whole 1.20–1.21.10 range, and
unlike the one-argument `hidePlayer(Player)` it is not deprecated.

**Invisibility is still applied on top, for a different audience.** Hiding is observer-relative
and deliberately skips the traveller themselves — and a client always renders its own player
regardless — so without it a traveller in third person watches themselves stand solid in the
column while everybody else sees an empty beam. Invisibility is the only thing that reaches
their own camera. It is a partial answer by nature: their own equipment keeps rendering for
them either way, so an armoured traveller in third person sees their kit without a body inside
it. That is the trade, taken knowingly, for the unarmoured case looking right.

**The effect is applied only when the traveller does not already have it.** Removing it
unconditionally at the end is what used to cancel an invisibility potion somebody had drunk
themselves — the sequence taking away something it never gave. What this did not apply, it does
not remove, and that one flag is the whole guard.

Two consequences of per-observer hiding are worth naming rather than fixing: a player who logs
in mid-beam sees the traveller for the remaining tick or so, which is not worth a join listener
to close; and Bukkit reference-counts hiding per plugin, so another plugin hiding the same
entity keeps it hidden after this one reveals it, which is correct rather than a leak.

Revealing is deliberately forgiving. `showEntity` on an entity that was never hidden is a
no-op, and the guard above means an effect that was never applied is never removed — so the
failure path can just call `show` without having to know how far the sequence got.

## Arriving blind

Nothing about hiding stops the **traveller** from looking around the destination the instant
they physically arrive, which is well before the descend column has finished settling. The
freeze only ever locked position; camera look cannot be locked server-side at all. So they got
a clear view of the destination followed by an arrival effect that then read as arriving late.

**Blindness alone did not close that gap.** In play-testing it turned out to be mostly a
render-distance fog rather than an opaque blackout: nearby terrain and anything bright —
daylight, torches, the beam's own `END_ROD` particles — still showed through. `DARKNESS`, the
real dark vignette a warden or sculk shrieker applies, stacked on top of it is what actually
blocks the view.

Both go on the moment the real teleport fires and come off the moment the column settles — the
same two ticks the hide and the reveal already key off — so the traveller's own vision resolves
in step with the visual instead of running ahead of it.

## Active and frozen are two states

Two sets, not one flag:

- **Active** covers the whole sequence, from the first tick to the last, envelope included
  while the traveller is still free to walk about. This is what refuses a second beam stacking
  onto a first.
- **Frozen** is the narrower, later state: position-locked, starting only at the vanish tick.

Collapsing them into one flag breaks one of the two, and there is no third option. Either the
already-beaming guard stops working during the envelope, so a second beam can start while the
first is still gathering, or the traveller is locked in place from the very first tick again,
which defeats the point of letting them move at all.

The freeze reverts x/y/z on a `PlayerMoveEvent` and **keeps the new yaw and pitch**, so looking
around during the sequence still works. Camera look is purely client-rendered; there is nothing
to lock.

## Mounts

Bukkit's `Entity#teleport(Location)` contract is that a riding entity is dismounted before
teleportation, unchanged across the whole supported range. So the single `player.teleport(...)`
the sequence used to make silently tipped the traveller off their horse and beamed them alone,
leaving the horse at the origin.

Gates already solved this, and beaming borrows the answer: move the mount, then put the rider
back on it, through the shared `PassengerReattach` retry loop. Paper's
`TeleportFlag.EntityState.RETAIN_VEHICLE` would be the one-call version, but it is not in the
plain Spigot API at any version in this range, and this plugin ships one jar for Spigot, Paper
and Purpur alike.

**Holding the mount still is the other half, and less obvious.** The freeze works by reverting
`PlayerMoveEvent`, which a rider does not raise — their position comes from the vehicle — so a
frozen player on a horse could still steer it clean out of the departure column during the
rise, which at eighteen ticks is a long way at a gallop. Rather than fight that, the rider is
dismounted at the vanish tick so the existing freeze does exactly what it was written to do,
and the mount's AI is switched off so it does not wander off on its own. Neither is visible to
anybody: both are already hidden on that same tick.

A mount somebody else is also sitting on is left alone. The captured stack is the mount and
everything riding it, and an unmounted traveller gets an absent mount whose every method is a
no-op, so the sequence never branches on null.

## Timings are clamped, once

Six durations come from config, and two of them have to sit strictly inside a third: the vanish
must fire before the envelope ends, and the teleport must fire before the rise ends.

Read naively, a server admin setting `beam-teleport-at-step` equal to or past `beam-rise-ticks`
means `tick == teleportAtStep` is never reached while `tick < riseTicks` still holds. The
teleport condition never fires, and **the traveller is left frozen and invisible with no way
out short of a restart.**

Resolving all six together, once, in one place is what makes that impossible whatever a config
file says. Durations are read at the start of each sequence rather than re-read every tick, so
a config change mid-flight cannot desync a beam already running — the same way ring timings
work.

## The pure half and the dull half

`BeamFrame` computes what a tick should do, purely, from the tick number and the resolved
timings: no Bukkit types, no side effects, nothing needing a running server. The sequence asks
it what should happen and then does exactly that — spawn a column at a given height, offset and
density, apply an effect, play a sound, fire the teleport — with no arithmetic of its own left
to get wrong.

This mirrors the split the ring subsystem grew (`RingCycle` for the decisions, `RingTransit`
for touching a live world), and for the same reason: the ordering, the frame arithmetic and the
phase boundaries are what is actually easy to get subtly wrong. An off-by-one at a boundary
reads as a visible stutter or a column starting a tick late, and none of it needs a server to
get right.

It was worth doing *after* the shape had survived play-testing rather than before, for a
sequence still being tuned by feel. Every quantity is the same value the original un-split
sequence computed inline; the split relocated the computation, not what it computes.

Three quantities turn out to cover all four phases: `height` separates the envelope (body
height) from the full column, `yOffset` is what rising and descending both are, and `density`
is what brightening and fading both are.

## Terrain drifts

A destination's coordinates were recorded once and nothing re-checks them, so somewhere that
was standable when it was saved can be dug out or built over. A stored point can strand a
traveller buried or hanging in mid-air.

The safe-ground correction runs **once, in `BeamAnimation.start`**, for every beam it runs
rather than in each caller. Every caller was already doing it identically immediately before
calling in, which made it a convention the fifth caller could forget rather than a guarantee.
It is idempotent — it prefers the exact stored spot whenever that spot is already standable —
so a caller that still snaps its own location first loses nothing.

Doing it once also means the real teleport and the descend column share the corrected point, so
**the effect cannot land somewhere the player does not**.

## When a sequence fails

If anything throws mid-sequence, the traveller is freed rather than left stuck: effects
removed, mount released, freeze cleared, and a message saying so. Each step is guarded on its
own, because clearing the freeze is the one that actually matters and it must not be skipped
because something earlier failed.

That is the failure this shape is most worth protecting against. A stuck beam leaves somebody
frozen and invisible, which no amount of reconnecting fixes.

## Cost and cooldown

Both are **checked in `BeamTravel` before the sequence starts, and applied from the depart
hook** — once the real teleport has fired, not at the point of merely starting. The same split
gate travel makes, for the same reason: applying either at the check spends it on a trip that
has not happened and, if the player disconnects mid-sequence, may never happen.

A non-zero cost is **said up front**, before the sequence starts, rather than only discovered
once charged. With per-destination cost real, a silent auto-charge could be a genuine surprise;
a hard confirm step felt like more friction than gate travel has ever needed for the same kind
of cost, so this is the middle ground — seen, not gated on.

Cost resolves as the destination's own override, else the global default, else **zero when
economy is disabled or Vault is absent**. That last check was missed when per-destination cost
was added: `canAfford` and `charge` already fail open in that situation, so without it the
player was shown "this will cost X" and "charged X" for a charge that never happened.

`wormhole.beam.admin` **bypasses both entirely** — neither checked nor applied. That is a
deliberate departure from gate travel, whose cooldown and cost apply uniformly with no such
bypass. Staff testing destinations or handling a support request are the common case it is
actually for, and it reuses the node that already gates managing public destinations rather
than inventing a second one meaning the same thing.

The cooldown is a single duration per player, unlike gates' `StargateRestrictions`, which still
carries a multi-group system beaming has no equivalent concept for — a beam destination is not
grouped the way a restriction group grouped gates.

Both refusal messages are written for beaming rather than reused from the gate strings, whose
wording names a stargate specifically and would be wrong here.

## Sound

Three sounds: a power-up as the sequence begins, a departure where the traveller leaves, and an
arrival where they land.

| Setting | Default |
|---|---|
| `beam-sound-charge` | `block.respawn_anchor.charge` |
| `beam-sound-depart` | `entity.enderman.teleport` |
| `beam-sound-arrive` | `entity.shulker.teleport` |

Names and volume are read live, the same way ring sounds are, so an admin can retune or silence
beaming without a restart. The defaults are deliberately distinct from the ring palette
(`block.beacon.*`, `block.piston.extend`) so the two mechanics do not sound alike, and taken
from vanilla's own teleport sounds so nothing had to be invented.

Played through the shared `Sounds` helper, which never throws. A sound is decoration, not worth
failing a beam over.

## Permissions

Four nodes, checked by `BeamPermissions` rather than `WXPermissions` — the same separation
rings make, and for the same reason: the gate class is built around gates, and beaming shares
nothing with one beyond both being ways to travel.

```
wormhole.beam.use             travel to a public destination or your own place   default: true
wormhole.beam.place           create and manage your own places                  default: true
wormhole.beam.admin           manage public destinations; bypass cost/cooldown   default: op
wormhole.beam.admin.teleport  beam anybody to a player or to raw coordinates     default: op
```

**`admin.teleport` is deliberately not implied by `admin`.** Curating the destination list and
relocating any player at will are different orders of power, and a beam-admin delegate should
not inherit the second just for holding the first.

The check takes a `CommandSender` rather than a `Player`, because `admin goto` and `admin send`
have to work from console and command blocks — `isOp()` and `hasPermission` are defined on
`CommandSender` itself. Operators hold everything, matching gates and rings.

## Commands

```
/wormhole beam to <name>                  travel; your own places first, then public
/wormhole beam list                       list public destinations
/wormhole beam place list                 list your own places
/wormhole beam place set <name>           save your current location as a place
/wormhole beam place remove <name>        remove one of your own places
/wormhole beam admin set <name>           register a public destination where you stand
/wormhole beam admin remove <name>        remove a public destination
/wormhole beam admin cost <name> <amount> what it costs to use
/wormhole beam admin cost <name> default  clear the override; use the configured default
/wormhole beam admin goto <player|destination|x y z [world]>
/wormhole beam admin send <target> <player|destination|x y z [world]>
```

**Travel goes through one verb, `to`.** It used to take a bare name — `/wormhole beam <name>` —
which sat in the same argument slot as `list`, `admin` and `place` and read as one more
subcommand rather than as the thing you are travelling to. A name that collides with a verb is
no longer ambiguous once a word in front of it says "what follows is a destination."

`goto` and `send` are the one place this command accepts a non-player sender. Console and
command blocks have no location of their own to beam *from*, so only `send` makes sense for
either — never a bare `goto`.

## Config

```yaml
beam-envelop-ticks: 12       # glow gathers at body height
beam-vanish-at-step: 6       # how far into the envelope the traveller vanishes
beam-rise-ticks: 18          # column rises and departs
beam-teleport-at-step: 12    # how far into the rise the real teleport fires
beam-descend-ticks: 20       # column arrives and settles
beam-fade-ticks: 8           # column collapses once the traveller is deposited

beam-sounds-enabled: true
beam-sound-volume: 1.0
beam-sound-charge: block.respawn_anchor.charge
beam-sound-depart: entity.enderman.teleport
beam-sound-arrive: entity.shulker.teleport

beam-use-cooldown-enabled: false
beam-use-cooldown-seconds: 120
beam-economy-use-cost: 0     # 0 = free; a public destination may override it
```

`beam-vanish-at-step` and `beam-teleport-at-step` are clamped strictly inside the phases they
sit in — see [Timings are clamped, once](#timings-are-clamped-once). The values above are what
the plugin falls back to when a setting is absent, so a config file that has never mentioned
beaming still gets the tuned sequence.

## Layout

```
model/beam/BeamDestination.java    one named point: world, position, facing, optional cost
model/beam/BeamManager.java        the public map and one place map per player
model/beam/BeamYamlManager.java    load and save beam.yml
model/beam/BeamTravel.java         resolve a name, check cost and cooldown, start a beam
model/beam/BeamAnimation.java      the sequence, and the only class that touches Bukkit
model/beam/BeamFrame.java          what a tick should do, computed purely
model/beam/BeamTiming.java         six durations, clamped into consistency
model/beam/BeamVisibility.java     hiding the traveller, and putting them back
model/beam/BeamFreeze.java         active and frozen, as two sets
model/beam/BeamFreezeListener.java holds position, leaves the camera alone
model/beam/BeamMount.java          what they were riding, and what to do about it
model/beam/BeamCooldown.java       one duration per player
model/beam/BeamSounds.java         charge, depart, arrive
model/beam/BeamPermissions.java    the four nodes
command/handlers/BeamCommand.java  /wormhole beam <verb>
command/Go.java                    /wormhole go: a gate first, then a beam destination
```

The split that matters is `BeamFrame` against `BeamAnimation.Sequence`. Everything about *when*
a thing happens is pure and testable with no server running; everything that touches a live
world is left with no decisions in it.

## What the tests guard

- **Every phase boundary lands with no gap and no overlap**, and each one-shot event fires
  exactly once (`BeamFrameTest` — thirteen cases, one per boundary and event).
- **The rise is still active on the teleport tick**, so the origin column keeps playing after
  the traveller has gone (`BeamFrameTest`).
- **A config that would strand a traveller frozen and invisible is clamped into one that
  cannot**, checked across a wide range of inputs rather than at a few chosen points
  (`BeamTimingTest`).
- **Active and frozen stay independent**, and two players never share either
  (`BeamFreezeTest`).
- **A mount is captured, held and released correctly** — including that releasing twice gives
  the AI back once, and that a mount whose AI was already off is left off (`BeamMountTest`).
- **A mount somebody else is also riding is left alone** (`BeamMountTest`).
- **Cost is zero whenever economy is off or Vault is absent**, whatever the destination or the
  config says (`BeamTravelTest`).
- **A destination with no cost reads back as null, not zero**, and one explicitly set free
  reads back as zero, not null (`BeamYamlManagerTest`).
- **A malformed cost field is ignored rather than failing the whole entry**
  (`BeamYamlManagerTest`).
