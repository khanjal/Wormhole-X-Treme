# Licensing and Attribution

## Project License
**Wormhole X-Treme** is licensed under the **GNU General Public License v3 (GPL-3.0)**.

- [LICENSE](LICENSE) is the full GPL-3.0 text.
- [NOTICE.txt](NOTICE.txt) is the copyright notice and the no-warranty statement.

**The name and logo are excluded from GPL-3.0** and covered by
[TRADEMARK.md](TRADEMARK.md) instead. The code is unaffected -- every right the GPL grants over
the code stands. The carve-out exists because more than one project carries this name, and the
mark is what distinguishes them.

## Copyright

    Copyright (C) 2011  Ben Echols
                        Dean Bailey
    Copyright (C) 2026  Khan Jal

## Dependencies

### Primary Dependencies
All dependencies are managed via Maven and are compatible with GPL-3.0:

- **org.spigotmc:spigot-api** - Provided scope (build-only)
- **org.yaml:snakeyaml** - Apache License 2.0
- **org.junit.jupiter:junit-jupiter** - EPL-2.0 (test scope)
- **org.mockito:mockito-core** - MIT (test scope)

### Shaded Dependencies
One dependency is included in the shaded JAR (`target/WormholeXTreme-*.jar`):

- snakeyaml (YAML configuration and per-gate export/import)

There is no database dependency. Gates are stored as one YAML file each; the SQLite and HSQLDB
backends this document used to list were removed along with the database layer.

## Permission System
Wormhole X-Treme uses Bukkit's standard permission API, which integrates with:
- **Vault** - Recommended permission backend provider
- **LuckPerms** - Modern recommended permission management plugin
- **Built-in permissions** - Fallback system with per-player/network permission levels

## Compliance Notes

1. **GPL-3.0 Compatibility**: All dependencies are compatible with GPL-3.0 requirements.
2. **Service Merging**: The maven-shade-plugin uses `ServicesResourceTransformer`. It is a general safeguard for merging `META-INF/services` entries; the `java.sql.Driver` entries it was originally added for went away with the database backends.
3. **No Copyleft Conflict**: Test-scope dependencies (junit-jupiter, mockito-core) do not affect the released plugin.

## Questions or Issues

For licensing clarification or questions about specific dependencies, please file an issue or contact the maintainers.
