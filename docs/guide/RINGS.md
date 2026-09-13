# Transport rings

A ring is an invisible pad set into a floor or ceiling. Walk in, it counts down, and everything at
both ends swaps in the same instant. Why they work the way they do is in the design notes,
[docs/RINGS.md](../RINGS.md).

| | Stargate | Ring |
|---|---|---|
| Addressing | Dial any gate by name | Fixed pair |
| Orientation | Vertical | Floor or ceiling |
| Activation | Button, sign, redstone, `/dial` | Walk into it |
| Direction | One way per dial | Both ends fire together |
| Appearance | Permanent structure | Invisible until it fires |
| Range | Cross-world, config permitting | Same world |

Two people at opposite ends swap places in one trip. Rings reach 256 blocks across but the full
height of the world, because going straight down — a mine to the hall above it — is what they are
for.

## Contents

- [Building a ring pair](#building-a-ring-pair)
- [Using rings](#using-rings)
- [Ring settings](#ring-settings)
- [Editing a pair](#editing-a-pair)
- [Permissions](#permissions)
- [Sounds](#sounds)

## Building a ring pair

Lay a circle of slabs, stand inside it, and run `/wormhole ring create`. Do the same somewhere else
in the same world and the two are paired. Only then are the slabs consumed and the floors put back
as they looked.

```
ODD — 7 across, 16 slabs          EVEN — 6 across, 12 slabs

    . . # # # . .                     . . # # . .
    . # : : : # .                     . # : : # .
    # : : : : : #                     # : + : : #
    # : : + : : #                     # : : : : #
    # : : : : : #                     . # : : # .
    . # : : : # .                     . . # # . .
    . . # # # . .

  # = lay a slab    : = stand anywhere in here    + = anchor
```

Both are [drawn to scale](../RINGS.md#patterns) in the design notes, along with
[what the deploy looks like frame by frame](../RINGS.md#what-that-looks-like).

- **Only the ring**, not a filled disc.
- **One kind of slab.** It becomes the ring's material — deepslate slabs rise as deepslate.
- **All facing the same way.** Bottom slabs on a floor make a floor ring; top slabs under a ceiling
  make a ceiling ring. Double slabs are refused.
- **Four blocks of headroom** above a floor ring.
- **A ceiling ring needs a room four to ten blocks tall**, since its rings fall to the floor.
- Not overlapping another ring or a gate, and within 256 blocks across and 384 in height of its
  partner.

A refusal says exactly what is wrong. `/wormhole ring cancel` abandons a half-built pair and gives
its slabs back; `/wormhole ring remove` lays both circles back out so a pair can be moved.

## Using rings

Walk in. The floor opens along the ring's pattern and counts down; step clear before it commits and
it stands down. Then four rings rise, the light runs through them, and you are at the other end. A
**ceiling ring**'s rings fall to the floor instead, so you stand inside them.

- **Everything in the ring travels** — players, mobs, items, vehicles. Only players are checked for
  access. Ride in on a horse and you arrive still on it.
- **It refuses if the inside is not fit to arrive in**: every square must be clear, with solid
  ground under it. Water and lava do not count as ground. What is built *around* a ring is up to
  you. A refused trip costs nothing.
- **A pair rests for a minute** after carrying somebody. Stepping onto a resting pad tells you how
  long is left and briefly shows where the ring is.
- **Name an end** — `/wormhole ring edit name Tower`, standing in it — and its partner tells
  travellers where they are heading.

Nothing in a cycle changes the world: the rings and lights are drawn to nearby players, so a
"light" material does not actually light anything —
[why](../RINGS.md#rings-are-drawn-not-built).

## Ring settings

All under `rings:` in `config.yml`.

| Setting | Default | What it does |
|---|---|---|
| `countdown` | 60 | Ticks before the rings commit. At least 30, so stepping clear stays possible. |
| `cycle-cooldown` | 1200 | Ticks before a pair fires again. |
| `deploy-ticks` | 2 | Ticks between animation frames — the speed knob. |
| `settle-ticks` | 20 | How long the finished stack stands before transport. |
| `flash-ticks` | 3 | How long each ring stays lit as the light passes. |
| `hold-ticks` | 20 | How long the stack stands after the light. |
| `lights-linger-ticks` | 20 | How long the pad stays lit after the last ring is home. |
| `reach` | 4 | Block layers of passenger volume above the pad. |
| `min-separation` | 8 | Required distance between ring anchors. |
| `max-link-distance` | 256 | Furthest two ends may be across. `0` is unlimited. |
| `max-link-height` | 384 | Furthest two ends may be in height. `0` is unlimited. |
| `max-ceiling-drop` | 10 | How far below a ceiling ring to look for the floor. |
| `max-pairs-per-player` | 10 | Quota. `0` is unlimited. |
| `default-access` | `PRIVATE` | What a new pair starts as. |
| `default-style` | `CONCURRENT` | How the stack deploys. |
| `default-light-material` | `REDSTONE_LAMP` | What the pad lights up as. |
| `default-flash-material` | `REDSTONE_LAMP` | What a ring turns to as the light passes. |
| `default-ring-material` | `SMOOTH_STONE_SLAB` | Fallback only; normally read from the slabs you laid. |
| `outline-on-refusal` | `true` | Briefly show the pattern to somebody a ring turns away. |
| `outline-ticks` | 40 | How long that outline stays up. |

## Editing a pair

`/wormhole ring edit [id] <field> [value]`. Standing in a ring edits that end; naming a pair by id
edits both.

| Field | Scope | Values |
|---|---|---|
| `ring` | per end | Any slab, including one a data pack adds |
| `light` | per end | The pad while the ring works; completion suggests blocks that look like lights |
| `flash` | per end | The light running through the stack |
| `name` | per end | Free text; stand in the ring you mean |
| `access` | per pair | `public` or `private` |
| `style` | per end | `fast` sends several rings up together, `slow` one at a time |
| `reset` | per end | Back to the slab it was laid in, and the server's default lights and style |

`reset` leaves ownership, access, the allow list and names alone.

Access is per pair because both ends fire together. A private pair is usable by its owner and
anyone named with `/wormhole ring allow <player>` — which covers being carried as well as setting it
off. `ring deny <player>` takes that back, and `ring owner <player>` hands the pair over.

Other ring commands: `ring list`, `ring remove [id]`, `ring cancel`.

## Permissions

| Node | Default | Allows |
|---|---|---|
| `wormhole.ring.build` | op | Creating and pairing rings |
| `wormhole.ring.use` | true | Travelling by a ring you are allowed on |
| `wormhole.ring.admin` | op | Using and managing any pair |
| `wormhole.ring.unlimited` | op | Owning more pairs than the quota |

Being on an allow list lets somebody travel, not recolour, rename, give away or delete the pair.

## Sounds

General rules — naming, volume, `none` — are in [Sounds](SERVER.md#sounds).

| Setting | Default | When it plays |
|---|---|---|
| `ring-sounds-enabled` | `true` | Everything below is ignored when off |
| `ring-sound-volume` | 1.0 | About sixteen blocks |
| `ring-sound-open` | `block.beacon.activate` | At both ends, as the pad opens |
| `ring-sound-ring` | `block.piston.extend` | Once per ring, pitch climbing as the stack builds and falling as it returns |
| `ring-sound-flash` | `block.beacon.power_select` | At both ends, at the moment of transport |
| `ring-sound-close` | `block.beacon.deactivate` | At both ends, as the pad closes |
| `ring-sound-refused` | `block.note_block.bass` | To a turned-away player only |
