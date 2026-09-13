<p align="center">
  <img src="docs/images/logo-banner.svg" alt="Wormhole X-Treme" width="620">
</p>

[![CI](https://img.shields.io/github/actions/workflow/status/khanjal/Wormhole-X-Treme/ci.yml?branch=main&label=CI&logo=githubactions&logoColor=white)](https://github.com/khanjal/Wormhole-X-Treme/actions/workflows/ci.yml)
[![Tests](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fsonarcloud.io%2Fapi%2Fmeasures%2Fcomponent%3Fcomponent%3Dkhanjal_Wormhole-X-Treme%26metricKeys%3Dtests&query=%24.component.measures%5B0%5D.value&suffix=%20passing&label=tests&color=success&logo=junit5&logoColor=white)](https://github.com/khanjal/Wormhole-X-Treme/actions/workflows/ci.yml)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=khanjal_Wormhole-X-Treme&metric=coverage)](https://sonarcloud.io/component_measures?id=khanjal_Wormhole-X-Treme&metric=coverage)
[![Maintainability](https://sonarcloud.io/api/project_badges/measure?project=khanjal_Wormhole-X-Treme&metric=sqale_rating)](https://sonarcloud.io/summary/overall/?id=khanjal_Wormhole-X-Treme)
[![Reliability](https://sonarcloud.io/api/project_badges/measure?project=khanjal_Wormhole-X-Treme&metric=reliability_rating)](https://sonarcloud.io/summary/overall/?id=khanjal_Wormhole-X-Treme)
[![Security](https://sonarcloud.io/api/project_badges/measure?project=khanjal_Wormhole-X-Treme&metric=security_rating)](https://sonarcloud.io/summary/overall/?id=khanjal_Wormhole-X-Treme)

[![Release](https://img.shields.io/github/v/release/khanjal/Wormhole-X-Treme?label=release&logo=github)](https://github.com/khanjal/Wormhole-X-Treme/releases/latest)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20%20--%201.21.10-brightgreen)](#compatibility)
[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)](#building-from-source)
[![License](https://img.shields.io/github/license/khanjal/Wormhole-X-Treme?color=blue)](LICENSE)

Stargate-style travel for Bukkit, Spigot and Paper servers, on Minecraft 1.20 through 1.21.10.

Four ways to get somewhere, each a different trade between what you build and what you get:

| | What you build | How you use it | Where it goes |
|---|---|---|---|
| **[Stargates](docs/guide/GATES.md)** | A frame of blocks | Dial by button, sign, redstone or `/dial` | Any gate, across worlds |
| **[Transport rings](docs/guide/RINGS.md)** | A circle of slabs, in pairs | Walk into it | Its partner, same world |
| **[Beaming](docs/guide/BEAMS.md)** | Nothing | `/wormhole beam to <name>` | A saved point, anywhere |
| **[Quantum mirrors](docs/guide/MIRRORS.md)** | One banner | Right-click it | Another world, one way |

## Features

- **Everything travels** — minecarts and boats with their passengers, horses with their riders,
  arrows and ender pearls mid-flight, and mobs and items that wander into an open gate.
- **Configured in-game** — `/wormhole config` changes any setting on the spot, with no reload.
- **Eleven gate shapes** in any palette: build `Standard` in obsidian or in lapis and get a
  different-looking gate from one shape file.
- **Mirrors that look like where they go** — a banner reads its destination and paints itself.
- **Every sound is a setting**, resource pack sounds included.
- **Works with or without a permissions plugin**, and charges through Vault only if you want it to.
- **Events for other plugins** to watch or cancel travel — see [docs/API.md](docs/API.md).
- **Plain YAML storage**, and `/wormhole gate import` for gates from older forks' SQLite databases.

## Getting started

Drop the jar from the [latest release](https://github.com/khanjal/Wormhole-X-Treme/releases/latest)
into `plugins/` and start the server. No dependencies.

**A gate** — `/wormhole gate build StandardSignDial`, lay the frame in obsidian, click the DHD
button, then `/wormhole gate complete Home`. Put a sign on the dial block to pick destinations.
[More](docs/guide/GATES.md#building-a-gate)

**Rings** — lay a circle of slabs, stand in it, `/wormhole ring create`. Do it again somewhere
else in the same world. [More](docs/guide/RINGS.md#building-a-ring-pair)

**Beaming** — `/wormhole beam place set home` where you stand, `/wormhole beam to home` from
anywhere. [More](docs/guide/BEAMS.md)

**A mirror** — look at a banner and `/wormhole mirror set nether`, then look at a banner in
another world and `/wormhole mirror link nether`. [More](docs/guide/MIRRORS.md#setting-one-up)

## Compatibility

| | |
|---|---|
| Minecraft | 1.20 – 1.21.10, built and tested against seven versions across that range |
| Servers | Spigot, Paper and CraftBukkit. Purpur best effort. Not Folia. |
| Java | 17 or later. Minecraft 1.20.5+ itself needs Java 21. |

CI proves it compiles and passes its tests on each version, not that it has been played on each
one. [Details](docs/guide/SERVER.md#compatibility)

## Documentation

**Running a server** — [Setup, configuration, permissions and commands](docs/guide/SERVER.md)

**Using each feature** — [Gates](docs/guide/GATES.md) · [Rings](docs/guide/RINGS.md) ·
[Beaming](docs/guide/BEAMS.md) · [Mirrors](docs/guide/MIRRORS.md)

**How it works, and why** — [Gates](docs/GATES.md) · [Rings](docs/RINGS.md) ·
[Beaming](docs/BEAMS.md) · [Mirrors](docs/MIRRORS.md)

**Writing a plugin against this one** — [docs/API.md](docs/API.md)

**Working on the plugin** — [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) · [CHANGELOG](CHANGELOG.md)

## Building from source

JDK 17 and Maven 3.6+.

```bash
mvn -DskipTests package
```

The jar lands in `target/WormholeXTreme-<version>.jar`. Tests, static analysis and supported
versions are covered in [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## Credits

Wormhole X-Treme was written by **Lologarithm** (Ben Echols) and **alron** (Dean Bailey), with
contributions from **lirelent** (Ryan Metzger) and **Jeremy Wood**. alron wrote most of it — 309
of the commits in this repository — and the gate detection, shape format and storage layer this
fork still runs on are his design.

**lycano** kept the plugin alive after the original went quiet, through the
[WolfNetDevelopment fork](https://github.com/WolfNetDevelopment/Wormhole-X-Treme) until 2015.
This fork descends from the original rather than theirs, but the plugin's history does not make
sense without that work.

This fork brings it to modern Minecraft, and adds rings, beaming and mirrors.

**The logo is a placeholder**, hand-authored as SVG with Claude Code as a brief for a real artist.
[docs/LOGO.md](docs/LOGO.md) explains it, [TRADEMARK.md](TRADEMARK.md) covers use of the name and
mark, and [issue #187](https://github.com/khanjal/Wormhole-X-Treme/issues/187) tracks replacing it.

## Contributing

Pull requests against `main`, with tests where the change touches behaviour. See
[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).
