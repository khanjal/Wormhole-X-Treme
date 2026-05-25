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

Output jar: `target/WormholeXTreme-1.0.0.jar`

## Configuration

On first run the plugin creates `plugins/WormholeXTreme/config.yml`. If you update to a newer version that adds new config keys, **missing keys are automatically appended** to the bottom of your existing `config.yml` with their defaults and description comments — existing values are never overwritten.

Important keys (kebab-case in `config.yml`):

- `storage-backend`: `file` (default), `sqlite`, `mysql`, `postgres`
- `storage-sqlite-path`: path to SQLite file when using `sqlite` backend.
- `storage-jdbc-url`, `storage-jdbc-user`, `storage-jdbc-password`: for JDBC backends.

Permissions are intended to be handled by Vault/LuckPerms. Legacy simple permissions were removed.

## Commands

All commands are run as `/wormhole <subcommand> ...` unless otherwise noted.

- `/wormhole list` — list available gates.
- `/wormhole custom [stargate|-all] <true|false>` — toggle custom-shape mode for a gate or all gates.
- `/wormhole cooldown [false|true|group] <time>` — manage use cooldowns (group names: `one`, `two`, `three`).
- `/wormhole activate` — (varies by server) activate gate; see plugin help output.

Storage commands (admin):
- `/wormhole storage backend <file|sqlite|mysql|postgres>` — set storage backend at runtime (use `config.yml` rewrite to persist).
- `/wormhole storage migrate <backend> [force]` — migrate gates to the specified backend. `force` overwrites existing YAML when migrating to file.

Other administrative commands (examples): `/wormhole owner`, `/wormhole custom`, `/wormhole irismaterial`, `/wormhole lightmaterial`, etc. Use plugin help (or `/help` with the HelpSupport plugin) for the full command list.

## Storage and Migration

- `file` backend uses per-gate YAML files stored in `plugins/WormholeXTreme/gates/` (one YAML per gate).
- `sqlite` backend stores gates in an SQLite DB file (path from config). A `SqliteStorage` scaffold is provided.
- Legacy HSQLDB containers are supported for migration as a read-only source; the plugin ships a migration adapter to read old `Stargates(Name, GateData)` rows and export them to a writable backend.

Migration notes:
- The built-in migrator supports reading from the currently-configured backend or explicitly from legacy HSQLDB. Use `/wormhole storage migrate hsqldb <dest>` to read directly from legacy `.properties/.script` HSQLDB files in the plugin data folder.
- Examples:
	- Migrate from HSQLDB to per-gate YAML files (non-destructive):

		/wormhole storage migrate hsqldb file

	- Migrate from HSQLDB to SQLite:

		/wormhole storage migrate hsqldb sqlite

	- Force overwrite existing YAML files when migrating to `file`:

		/wormhole storage migrate hsqldb file force

- Migration is non-destructive by default: existing YAML files are skipped unless `force` is used.
- The migrator opens legacy HSQLDB read-only and does not modify the original DB files; still, keep external backups before large migrations.

## Shapes

Gate shapes live under:

- `plugins/WormholeXTreme/GateShapes/3d/` (3D shapes)
- `plugins/WormholeXTreme/GateShapes/2d/` (2D shapes)

Default shapes are extracted from the jar on first run only — they will **not** overwrite user-customized files.

### Shape material parameters

All material parameters are optional; each falls back to a sensible default if omitted.

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

## Redstone activation

Two redstone activation modes are supported and controlled by blocks registered to the gate at build time.

### Redstone sign dial (sign gate)

A gate with a redstone sign dial cycles through available network targets via a redstone pulse on the `gateRedstoneSignActivationBlock`. Each pulse advances the dial sign to the next target. A sustained high signal does not repeatedly cycle — only transitions trigger a step.

### Redstone direct dial

A gate can be activated directly by a redstone signal on the `gateRedstoneDialActivationBlock`. When the signal goes high the gate dials its current sign target; when the signal drops the gate shuts down (if `shutdown_timeout` is `0`). Enable this mode per gate with `/wormhole redstone <gate> true`.

Both modes work via `BlockRedstoneEvent` on `REDSTONE_WIRE` and are fully compatible with all Bukkit-based servers.

## Developer notes

- `LegacyCompat` utility class provides `isWallSign(Material)` and `isButton(Material)` helpers that cover all current wood, stone, and Nether variants so that detection code does not need explicit per-type checks.
- All air-type checks use `Material.isAir()` (covers `AIR`, `CAVE_AIR`, `VOID_AIR`) rather than a direct `== Material.AIR` comparison.
- Sign material for each gate is read from the shape's `SIGN_MATERIAL=` key and stored on `StargateShape` / `Stargate3DShape`; placement and detection code reads from the shape object rather than hardcoding `OAK_WALL_SIGN`.
- Code now provides a `StorageBackend` interface and a `SqliteStorage` scaffold at `src/main/java/com/wormhole_xtreme/wormhole/storage/`.
- `StargateYamlManager` handles per-gate YAML read/write.
- `StorageMigrator` provides a CLI-accessible migration tool for `db -> file`.

## Troubleshooting

- If gates disappear after restart: check `plugins/WormholeXTreme/gates/` for YAML files or the configured DB file at `storage-sqlite-path`/JDBC URL.
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

