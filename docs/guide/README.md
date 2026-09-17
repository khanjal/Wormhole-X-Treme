# Server owner's guide

How to install, configure and use Wormhole X-Treme. Start with the page for what you want to do:

| Page | For |
|---|---|
| [SERVER.md](SERVER.md) | Installing, compatibility, configuration, permissions, commands, storage, economy, troubleshooting |
| [GATES.md](GATES.md) | Building, dialling and wiring stargates; shapes, palettes, signs, the iris, redstone |
| [RINGS.md](RINGS.md) | Building and using transport rings |
| [BEAMS.md](BEAMS.md) | Beam destinations and private places |
| [MIRRORS.md](MIRRORS.md) | Quantum mirrors, and making a banner look like where it goes |

These pages say what things do. Why they work that way is in the design notes beside this folder:
[GATES.md](../GATES.md), [RINGS.md](../RINGS.md), [BEAMS.md](../BEAMS.md) and
[MIRRORS.md](../MIRRORS.md). Plugin authors want [API.md](../API.md).

## What it looks like

The slots below are waiting on real captures from a server. Each placeholder names the shot it
is holding open, how long it should run and what the finished file is called;
[CAPTURES.md](../CAPTURES.md) has the shot list, the tick arithmetic behind each length, and the
ffmpeg commands.

**A finished gate.** All six shipped shapes, built and idle, from one camera position.

![The six shipped shapes, built](../images/gates/gate-shapes.png)

**Dialling.** Chevrons light in sequence, then the horizon erupts and settles.

![A Standard gate dialling: chevrons light in sequence, then the kawoosh](../images/gates/gate-dial.webp)

**Transport rings.** The pad lights, counts down, and four rings rise around whoever is standing
on it.

![A ring pair firing: the stack rises, the light runs through it, the rings come home](../images/rings/ring-cycle.webp)
