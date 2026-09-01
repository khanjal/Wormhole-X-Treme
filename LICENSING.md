# Licensing and Attribution

## Project License
**Wormhole X-Treme** is licensed under the **GNU General Public License v3 (GPL-3.0)**.

See [LICENSE.txt](LICENSE.txt) for the full license text.

## Dependencies

### Primary Dependencies
All dependencies are managed via Maven and are compatible with GPL-3.0:

- **org.spigotmc:spigot-api** - Provided scope (build-only)
- **org.yaml:snakeyaml** - Apache License 2.0
- **org.sqlite:sqlite-jdbc** - Apache License 2.0
- **org.hsqldb:hsqldb** - Dual-licensed under HSQLDB License / BSD License
- **org.junit.jupiter:junit-jupiter** - EPL-2.0 (test scope)
- **org.mockito:mockito-core** - MIT (test scope)

### Shaded Dependencies
The following dependencies are included in the shaded JAR (`target/WormholeXTreme-*.jar`):

- snakeyaml (YAML configuration and per-gate export/import)
- sqlite-jdbc (optional lightweight database backend)

## Permission System
Wormhole X-Treme uses Bukkit's standard permission API, which integrates with:
- **Vault** - Recommended permission backend provider
- **LuckPerms** - Modern recommended permission management plugin
- **Built-in permissions** - Fallback system with per-player/network permission levels

## Compliance Notes

1. **GPL-3.0 Compatibility**: All dependencies are compatible with GPL-3.0 requirements.
2. **Service Merging**: The maven-shade-plugin uses `ServicesResourceTransformer` to properly merge `META-INF/services/java.sql.Driver` entries from multiple database vendors.
3. **No Copyleft Conflict**: Test-scope dependencies (junit-jupiter, mockito-core) do not affect the released plugin.

## Questions or Issues

For licensing clarification or questions about specific dependencies, please file an issue or contact the maintainers.
