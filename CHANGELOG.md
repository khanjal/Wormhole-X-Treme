# Changelog

All notable changes to this project are documented in this file.

## Unreleased (dev)

- Added pluggable storage backend support and config keys (`storage-backend`, `storage-sqlite-path`, `storage-jdbc-*`).
- Implemented `StorageBackend` interface and `SqliteStorage` scaffold.
- Added `/wormhole storage` CLI: `backend` (set runtime backend) and `migrate` (DB -> YAML supported).
- Added `StorageMigrator` to export existing DB gates to per-gate YAML files (non-destructive by default).
- `ConfigurationYAML` migration now excludes legacy permission keys and preserves storage keys when writing `config.yml`.
- Removed legacy `SimplePermission` support; Vault/LuckPerms recommended.
- Added two new default gate shapes: `StandardAtlantis` and `StandardUniverse` (bundled in resources).
- Improved startup diagnostics and storage initialization logging.
- Fixed a number of persistence and teleport UX issues (teleport bounce mitigation and gate activation mapping fixes).

## 0.854 (previous)

-- Legacy HSQLDB-based persistence and startup code. (REMOVED in this branch)
- Per-gate YAML manager (`StargateYamlManager`) for file-based storage.

## Notes

- This changelog is concise; include more details per commit when preparing releases.
