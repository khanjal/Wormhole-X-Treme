# Wormhole X-Treme

Wormhole X-Treme is a Bukkit/Paper plugin that provides Stargate-style teleportation gates.
This branch targets Java 17 and Paper 1.20.4 while keeping Spigot compatibility.

## Build

Requirements:
- JDK 17
- Maven 3.6+

Build (skips tests):

```bash
mvn -DskipTests package
```

Shaded artifact produced at: `target/WormholeXTreme-0.854-shaded.jar` (version may vary).

## Configuration

On first run the plugin will create `plugins/WormholeXTreme/config.yml` (migrated from legacy `Settings.txt` if present).

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
- Legacy HSQLDB support has been removed; use `sqlite` or `file` (YAML) or an external JDBC backend.

Migration notes:
- The built-in migrator supports `db -> file (yaml)` out of the box via `/wormhole storage migrate file`.
- Migration is non-destructive by default: existing YAML files are skipped unless `force` is used.
- The migrator creates simple backups by leaving original DB files intact; consider external backups before large migrations.

## Shapes

Gate shapes live under:

- `src/main/resources/GateShapes/3d/` (bundled default 3D shapes)
- `src/main/resources/GateShapes/2d/` (bundled default 2D shapes)

On plugin startup default shapes are restored to the plugin data folder only if missing — the plugin will NOT overwrite user-customized shapes.

Creating a new shape:
1. Copy an existing `.shape` file from `GateShapes/3d` or `GateShapes/2d` as a starting point.
2. Edit the file to change the ring blocks, iris material, and lighting. Shape files are simple text files with a custom format (see existing files for examples). Keep the filename unique and the `.shape` extension.
3. Place the new `.shape` file into `plugins/WormholeXTreme/GateShapes/3d/` (or `2d/`) on the server, or add it to `src/main/resources/GateShapes/...` if you want it bundled.
4. Restart the server or use the plugin reload path that triggers `StargateHelper.loadShapes()`.
5. Use `/wormhole custom <gate> true` and set the gate's shape name to the new shape if necessary.

Tips:
- Test shapes on a development server before deploying to production.
- Don't modify the shipped shapes under the plugin jar if you expect updates — put custom shapes in the plugin data folder.

## Iris (gate shield) setup and troubleshooting

Iris support is built into many shapes. An "iris" is a material you can close over the portal to block travel.

In-game setup:
- Build a gate from a shape that supports an iris (many 3D shapes include an `:IA` marker; see `GateShapes/3d/Standard.shape`).
- When you complete the gate, set an IDC (iris deactivation code) to enable iris functionality:
        - `/wormhole complete <GateName> idc=<code>` — sets the IDC while completing.
        - `/wormhole idc <GateName> <code>` — set or change the IDC later.      
        - `/wormhole idc <GateName> -clear` — remove the IDC and iris control.  
- The plugin will create an iris activation lever for gates that support an iris. For many 2D shapes the iris lever is placed underneath the dial lever; 3D shapes define the iris activation block in the shape file.

Common issue: clicking the wrong lever toggles gate activation instead of the iris
- Cause: older logic treated any block adjacent to the dial/iris lever as the same control; if the iris lever is adjacent to the dial lever, clicks could be routed to the activation handler.
- Workarounds:
        - Click the exact lever under the gate (not a nearby lever) — the iris lever is often below the dial lever for 2D shapes.
        - Use the IDC during dialing to unlock a remote iris: include the IDC as the 2nd argument when using `/dial` (or `/wormhole dial`) or use `/wormhole idc` to manage the code.
        - If you control shapes, edit the shape to place the `:IA` block further from the `:A` (activation) block so they are not adjacent.

Code fix (applied in this branch): the click handler now prefers exact lever clicks over adjacency, which prevents adjacent iris/dial levers from being misclassified. If you still see unexpected behavior, send the gate name and I'll inspect the gate's `IrisLever` and `DialLever` locations in the logs.

## Developer notes

- Code now provides a `StorageBackend` interface and a `SqliteStorage` scaffold at `src/main/java/com/wormhole_xtreme/wormhole/storage/`.
- `StargateYamlManager` handles per-gate YAML read/write.
- `StorageMigrator` provides a CLI-accessible migration tool for `db -> file`.  

## Troubleshooting

- If gates disappear after restart: check `plugins/WormholeXTreme/gates/` for YAML files or the configured DB file at `storage-sqlite-path`/JDBC URL.
- Check logs for storage initialization errors; increased logging was added for storage backend diagnostics.

## Contributing

Submit PRs against the `main` branch. Keep changes modular and add unit/integration tests where possible.
