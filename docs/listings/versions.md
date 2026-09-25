# Versions

What goes in each site's version upload, one section per release, newest first. It starts at 1.7.0, the
first version on the plugin sites; add a section at the top for each new release.

| Site | Form | Takes |
|---|---|---|
| [SpigotMC](spigot.md#version-upload) | Post Resource Update | the jar, **Version string**, **Update title**, **Update message** (BBCode) |
| [Modrinth](modrinth.md#version-upload) | Create version | the jar, **Version number**, **Version title**, **Changelog** (Markdown), game versions |
| [Hangar](hangar.md#version-upload) | Upload version | the jar, then **Changelog** (Markdown) and platform versions on the next screen |

The jar for each is `WormholeXTreme-<version>.jar` on its
[GitHub release](https://github.com/khanjal/Wormhole-X-Treme/releases).

## Back-filling

- **Post oldest first.** Each site treats the newest upload as current, and Spigot makes the last
  update posted the resource's download. Going oldest to newest in one sitting leaves each site on
  1.8.0 with the history in order.
- **On Spigot, untick Notify followers for all but the newest**, or followers get one alert per
  back-filled version.
- **Tick each version's own Minecraft range** on Modrinth and Hangar, from its section below, not
  today's.

## Writing a new entry

- **Title**: a few words on what the release is for, under about 60 characters. The version
  number goes in front on every site, as below, so a list of updates reads on its own.
- **Changelog**: an Upgrading line first, then three to five bullets, then the link to that
  version's full section of `CHANGELOG.md`. Very high level: a site's changelog is read by
  people deciding whether to download, not by people debugging.
- **Keep the Upgrading line** even when it says there is nothing to do, and keep any step an
  upgrader has to take. For the newest release, also say what someone two or three versions
  behind has to do, and that a first install needs none of it.
- **Write the Markdown first, then convert it to BBCode** line for line, so the two say the same.
  The conversion is mechanical: `**bold**` to `[B]`, `` `code` `` to `[ICODE]`, a bullet list to
  `[LIST]` with `[*]` per item, a link to `[URL=…]`, and em-dashes to plain hyphens.

## 1.8.0

Released 2026-09-26.

| Field | Value |
|---|---|
| Version (all three sites) | `1.8.0` |
| Title (Spigot update title, Modrinth version title) | `1.8.0 - Animated iris, new dial patterns and CoreProtect logging` |
| Minecraft (Modrinth game versions, Hangar platform versions) | 1.20 – 26.3 |

Modrinth and Hangar changelog:

````markdown
**Upgrading from 1.7 — nothing you have to do.**

- **Anonymous usage counts now go to bStats**, on by default: Minecraft version, server software, and how many gates, rings, beams and mirrors, in ranges. `/wormhole config metrics-enabled false` stops it.
- Bundled shapes you have not edited update themselves at startup, keeping the old copy as `<name>.shape.old`. An edited one is left alone and named in the log.
- The default `top` dial rests on the top chevron after each lock, so a dial takes about three seconds longer. `/wormhole config gate-dial-spin chevron` keeps the old pace.
- Optional: add `dial-spin: pegasus` and `dial-spin: universe` to an existing `config.yml`'s Atlantis and Universe groups, as new installs have.
- A coded gate whose iris came back open after a crash under 1.7 needs its lever pulled once.
- Coming from 1.7.0? The `Massive` shape updates itself; then run `/wormhole gate regen <gate>` on each Massive gate, as 1.7.1 said. From 1.6.0, 1.7.0's step too: command keywords now need a dash.
- Installing for the first time? None of the above applies. Drop the jar in and start.

**New**

- An animated iris — sweep, spiral, rows or columns — that covers the wormhole instead of replacing it.
- Three more dial-spin patterns: `chase`, `universe` and `overshoot`. A gate or a material group can pick its own pattern and iris style.
- Optional CoreProtect logging of gate and ring construction, so it can be rolled back.
- For other plugins: wormhole open and close events, and a PlaceholderAPI expansion.
- `/wormhole` lists its commands by job, with a short coloured usage line and more tab completion.

**Fixed**

- Gate settings are saved as they are set and survive `gate regen`, so a crash no longer loses them or leaves an iris open.
- Many iris, wormhole and dialling fixes; the full changelog lists them.

[Full changelog](https://github.com/khanjal/Wormhole-X-Treme/blob/main/CHANGELOG.md#180-2026-09-26)
````

Spigot update message:

```
[B]Upgrading from 1.7 - nothing you have to do.[/B]

[LIST]
[*][B]Anonymous usage counts now go to bStats[/B], on by default: Minecraft version, server software, and how many gates, rings, beams and mirrors, in ranges. [ICODE]/wormhole config metrics-enabled false[/ICODE] stops it.
[*]Bundled shapes you have not edited update themselves at startup, keeping the old copy as [ICODE]<name>.shape.old[/ICODE]. An edited one is left alone and named in the log.
[*]The default [ICODE]top[/ICODE] dial rests on the top chevron after each lock, so a dial takes about three seconds longer. [ICODE]/wormhole config gate-dial-spin chevron[/ICODE] keeps the old pace.
[*]Optional: add [ICODE]dial-spin: pegasus[/ICODE] and [ICODE]dial-spin: universe[/ICODE] to an existing [ICODE]config.yml[/ICODE]'s Atlantis and Universe groups, as new installs have.
[*]A coded gate whose iris came back open after a crash under 1.7 needs its lever pulled once.
[*]Coming from 1.7.0? The [ICODE]Massive[/ICODE] shape updates itself; then run [ICODE]/wormhole gate regen <gate>[/ICODE] on each Massive gate, as 1.7.1 said. From 1.6.0, 1.7.0's step too: command keywords now need a dash.
[*]Installing for the first time? None of the above applies. Drop the jar in and start.
[/LIST]

[B]New[/B]

[LIST]
[*]An animated iris - sweep, spiral, rows or columns - that covers the wormhole instead of replacing it.
[*]Three more dial-spin patterns: [ICODE]chase[/ICODE], [ICODE]universe[/ICODE] and [ICODE]overshoot[/ICODE]. A gate or a material group can pick its own pattern and iris style.
[*]Optional CoreProtect logging of gate and ring construction, so it can be rolled back.
[*]For other plugins: wormhole open and close events, and a PlaceholderAPI expansion.
[*][ICODE]/wormhole[/ICODE] lists its commands by job, with a short coloured usage line and more tab completion.
[/LIST]

[B]Fixed[/B]

[LIST]
[*]Gate settings are saved as they are set and survive [ICODE]gate regen[/ICODE], so a crash no longer loses them or leaves an iris open.
[*]Many iris, wormhole and dialling fixes; the full changelog lists them.
[/LIST]

[URL=https://github.com/khanjal/Wormhole-X-Treme/blob/main/CHANGELOG.md#180-2026-09-26]Full changelog[/URL]
```

## 1.7.1

Released 2026-09-19.

| Field | Value |
|---|---|
| Version (all three sites) | `1.7.1` |
| Title (Spigot update title, Modrinth version title) | `1.7.1 - Massive gate shape fixed; regen fills missing blocks` |
| Minecraft (Modrinth game versions, Hangar platform versions) | 1.20 – 26.3 |

Modrinth and Hangar changelog:

````markdown
**Upgrading: take the new `Massive.shape`, then regenerate each Massive gate.** Delete `Massive.shape` if you did not edit it and restart, then run `/wormhole gate regen <gate>` on each Massive gate.

- `Massive` is symmetric, and its name sign hangs on the front instead of inside the ring.
- `gate regen -fill` places the few frame blocks a gate is missing.
- `gate preview place` over an unfinished gate fills in only what it lacks.
- `/version WormholeXTreme` shows when the jar was built.

[Full changelog](https://github.com/khanjal/Wormhole-X-Treme/blob/main/CHANGELOG.md#171-2026-09-19)
````

Spigot update message:

```
[B]Upgrading: take the new [ICODE]Massive.shape[/ICODE], then regenerate each Massive gate.[/B] Delete [ICODE]Massive.shape[/ICODE] if you did not edit it and restart, then run [ICODE]/wormhole gate regen <gate>[/ICODE] on each Massive gate.

[LIST]
[*][ICODE]Massive[/ICODE] is symmetric, and its name sign hangs on the front instead of inside the ring.
[*][ICODE]gate regen -fill[/ICODE] places the few frame blocks a gate is missing.
[*][ICODE]gate preview place[/ICODE] over an unfinished gate fills in only what it lacks.
[*][ICODE]/version WormholeXTreme[/ICODE] shows when the jar was built.
[/LIST]

[URL=https://github.com/khanjal/Wormhole-X-Treme/blob/main/CHANGELOG.md#171-2026-09-19]Full changelog[/URL]
```

## 1.7.0

Released 2026-09-19.

| Field | Value |
|---|---|
| Version (all three sites) | `1.7.0` |
| Title (Spigot update title, Modrinth version title) | `1.7.0 - Show-accurate dialling, build previews and pets through gates` |
| Minecraft (Modrinth game versions, Hangar platform versions) | 1.20 – 26.3 |

Modrinth and Hangar changelog:

````markdown
**Upgrading: command keywords now start with a dash** (`-destroy`, `-confirm`, `-start` and so on), so update scripts and command blocks. Delete bundled shapes you did not edit and restart to get the new ones.

- Dialling as the show does it: chevrons in order at a pace you can follow, an eighth for another world, the inner ring turning, and a lock-in sound.
- Build previews: `/wormhole gate build <shape>` stands a gate full size in front of you to build into, with a materials list, a build guide and `-place`.
- A player's pets that are not sitting come along through gates, rings, beams and mirrors.
- `gate regen` finds a gate's real shape.

[Full changelog](https://github.com/khanjal/Wormhole-X-Treme/blob/main/CHANGELOG.md#170-2026-09-19)
````

Spigot update message:

```
[B]Upgrading: command keywords now start with a dash[/B] ([ICODE]-destroy[/ICODE], [ICODE]-confirm[/ICODE], [ICODE]-start[/ICODE] and so on), so update scripts and command blocks. Delete bundled shapes you did not edit and restart to get the new ones.

[LIST]
[*]Dialling as the show does it: chevrons in order at a pace you can follow, an eighth for another world, the inner ring turning, and a lock-in sound.
[*]Build previews: [ICODE]/wormhole gate build <shape>[/ICODE] stands a gate full size in front of you to build into, with a materials list, a build guide and [ICODE]-place[/ICODE].
[*]A player's pets that are not sitting come along through gates, rings, beams and mirrors.
[*][ICODE]gate regen[/ICODE] finds a gate's real shape.
[/LIST]

[URL=https://github.com/khanjal/Wormhole-X-Treme/blob/main/CHANGELOG.md#170-2026-09-19]Full changelog[/URL]
```
