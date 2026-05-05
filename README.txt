Wormhole X-Treme v0.854

Quick Install (Paper 1.20.4 / Java 17)

1. Build or download the shaded JAR produced by this repo.
   - Quick build (no tests):

```bash
mvn -DskipTests package
```

2. Copy the shaded JAR into your Paper 1.20.4 server `plugins/` directory.

3. Start the server on Java 17 and watch `logs/latest.log` for plugin enable messages.

Configuration & Migration
 - On first run the plugin will migrate legacy `Settings.txt` into `config.yml` (kebab-case keys).
 - Permission-related keys are intentionally excluded from the migration — use Vault + a permissions
   provider (e.g., LuckPerms) instead of storing permissions in the plugin config.

Permissions (Vault + LuckPerms recommended)
 - This plugin prefers to use Vault as the permission bridge. Install Vault before starting the
   server so the bridge attaches on enable.
 - Recommended provider: LuckPerms. Install LuckPerms after Vault to manage permission nodes.
 - Relevant permission nodes used by the plugin (Complex mode):
   - `wormhole.use.sign`
   - `wormhole.use.dialer`
   - `wormhole.use.compass`
   - `wormhole.remove.own`
   - `wormhole.remove.all`
   - `wormhole.build`
   - `wormhole.config`
   - `wormhole.list`
   - `wormhole.go`
   - `wormhole.network.use.<NETWORKNAME>`
   - `wormhole.network.build.<NETWORKNAME>`
   - `wormhole.cooldown.groupone|grouptwo|groupthree`
 - Note: legacy `wormhole.simple.*` nodes and the plugin's simple-permissions mode have been removed
   in this release. Manage permissions via Vault/LuckPerms instead.

Storage & Backends
 - The plugin supports legacy HSQLDB storage and per-gate YAML persistence. On DB errors the plugin
   falls back to YAML storage automatically.
 - Future versions will add an explicit backend selection option and migration tooling.

Reporting issues
 - If the plugin fails on enable, attach `logs/latest.log` enable stacktrace and the server Java
   version. For persistence issues include `plugins/WormholeXTreme/config.yml` and any `gates/` YAML
   files from the plugin directory.

Build notes
 - The shaded artifact is produced at `target/WormholeXTreme-0.854-shaded.jar` after `mvn package`.
 - To build a release with a bumped version change the `pom.xml` version and run:

```bash
mvn -DskipTests package
```

-- Upgrade assistant