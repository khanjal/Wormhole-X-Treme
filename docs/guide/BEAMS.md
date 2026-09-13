# Beaming

Beaming is the only way to travel with nothing to build. A **beam destination** is a named point
somebody stood on once. You go there with a command, a column of light takes you, and the only way
back is another beam. Why it works the way it does is in the design notes,
[docs/BEAMS.md](../BEAMS.md).

- **Public destinations** are curated by staff and reachable by anyone.
- **Places** are private — each player has their own.

A name is looked up in your own places first, then the public list. Beams cross worlds freely; a
destination in an unloaded world says so rather than loading it.

## Commands

```
/wormhole beam to <name>                  travel; your own places first, then public
/wormhole beam list                       list public destinations
/wormhole beam place list                 list your own places
/wormhole beam place set <name>           save where you are standing
/wormhole beam place remove <name>        remove one of your places

  staff:
/wormhole beam admin set <name>           register a public destination where you stand
/wormhole beam admin remove <name>        remove a public destination
/wormhole beam admin cost <name> <amount> what it costs to use
/wormhole beam admin cost <name> default  go back to the configured default
/wormhole beam admin goto <player|destination|x y z [world]>
/wormhole beam admin send <target> <player|destination|x y z [world]>
```

`/wormhole go <name>` reaches the same places, trying a gate name first.

You arrive facing the way whoever saved the destination was facing. If the ground there has changed,
you are put on the nearest safe spot. Only public destinations can have their own cost.

Permissions are listed under [Permissions](SERVER.md#permissions).

## Settings

| Setting | Default | What it does |
|---|---|---|
| `beam-envelop-ticks` | 12 | How long the glow gathers. You can still move during this. |
| `beam-vanish-at-step` | 6 | How far into that you disappear. |
| `beam-rise-ticks` | 18 | How long the column rises and departs. |
| `beam-teleport-at-step` | 12 | How far into the rise you are moved. |
| `beam-descend-ticks` | 20 | How long the column takes to arrive at the far end. |
| `beam-fade-ticks` | 8 | How long it takes to collapse once you are there. |
| `beam-use-cooldown-enabled` | `false` | Whether beaming has a per-player cooldown. |
| `beam-use-cooldown-seconds` | 120 | How long that cooldown is. |
| `beam-economy-use-cost` | 0 | Default cost of a beam. A public destination may override it. |

The two `-at-step` settings are clamped inside their phase whatever you write, so a traveller can
never be left frozen and invisible. Durations are read when a beam starts, so a change does not
disturb one in flight.

## Sounds

General rules — naming, volume, `none` — are in [Sounds](SERVER.md#sounds).

| Setting | Default | When it plays |
|---|---|---|
| `beam-sounds-enabled` | `true` | Everything below is ignored when off |
| `beam-sound-volume` | 1.0 | About sixteen blocks |
| `beam-sound-charge` | `block.respawn_anchor.charge` | As the glow gathers |
| `beam-sound-depart` | `entity.enderman.teleport` | Where the traveller leaves |
| `beam-sound-arrive` | `entity.shulker.teleport` | Where they land |
