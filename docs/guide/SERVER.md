# Running a server

Installing Wormhole X-Treme, configuring it, and deciding who may do what. Each way to travel
has its own page: [gates](GATES.md), [rings](RINGS.md), [beaming](BEAMS.md) and
[mirrors](MIRRORS.md).

## Contents

- [Installing](#installing)
- [Compatibility](#compatibility)
- [Configuration](#configuration)
- [Permissions](#permissions)
- [Commands](#commands)
- [Sounds](#sounds)
- [Storage](#storage)
- [Coming from another Wormhole X-Treme](#coming-from-another-wormhole-x-treme)
- [Economy](#economy)
- [Troubleshooting](#troubleshooting)

## Installing

Drop `WormholeXTreme-<version>.jar` from the
[latest release](https://github.com/khanjal/Wormhole-X-Treme/releases/latest) into `plugins/`
and start the server. Nothing else is needed: the plugin has no runtime dependencies, and
SnakeYAML comes from the server itself.

LuckPerms and Vault are both optional. See [Permissions](#permissions) and [Economy](#economy).

## Compatibility

**Minecraft 1.20 through 1.21.10.** Both ends are measured rather than assumed.

| Minecraft | In CI | Note |
|---|---|---|
| 1.20 | yes | Floor — `CALIBRATED_SCULK_SENSOR` arrives here, and gate detection needs it |
| 1.20.1 | yes | A commonly pinned version |
| 1.20.4 | yes | Compile target |
| 1.20.6 | yes | |
| 1.21.1 | yes | |
| 1.21.4 | yes | Boats split into one entity type per wood here |
| 1.21.10 | yes | Newest published API |

Versions between those points are expected to work and are not separately built; the matrix
covers the boundaries where the API actually moved.

**None of this has been runtime-verified on a live server.** CI proves the plugin compiles and
its tests pass against each API — not that a gate behaves correctly in game.

| Server | Support |
|---|---|
| Spigot | Primary target — the API compiled against |
| CraftBukkit, Paper | Supported |
| Purpur, Pufferfish | Best effort |
| Folia | Not supported — different scheduler model |

The jar is Java 17 bytecode. **Minecraft 1.20.5 and later need the server on Java 21** — that is
the server's requirement, not this plugin's.

One Spigot API move is worth knowing about: `EntityDismountEvent` changed package at 1.20.4.
The plugin carries a listener for each and registers whichever the server can load. A server
with neither loses the ability to stop a rider dismounting mid-transit, and says so in the log.
How the version range is built and tested is in [DEVELOPMENT.md](../DEVELOPMENT.md#minecraft-versions).

## Configuration

On first run the plugin creates `plugins/WormholeXTreme/config.yml`. When an update adds
settings, the missing keys are appended to your file with their defaults and descriptions;
existing values are never overwritten.

**Change settings in-game** with `/wormhole config`:

```
/wormhole config <setting>            show it, with its description
/wormhole config <setting> <value>    change it — no reload, no restart
/wormhole config sign                 search: every setting with "sign" in its name
```

Either spelling works: `gate-sound-kawoosh`, as `config.yml` has it, or `gate_sound_kawoosh`, as
tab completion shows it. A setting with a fixed set of values refuses a bad one and names the
options. Sound names are not checked, because a resource pack's sounds have to pass through.

**Editing `config.yml` while the server is running does not work.** The plugin writes the file
back from memory when it shuts down, so an edit made underneath it is overwritten. Use the
command, or edit the file with the server stopped.

### Keeping gates from staying open

`shutdown_timeout` closes a wormhole a set time after it is dialled, and dialling restarts that
timer. `max-open-seconds` (default 300, `0` to disable) caps the total time a wormhole may stay
open, measured from when it first formed and **not** reset by re-dialling.

That cap matters for anything that re-dials on a schedule, and for `shutdown_timeout: 0`, which
means "stay open until something goes through" and could otherwise leave a gate open forever.

### What this costs a busy server

One repeating task per subsystem and no background threads. Cost scales with how much
travelling is happening, not with how much has been built: three thousand gates cost the same
per tick as three, as long as the same number are open.

- **Entity sweep** — every `entity-scan-interval-ticks` (default 20), one entity query per
  *open* gate. Raise it if many wormholes are open at once.
- **Projectile tracking** — per tick, but only for projectiles in flight while a wormhole is
  open, and only for a couple of seconds after they are fired.
- **Ambient hum** — every `gate-sound-ambient-ticks`, one sound per open gate.
  `gate-sounds-enabled: false` removes it.

Everything else is event-driven and answered by a hash lookup on the block position — including
`BlockPhysicsEvent`, which fires for every water flow and redstone update. A world with no gates
stops at the first lookup.

## Permissions

Permissions go through Bukkit's `player.hasPermission()`, so any permission plugin works —
LuckPerms, a Vault-bridged provider, or plain `ops.json`. Vault is not needed for permissions.

**An operator may do anything with a gate**, with or without a permissions plugin, and that
outranks a negated node.

**Gates**

| Node | Default | Allows |
|---|---|---|
| `wormhole.use.sign` | false | Using sign dialers |
| `wormhole.use.dialer` | false | Dialling a gate |
| `wormhole.use.compass` | false | `/wormhole compass` |
| `wormhole.list` | false | `/wormhole gate list` |
| `wormhole.go` | false | `/wormhole go` |
| `wormhole.build` | op | Building gates |
| `wormhole.remove.own` | false | Removing gates you own |
| `wormhole.remove.all` | op | Removing any gate |
| `wormhole.config` | op | Settings, and managing any gate: `edit`, `regenerate`, `validate`, `import`, ownership. Also every `mirror` command. |
| `wormhole.network.use.<network>` | | Using gates on that network |
| `wormhole.network.build.<network>` | | Building gates on that network |

**Rings**

| Node | Default | Allows |
|---|---|---|
| `wormhole.ring.build` | op | Creating and pairing rings |
| `wormhole.ring.use` | true | Travelling by a ring you are allowed on |
| `wormhole.ring.admin` | op | Using and managing any pair |
| `wormhole.ring.unlimited` | op | Owning more pairs than the quota |

Being on a private pair's allow list lets somebody travel by it, not recolour, rename, give away
or delete it.

**Beaming**

| Node | Default | Allows |
|---|---|---|
| `wormhole.beam.use` | true | `beam to`, `beam list` |
| `wormhole.beam.place` | true | Your own private places |
| `wormhole.beam.admin` | op | Public destinations and anyone's places; bypasses beam cooldown and cost |
| `wormhole.beam.admin.teleport` | op | `beam admin goto` and `send`. Not implied by `beam.admin`: curating destinations and relocating players are different powers. |

Worth knowing:

- `beam`, `ring`, `go`, `list` and `compass` answer to their own nodes. Everything else under
  `/wormhole` needs `wormhole.config`.
- Nobody may build inside a gate's opening except those who could take the gate apart: operators,
  its owner, and holders of `wormhole.config` or `wormhole.remove.all`. `wormhole.build` does not
  carry it. A block left there is not part of the gate, so anyone can break it back out.
- One use cooldown applies to everyone: `use-cooldown-seconds`, switched on by
  `use-cooldown-enabled`.
- With the `Help` plugin present, the nodes are registered with it.

### Permission backend and fallback

- `permissions-support-disable` (default `false`) — never attach to an external permission
  provider, even if one is present.
- `permissions-auto-fallback` (default `true`) — if no provider is found at startup, basic use
  actions keep working and advanced ones stay with operators and gate owners. Set `false` to
  leave permission handling entirely to you.

## Commands

Everything is a subcommand of `/wormhole` (alias `/wx`). Run it with no arguments for the list;
tab completion fills in subcommands, gate names, networks and values.

| Group | Covered in |
|---|---|
| `gate …` | [Gate commands](GATES.md#commands) |
| `ring …` | [Building](RINGS.md#building-a-ring-pair) and [ring settings](RINGS.md#ring-settings) |
| `beam …` | [Beam commands](BEAMS.md#commands) |
| `mirror …` | [Mirror commands](MIRRORS.md#commands) |
| `config …` | [Configuration](#configuration) |
| `compass` | Points your compass at the nearest gate; `compass reset` points it back at spawn |

`compass` works without a compass in hand — the heading is stored against you. It says so when
nothing you carry will show it: a lodestone compass points at its lodestone, a recovery compass
at where you died.

`/dial <gate> [idc]` finishes dialling a gate that has no dial sign. See
[Dialling](GATES.md#dialling).

<details>
<summary>The old flat commands still work</summary>

`list`, `build`, `complete`, `remove`, `regenerate`, `refresh`, `go`, `force`, `owner`, `idc`,
`redstone`, `custom`, `portalmaterial`, `irismaterial`, `lightmaterial`, `wooshdepth`,
`shutdown_timeout`, `activate_timeout`, `cooldown` and `restrict` all still dispatch, so
command blocks and scripts keep working. They are just no longer listed.

`/wormhole cooldown` takes a number of seconds. `/wormhole restrict` reports that build
restriction was removed; building is governed by `wormhole.build`.

</details>

## Sounds

Gates, rings and beams make noise, all configured the same way. Tables are on each page:
[gates](GATES.md#sounds), [rings](RINGS.md#sounds), [beaming](BEAMS.md#sounds).

- **Each subsystem has its own switch and volume** — `gate-sounds-enabled`,
  `ring-sounds-enabled`, `beam-sounds-enabled` — and any single sound set to `none` goes quiet.
- **Sounds are named, not picked from a list.** Anything the client knows works, including a
  resource pack's own sounds. An unrecognised name is silent, so a typo costs that sound and
  nothing else.
- **Volume is range.** `1.0` carries about sixteen blocks, `1.5` about twenty-four.

| Instead of | Try | For |
|---|---|---|
| `gate-sound-kawoosh` | `item.trident.riptide_3` | A longer rush instead of a single burst |
| `gate-sound-chevron` | `block.piston.contract` | A heavier clunk |
| `gate-sound-ambient` | `block.conduit.ambient` | A resonant hum instead of open water |
| `ring-sound-ring` | `block.amethyst_block.chime` | Crystalline rather than mechanical |

## Storage

```
plugins/WormholeXTreme/
├── config.yml
├── shapes/gate/*.shape
├── shapes/mirror/*.mirror
└── data/
    ├── gates/<name>.yml
    ├── rings/<world>.yml
    ├── beam.yml
    └── mirror.yml
```

Back up by copying `data/`; edit anything in it by hand if you need to. There is no database to
install or configure. Gates are one file each; rings are one file per world, and a pair that
will not parse is logged and skipped while the rest of the world loads.

Older builds kept these files in `WormholeXTremeDB/`. They are moved into `data/` on the first
startup after upgrading, and nothing is deleted.

Earlier versions also offered HSQLDB and SQLite backends; both are gone. If you are coming from
an install that used one, migrate with a build from before their removal, or rebuild the gates.

## Coming from another Wormhole X-Treme

Every build descended from the 2011 original kept its gates in
`WormholeXTremeDB/WormholeXTreme.sqlite`. This fork uses a file per gate, so swapping the jar
leaves a server full of gates the plugin cannot see.

**`/wormhole gate import`** brings them across and reports what came and what did not. A gate is
skipped rather than guessed at if its world is not loaded or its name is taken. Nothing is
written to the old database, so a failed import costs nothing and running it twice duplicates
nothing. If old gates are found on startup and you have none, the log says so once.

`WormholeXTremeDB/` keeps its name because that is how the import finds the database.

Your server needs a SQLite driver for this. It is not shipped — thirteen megabytes for a one-time
import — but any server that *wrote* one of these databases already has one.

## Economy

Optional. Needs [Vault](https://www.spigotmc.org/resources/vault.34315/) and an economy plugin
such as [EssentialsX](https://essentialsx.net/).

| Setting | Default | What it does |
|---|---|---|
| `economy-enabled` | `false` | Nothing is ever charged while this is off. |
| `economy-use-cost` | `0.0` | Charged each time a player walks through a gate. |
| `economy-build-cost` | `0.0` | Charged when a player completes a gate. |

- No Vault or no economy provider means charges are skipped.
- A player who cannot afford the use cost is stopped and told.
- A player who cannot afford the build cost still gets the gate, is told, and is not charged.

Beaming has its own cost settings; see [Beam settings](BEAMS.md#settings).

## Troubleshooting

- **Gates gone after a restart** — check `plugins/WormholeXTreme/data/gates/` for their files,
  and the log for storage errors.
- **A `?` where an accented character was** in a gate name, owner or iris code — a pre-1.5.0 bug
  on servers whose locale is not UTF-8. 1.5.0 writes UTF-8 everywhere but cannot recover a
  character that never reached the file: rebuild the gate under the right name, or set the iris
  code again.
- **A gate that stopped responding after WorldEdit** — `/wormhole gate validate <gate|-all>`
  reports what is missing. See [Gate commands](GATES.md#commands).
