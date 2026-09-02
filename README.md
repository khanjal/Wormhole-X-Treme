# Wormhole X-Treme

Wormhole X-Treme is a Bukkit/Spigot/Paper plugin that provides Stargate-style teleportation portals.
Gates are fully configurable per shape — materials, iris, lighting, and sign type are all set in `.shape` files.
This branch targets Java 17 and the Bukkit 1.20 API.

## Server Compatibility

### Compatibility Matrix

| Runtime Server | Base/API lineage | Support tier | Notes |
|---|---|---|---|
| CraftBukkit 1.20.4 | Bukkit | Supported | Baseline Bukkit/Spigot API behavior |
| Spigot 1.20.4 | Spigot (Bukkit+) | Primary target | **Compile target** (`spigot-api`) |
| Paper 1.20.4 | Paper (Spigot+) | Supported | Verified runtime target |
| Purpur / Pufferfish | Paper fork | Best effort | Usually compatible with Paper behavior |
| Folia | Paper fork (region scheduler) | Not supported | Different scheduler/threading model |

The plugin is compiled against the Spigot API (`spigot-api 1.20.4`) as a `provided` dependency.

Why Spigot over Bukkit for build target:
- Bukkit is the conceptual base and broadest API lineage.
- Spigot is the practical widest deployment target while remaining close to Bukkit API.
- Building against Spigot gives broad compatibility across Spigot and most Paper-based servers without tying the plugin to Paper-only APIs.

Java support policy:
- Java 17 and 21: officially supported
- Java 25: best-effort (CI coverage)

Build policy recommendation:
- Compile against Spigot only.
- Run CI tests on Java 17/21/25.
- Optionally add runtime smoke tests on Spigot and Paper server jars if you want explicit per-server verification.

## Build

Requirements:
- JDK 17
- Maven 3.6+

Build (skips tests):

```bash
mvn -DskipTests package
```

Output jar: `target/WormholeXTreme-1.0.0.jar` (~300KB).

Nothing is bundled into the jar and the plugin has no runtime dependencies. SnakeYAML
comes from the server — Spigot declares it and Bukkit's own config system uses it.

## Configuration

On first run the plugin creates `plugins/WormholeXTreme/config.yml`. If you update to a newer version that adds new config keys, **missing keys are automatically appended** to the bottom of your existing `config.yml` with their defaults and description comments — existing values are never overwritten.

Important keys (kebab-case in `config.yml`):

### Keeping gates from staying open

`shutdown_timeout` closes a wormhole a set time after it is dialled, and dialling restarts
that timer. `max-open-seconds` (default 300, 0 to disable) is a ceiling on the total time a
wormhole may stay open, measured from when it first formed and **not** reset by re-dialling.

It matters in two cases: anything that re-dials on a schedule, and `shutdown_timeout: 0`,
which means "stay open until something goes through" and can otherwise leave a gate open
indefinitely.

Gates are stored as one YAML file each under `plugins/WormholeXTreme/.../gates`. There is
no database to configure.

Permissions go through Bukkit's `player.hasPermission()`, so any permission plugin works —
LuckPerms, a Vault-bridged provider, or the server's own `ops.json`. There is no separate
permission system to configure, and Vault is not required for permissions (only for economy,
which is optional and detected at runtime).

**An operator may do anything with a gate**, with or without a permissions plugin. This
deliberately outranks a negated node: on a server where someone has been given op, that is
taken as the final word.

## Commands

Everything is a subcommand of `/wormhole` (aliased `/wx`). Run it with no arguments for the
list; tab completion offers subcommand names, then gate names, networks, backends or
booleans depending on where you are in the line.

**Gates** — `list [network]`, `build <shape>`, `complete <name> [idc=IDC] [net=NET]`,
`remove <gate>`, `regenerate <gate>` (alias `regen`), `refresh`

**Travel** — `go <gate>`, `compass`, `force <gate>`

**Per gate** — `owner <gate> [player]`, `idc <gate> [code]`, `redstone <gate> [true|false]`,
`custom <gate> [true|false]`, `portalmaterial`, `irismaterial`, `lightmaterial`,
`wooshdepth`

**Server** — `shutdown_timeout <seconds>` (alias `timeout`),
`activate_timeout <seconds>`, `cooldown <one|two|three|true|false> [time]`,
`restrict <true|false>`

### Clearing snapshotted material overrides

Older versions of `/wormhole custom <gate> true` copied the shape's materials into the
gate's own override fields. Those copies pin a gate to the materials that were current at
the time and stop it following its [material group](#material-groups).

`/wormhole custom -clean` reports which gates are affected. `/wormhole custom -clean confirm`
clears them, after which those gates follow their palette again.

Only a gate whose *whole set* of four overrides matches the built-in defaults is treated as
a snapshot — someone who deliberately set an iris to stone meant stone, and a coincidental
match on one material is not evidence of anything. Gates you genuinely customised are left
alone.

## Storage

Gates are stored as one YAML file each, under
`plugins/WormholeXTreme/WormholeXTremeDB/gates/`. Back them up by copying the folder; edit
them by hand if you need to.

There is no database backend and nothing to configure. Earlier versions offered HSQLDB and
SQLite; both are gone. A few thousand small records read once at startup and written one at
a time gains nothing from a database engine, and the drivers were over 95% of the plugin's
download size. If you are coming from an install that used one of them, migrate with a
build from before their removal, or rebuild the gates.

## Shapes

Gate shapes live under:

- `plugins/WormholeXTreme/GateShapes/3d/` (3D shapes)
- `plugins/WormholeXTreme/GateShapes/2d/` (2D shapes)

Default shapes are extracted from the jar on first run only — they will **not** overwrite user-customized files.

### Shape material parameters

Shapes describe geometry. Appearance comes from the gate's [material group](#material-groups),
chosen by the material the frame is actually built from — so the shipped shapes set no
materials at all, and one shape file can be built as a Standard, Atlantis or Universe gate.

The keys below still exist for a shape that genuinely must look a particular way whatever
palette it resolves to. An explicit value outranks the material group, so setting one opts
that material out of palettes entirely; leave it unset unless the geometry needs it.

| Key | Default | Description |
|---|---|---|
| `STARGATE_MATERIAL=` | `OBSIDIAN` | The block type used for the gate frame (`[S]` blocks). |
| `PORTAL_MATERIAL=` | `WATER` | The block type filling the open portal (`[P]` blocks when active). |
| `IRIS_MATERIAL=` | `STONE` | The block type filling the portal when the iris is closed. |
| `ACTIVE_MATERIAL=` | `GLOWSTONE` | The block type used for light blocks (`:L` markers) when the gate is active. |
| `SIGN_MATERIAL=` | `OAK_WALL_SIGN` | The wall-sign type used for the gate name sign and the dial sign. Any `*_WALL_SIGN` material is valid (e.g. `CRIMSON_WALL_SIGN`, `WARPED_WALL_SIGN`). |

Example — a Nether-themed gate using crimson materials:

```
STARGATE_MATERIAL=BLACKSTONE
PORTAL_MATERIAL=LAVA
IRIS_MATERIAL=NETHERRACK
ACTIVE_MATERIAL=SHROOMLIGHT
SIGN_MATERIAL=CRIMSON_WALL_SIGN
```

### Creating a new shape

1. Copy an existing `.shape` file as a starting point.
2. Edit the block grid and material keys. Keep the filename unique with the `.shape` extension.
3. Place it in `plugins/WormholeXTreme/GateShapes/3d/` (or `2d/`) and restart the server (or trigger `StargateHelper.loadShapes()`).
4. Use `/wormhole custom <gate> true` to assign the shape to a gate if needed.

## Nether and End dimension support

Gates work correctly in the Nether and End. In those dimensions Minecraft uses `CAVE_AIR` (Nether) and `VOID_AIR` (End) for empty space instead of the normal `AIR` used in the Overworld. All portal-detection and teleport-exit-position searches use `Material.isAir()`, which covers all three air types, so gates build and activate correctly regardless of which dimension they are placed in.

## DHD (dial-home device) — button and lever support

The DHD block that a player clicks to activate a gate can be any button type or a lever. All of the following are recognised:

- All wood buttons: `OAK_BUTTON`, `SPRUCE_BUTTON`, `BIRCH_BUTTON`, `JUNGLE_BUTTON`, `ACACIA_BUTTON`, `DARK_OAK_BUTTON`, `MANGROVE_BUTTON`, `CHERRY_BUTTON`, `BAMBOO_BUTTON`
- Nether buttons: `CRIMSON_BUTTON`, `WARPED_BUTTON`
- Stone buttons: `STONE_BUTTON`, `POLISHED_BLACKSTONE_BUTTON`
- `LEVER`

When a gate is activated via a button, the button is automatically replaced with a lever so the gate can be held open. Shutting down the gate restores a lever in its place.

## Iris (gate shield) setup and troubleshooting

An iris closes over the portal to block travel. When a remote gate's iris is active, players who walk into the portal are bounced back with the message "Remote Iris is locked!".

### Setup

- Build a gate from a shape that includes an `:IA` marker (most 3D shapes do; see `GateShapes/3d/Standard.shape`).
- Set an IDC (iris deactivation code) to allow callers to unlock the iris remotely:
  - `/wormhole complete <GateName> idc=<code>` — set IDC while completing.
  - `/wormhole idc <GateName> <code>` — set or change the IDC later.
  - `/wormhole idc <GateName> -clear` — remove the IDC.
- The plugin places an iris activation lever at the `:IA` block position when the gate is built.

### Common issue: clicking the iris lever activates the gate instead

- Cause: older logic treated any adjacent block as the same control; an iris lever next to the dial lever could be misclassified.
- Fix applied in this branch: the click handler now matches the exact lever block against the gate's stored `IrisLever` and `DialLever` positions, eliminating the misclassification.
- If you still see unexpected behavior, check the server log for the gate's lever positions or use `/wormhole list` to inspect gate state.

## Material groups

A gate's **shape** is its geometry; its **material group** is what that geometry is built
from. The two are independent, so one shape file can be built in any palette.

Groups are defined in `config.yml` under `gate-material-groups`. The first listed is the
default:

```yaml
gate-material-groups:
  Standard:
    structure: OBSIDIAN
    portal: WATER
    iris: STONE
    light: GLOWSTONE
  Atlantis:
    structure: LAPIS_BLOCK
    portal: WATER
    iris: YELLOW_STAINED_GLASS
    light: SEA_LANTERN
    sign: WARPED_WALL_SIGN
```

`sign` sets the wall-sign type used for the gate's name sign — any `*_WALL_SIGN`
material. The dial sign is placed by the player, so its type is whatever they used.

A gate's palette is identified by the material of its **frame**, so every group must use a
different `structure` material — build the Standard shape in obsidian and you get a
Standard gate, build the same shape in lapis and you get an Atlantis one. A group that
reuses a frame material already claimed by another is rejected at load with a warning,
since it would make detection ambiguous.

Material resolution order for any gate is:

1. An explicit per-gate override (`/wormhole portalmaterial` and friends).
2. A material named outright in the shape file. `Horizontal.shape` asks for a `GLASS`
   iris because a horizontal gate is meant to be seen through, and the palette does not
   overrule that.
3. The gate's material group, for anything the shape left unsaid.
4. The built-in defaults, for a shape with no palette match at all.

A shape may also restrict which palettes it accepts with `MATERIAL_GROUPS=Standard,Atlantis`
in the `.shape` file; with no such line, every group is accepted.

### Shapes whose materials are not in any group

Nothing breaks. A shape framed in a material no group declares — a custom blackstone gate,
say — is still detected, and gates built from it keep the materials named in their own
`.shape` file. The plugin logs those materials once at startup so you know a
`gate-material-groups` entry would let you reuse that palette across other shapes.

When a shape's palette is unambiguous, the plugin adds it to `config.yml` for you, the
same way missing scalar keys are appended. A lone diamond gate with gold chevrons becomes:

```yaml
  # Added automatically from a gate shape using this frame material.
  Diamond:
    structure: DIAMOND_BLOCK
    portal: WATER
    iris: GLASS
    light: GOLD_BLOCK
```

It takes effect immediately, not just after the next restart, and you can then apply that
palette to any other geometry.

"Unambiguous" is doing real work there. A group is identified by its frame material, so a
frame material can name exactly one palette — and shapes do not necessarily agree. The
stock shapes are the illustration: all seven are framed in obsidian but ask for three
different irises (`GLASS`, `STONE`, `BEDROCK`). There is no single obsidian palette to
derive, so none is offered and the plugin says so in the log. Guessing one would silently
restyle whichever shapes lost the vote.

Set `gate-material-groups-autodiscover: false` to curate the list by hand. With it off,
discovered palettes are only logged — and a group you delete stays deleted instead of
reappearing on the next restart.

### Why this is not just a config convenience

Gate detection scans every registered shape in turn, running a full geometry-and-material
check on each. Before material groups, a palette variant meant a whole extra `.shape`
file — `StandardAtlantis.shape` was `Standard.shape` with four lines changed — so each
palette added a complete extra scan to every detection attempt.

That matters more than it looks, because detection is not rare. Right-clicking a
directional block that is not a gate falls back to probing 26 surrounding blocks against 6
faces, which is 156 detection calls for one click. At nine shapes that is over 1,400 full
geometry scans; each extra palette-as-shape would add another 156.

With groups, the frame material is read from the world once and looked up in a map, so
palettes cost nothing per detection. A server can offer twenty palettes and detection is
exactly as fast as with one.

The `StandardAtlantis.shape` and `StandardUniverse.shape` files have been removed: they
were `Standard.shape` with different materials, which is exactly what a palette is now.
Build `Standard.shape` in lapis for an Atlantis gate or polished blackstone for a Universe
one.

## What travels through a gate

| | How it travels |
|---|---|
| Players | Their own move event |
| Minecarts, boats | `VehicleMoveEvent`, with passengers re-seated on arrival |
| Ridden animals (horse, camel, pig, donkey, llama, strider) | The rider's move event; the animal goes first and the rider is re-seated |
| Arrows, tridents, snowballs, ender pearls, potions, fireballs | Followed from launch, crossing the tick they reach a portal |
| Mobs, animals, dropped items, XP orbs, armour stands | A periodic sweep of open gates |
| Item frames, paintings | Never — they hang on a block and stay put |

So yes — a zombie or skeleton that wanders into an open wormhole comes out the other
side, as does a dropped item or a wandering cow. The sweep runs every
`entity-scan-interval-ticks` (default 20, i.e. once a second) and only looks at gates that
are currently open.

Because it polls rather than reacting to an event, a fast-moving entity can cross the
portal between two sweeps and carry on through without travelling. Dropped items usually
come to rest in the ring and get picked up on the next pass, but arrows and similar are
hit-and-miss by nature. Lower the interval if you want it caught more reliably, at the cost
of more frequent scanning.

Projectiles are handled differently from everything else. An arrow cannot be moved through
a gate: teleporting one leaves it flagged as having landed, so it arrives at the far end
already stuck and drops out of the air. Instead the original is consumed at the source and
an identical one is fired out of the destination gate — same speed, same shooter, and for
arrows the same damage, crit, knockback, pierce and pickup rules, so a kill through a gate
is still credited correctly. Splash potions keep their effect.

This covers every projectile: arrows, tridents, snowballs, eggs, ender pearls, potions and
fireballs.

Projectiles are not found by the periodic sweep at all. Portal blocks are air, so an arrow
passes through the ring and keeps going. Each one is instead followed individually from the
moment it is launched, and every tick the plugin checks the *path* it travelled since the
last tick rather than where it currently is — a drawn bow moves an arrow about three blocks
per tick and a portal is one block thick, so checking its position alone steps straight over
the gate. Cost scales with how many projectiles are in flight, not with how many gates
exist.

If one does arrive already stopped, it is relaunched at a bow's speed rather than trickling
out of the destination.

One consequence is worth knowing: an ender pearl thrown through a gate teleports its owner
to wherever it lands — across the wormhole — which sidesteps the permission and cooldown
checks a player walking through would face.

Anything riding something else travels with its carrier rather than separately, and
anything that just came through is ignored for a moment so it is not bounced straight back.

### A wormhole runs one way

Dialling leaves the origin gate holding a target and the destination gate holding none,
and every path that moves something through a gate keys off having a target. The
destination end is therefore an exit, not an entrance: nothing travels back up an open
wormhole, and a player who walks into the destination ring is pushed back out rather than
sent anywhere.

That means a gate dialled out of your base is not a door mobs can wander in through. Things
standing in *your* gate are sent to the far end, never the reverse. Gates do not filter by
mob type, so a creeper in your own gate room will happily be sent along with you — but
nothing arrives from the other side on its own.

## Redstone activation

Two redstone activation modes are supported and controlled by blocks registered to the gate at build time.

### Redstone sign dial (sign gate)

A gate with a redstone sign dial cycles through available network targets via a redstone pulse on the `gateRedstoneSignActivationBlock`. Each pulse advances the dial sign to the next target. A sustained high signal does not repeatedly cycle — only transitions trigger a step.

### Redstone direct dial

A gate can be activated directly by a redstone signal on the `gateRedstoneDialActivationBlock`. When the signal goes high the gate dials its current sign target; when the signal drops the gate shuts down (if `shutdown_timeout` is `0`). Enable this mode per gate with `/wormhole redstone <gate> true`.

Both modes work via `BlockRedstoneEvent` and are fully compatible with all Bukkit-based servers.

A signal counts when it lands **on** the activation block or on any redstone component **touching** it — dust, a repeater, a comparator, a lever, a button, a pressure plate, an observer, a redstone block or torch, or a detector/powered/activator rail. You do not have to place dust exactly on the activation block.

### Building a redstone gate

A redstone gate is a **sign gate with redstone inputs**. Redstone does not choose a
destination — the dial sign does, exactly as if a player were clicking it. Redstone just
presses the buttons. A shape without a `[D]` dial sign block cannot be redstone-dialled.

Two shapes take redstone: `MinimalSignDialRedstone` and `StandardSignDial`. The minimal one
is covered first because it carries the full set of markers; if you already have a Standard
gate built, skip to [Redstone on a Standard gate](#redstone-on-a-standard-gate).

`MinimalSignDialRedstone.shape` is not laid out like `MinimalSignDial` — it is a block
taller and the pillars are further apart — so building the plain sign-dial gate will not
give you a redstone one.

```
Layer 1 — the ring              Layer 2 — behind it, the DHD side
   y=5   .  .  .                   y=5   .  .  .
   y=4   .  ~  .                   y=4   A  .  D
   y=3   .  ~  .                   y=3   #  .  #
   y=2   .  e  .                   y=2   R  m  C
   y=1   .  V  .                   y=1   #  .  #
   y=0   .  #  .                   y=0   .  .  .

   #  gate frame block            ~  portal, leave empty
   .  leave empty                 e  player exit    m  minecart exit
   A  activation lever or button  D  dial sign
   R  [RD]  dial          -> redstone dust
   C  [RS]  next target   -> redstone dust
   V  [RA]  gate is open  -> lever
```

`R`, `C` and `V` are not frame blocks — do not build gate material at them. Each sits
directly on top of a frame block, which is why the shape file says they belong "on top of a
[S] block".

The two pillars are mirror images, so if `R` and `C` end up swapped the gate will cycle
targets when you meant to dial. Swap the dust to the other side if that happens.

Put the dial sign up and pick a destination before testing: `[RD]` dials whatever the sign
is showing, so with no target selected a pulse does nothing.

A signal counts when it lands on the marked block **or on any redstone component touching
it**, so you can run dust up to it rather than having to land exactly on the cell.

### Redstone on a Standard gate

`StandardSignDial` also takes redstone, on the DHD side (layer 4), next to the activation
block and dial sign:

```
   y=2   .  .  R          #  gate frame block   .  leave empty
   y=1   .  A  D          A  activation block   D  dial sign holder
   y=0   V  #  #          R  [RD]  dial       -> redstone dust
                          V  [RA]  gate open  -> lever
```

`R` goes on top of the activation block; `V` hangs on the side of the pillar below it.
Neither is a frame block, so an existing Standard gate does not need rebuilding — place the
dust and the lever and run `/wormhole redstone <gate> true`.

There is no `[RS]` cycle block on this shape. The only free block top left is the one right
beside `[RD]`, and a signal counts anywhere within a block of a marker, so a single pulse
would cycle the destination and then dial whatever it landed on. Use
`MinimalSignDialRedstone` if you want redstone target cycling as well as redstone dialling;
on a Standard gate, click the sign to choose the destination and let redstone do the
dialling.

`[RA]` is deliberately two blocks below `[RD]` rather than beside it. The plugin switches
that lever on itself when the gate opens, so keeping it out of range stops the gate's own
output from feeding back into its dial input.

### Driving a gate with a minecart

Run the track past the gate and put a **detector rail** in the line, then wire it to the
`[RD]` block. A cart rolling over the detector rail dials whatever the dial sign is
currently showing, so the cart can ride straight through.

Use a detector rail, not a powered rail. A powered rail is already energised by whatever is
switching it, so a cart passing over it changes nothing and produces no event. A detector
rail emits a pulse only while a cart is on it, which is exactly the trigger you want.

A trigger on an already-open gate does nothing at all — it neither closes the gate nor
re-dials it. Closing was the old behaviour and made repeated triggers useless: a second cart
shut the wormhole the first one had opened. Re-dialling is not the answer either, because
dialling restarts the shutdown timer, so a cart every few seconds would hold the gate open
and lock everyone else out. Leaving it alone means the gate always closes on its own timer,
however often it is triggered.

A trigger on a gate that is lit but never dialled still deactivates it, which is the only
way to clear a gate somebody activated and walked away from.

## Events for other plugins

Gate lifecycle is published as Bukkit events, so another plugin can react without this one
knowing it exists. Both live in `com.wormhole_xtreme.wormhole.events`.

| Event | Fired |
| --- | --- |
| `StargateCreatedEvent` | after a gate is built, named, registered and saved |
| `StargateRemovedEvent` | while a gate is being removed, before it is torn down |
| `StargatePlayerTravelEvent` | before a player travels, and **cancellable** |

```java
@EventHandler
public void onGateCreated(final StargateCreatedEvent event)
{
    getLogger().info(event.getStargateName() + " built by "
        + (event.getBuilder() != null ? event.getBuilder().getName() : "no player"));
}
```

`getStargate()` gives the gate itself. `getBuilder()` and `getRemover()` give the player
responsible, and are **null** when the gate was not created or removed by one — check before
using them.

The removal event fires *before* teardown, so the gate can still be read: name, owner,
network, blocks and teleport location are all still populated, which is what a listener
cleaning up its own records needs.

The lifecycle events are not cancellable. Both are sent after the decision has been made
and, for creation, after the gate is already on disk. To prevent a gate being built, deny
`wormhole.build` rather than listening for it.

### Watching and stopping travel

`StargatePlayerTravelEvent` fires once every check this plugin makes has passed — permission,
iris code, cooldown, one-way, same-world — and before anything has moved. `getStargate()` is
the gate being entered, `getDestination()` is where it leads, and `getArrival()` is the exact
spot the player would land.

```java
@EventHandler
public void onTravel(final StargatePlayerTravelEvent event)
{
    if (inCombat(event.getPlayer()))
    {
        event.setCancelled(true);
    }
}
```

It fires for a player on foot and for one riding anything — a horse, a minecart, a boat. It
does not fire for the vehicle itself, nor for anything travelling on its own, so cancelling
stops the player rather than the world around them.

A cancelled traveller is held, not moved. If they were walking in they are kept out; if they
were already standing in the portal they stay free to walk away. Refusing every move of
someone already inside would leave them unable to leave the ring at all.

A listener that throws does not stop travel. Another plugin failing is not a decision to
strand somebody halfway into a wormhole.

Refreshing a gate does **not** raise a removal. A refresh deregisters the gate and registers
it again with freshly detected geometry, which is not the gate going away, so a listener is
not told to discard what it knows about it.

## Developer notes

- `LegacyCompat` utility class provides `isWallSign(Material)` and `isButton(Material)` helpers that cover all current wood, stone, and Nether variants so that detection code does not need explicit per-type checks.
- All air-type checks use `Material.isAir()` (covers `AIR`, `CAVE_AIR`, `VOID_AIR`) rather than a direct `== Material.AIR` comparison.
- Sign material for each gate is read from the shape's `SIGN_MATERIAL=` key and stored on `StargateShape` / `Stargate3DShape`; placement and detection code reads from the shape object rather than hardcoding `OAK_WALL_SIGN`.
- `StargateYamlManager` handles per-gate YAML read/write.
- `StorageMigrator` provides a CLI-accessible migration tool for `db -> file`.

## Troubleshooting

- If gates disappear after restart: check for the per-gate YAML files under `plugins/WormholeXTreme/WormholeXTremeDB/gates/`.
- Check logs for storage initialization errors; increased logging was added for storage backend diagnostics.

## Economy

Economy integration is optional and requires **[Vault](https://www.spigotmc.org/resources/vault.34315/)** and an economy provider plugin (e.g. [EssentialsX](https://essentialsx.net/), CMI, iConomy, etc.) to be installed.

| Config key | Default | Description |
|---|---|---|
| `economy-enabled` | `false` | Set `true` to enable all economy features. When `false`, no charges are ever applied. |
| `economy-use-cost` | `0.0` | Amount charged to a player each time they walk through a gate. Set `0.0` to disable. |
| `economy-build-cost` | `0.0` | Amount charged to a player when they successfully complete a new gate. Set `0.0` to disable. |

**Behaviour:**
- If Vault is absent or no economy provider is registered, all charges are silently skipped (fail-open).
- If the player cannot afford the use cost, they are blocked from teleporting and informed.
- If the player cannot afford the build cost, the gate is still built but they are notified — no charge is taken.
- Currency names are taken from the active economy plugin (singular/plural).

## Contributing

Submit PRs against the `main` branch. Keep changes modular and add unit/integration tests where possible.

## Permissions

The plugin uses permission nodes for feature access. Permissions are intended to be managed by a permissions plugin (Vault/LuckPerms recommended).

- `wormhole.use.sign` — allow using sign-based dialers and sign interactions.
- `wormhole.use.dialer` — allow using the dialer to initiate a gate dial.
- `wormhole.use.compass` — allow using the compass command to point to gates.
- `wormhole.remove.own` — allow removing gates you own.
- `wormhole.remove.all` — allow removing any gate (admin-level).
- `wormhole.build` — allow building gates using `/wormhole build`/`wxbuild` automation.
- `wormhole.config` — allow changing plugin configuration via commands.
- `wormhole.list` — allow listing gates via `/wormhole list`.
- `wormhole.go` — allow teleporting to gates via command (`/wormhole go`).
- `wormhole.network.use.<networkName>` — prefix for network-specific use rights (e.g. `wormhole.network.use.staff`).
- `wormhole.network.build.<networkName>` — prefix for network-specific build rights.

Notes:
- Per-group cooldown/build permission nodes (legacy `one`/`two`/`three`) have been removed; cooldowns are handled centrally when enabled in `config.yml`.
- The `HelpSupport` integration (attach to the external `Help` plugin) will register many of the above nodes with the help system when present.

### Permission backend & auto-fallback

The plugin prefers a Vault-compatible permissions provider (Vault + LuckPerms recommended). On first run the plugin will use the server's configured permission backend via the standard Bukkit `player.hasPermission(...)` API.

- `permissions-support-disable` (boolean): If `true`, the plugin will not attempt to attach to any external permission provider even if one is available. Default: `false`.
- `permissions-auto-fallback` (boolean): If `true` (default), and no Vault-compatible provider is detected at startup, the plugin will automatically enable a simple permission fallback mode so basic use actions continue to work; advanced actions remain restricted to operators or gate owners. Set this to `false` if you prefer to leave permission handling to server admins and not enable the fallback.

Behavior note:
- The `/wormhole go` command (teleport-to-gate) no longer grants access to all players by default. Use the `wormhole.go` permission node to grant command access to non-ops, or rely on operator/owner status. This ensures servers do not inadvertently expose teleport commands to all users when no permissions provider is present.

