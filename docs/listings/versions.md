# Versions

What goes in each site's version upload, one section per release, newest first. It starts at 1.7.0, the
first version on the plugin sites; add a section at the top for each new release.

| Site | Form | Takes |
|---|---|---|
| [SpigotMC](spigot.md#version-upload) | Post Resource Update | the jar, **Version string**, **Update title**, **Update message** (BBCode) |
| [Modrinth](modrinth.md#version-upload) | Create version | the jar, **Version number**, **Version title**, **Changelog** (Markdown), game versions |
| [Hangar](hangar.md#version-upload) | Upload version | the jar, then **Changelog** (Markdown) and platform versions on the next screen |
| [CurseForge](curseforge.md#version-upload) | Upload file | the jar, **Display name**, **Changelog** (the Modrinth and Hangar Markdown), Bukkit versions |

The jar for each is `WormholeXTreme-<version>.jar` on its
[GitHub release](https://github.com/khanjal/Wormhole-X-Treme/releases).

## Back-filling

- **Post oldest first.** Each site treats the newest upload as current, and Spigot makes the last
  update posted the resource's download. Going oldest to newest in one sitting leaves each site on
  1.13.0 with the history in order.
- **On Spigot, untick Notify followers for all but the newest**, or followers get one alert per
  back-filled version.
- **Tick each version's own Minecraft range** on Modrinth, Hangar and CurseForge, from its section below, not
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

## 1.13.0

Released 2026-11-01.

| Field | Value |
|---|---|
| Version (all four sites) | `1.13.0` |
| Title (Spigot update title, Modrinth version title) | `1.13.0 - Per-shape permissions, gate limits, gate costs` |
| Minecraft (Modrinth game versions, Hangar platform versions, CurseForge Bukkit versions) | 1.20 - 26.3 |

Modrinth, Hangar and CurseForge changelog:

````markdown
**Upgrading from 1.12 - nothing to change.** Your `config.yml` is kept as it is, `gate-dial-spin` included, so gates dial with the ring light they had. The new `max-gates-per-player` is appended at `0`, no limit, the first time it loads. Captures move to `data/captures/` by themselves the first time they are needed. From 1.10 or earlier, read the 1.11.0 upgrade note below first: an unedited `config.yml` is replaced there, and gets the new `gate-dial-spin: none`. A first install needs none of this.

- **Build permission per shape**: `wormhole.build.<shape>` lets a rank build one gate shape, its name in lower case with anything but a letter, digit or underscore made an underscore (`wormhole.build.grand_gate`); `wormhole.build` still allows every shape. A rank given `wormhole.build.*` now builds every shape.
- **A gate limit per player**: `max-gates-per-player` caps the gates one player may own, off at `0`; `wormhole.gates.max.<n>` gives a group its own limit, `wormhole.gates.unlimited` lifts it, and `/wormhole gate list` shows a limited player their count and limit.
- **A use cost per gate or shape**: `/wormhole gate edit <gate> cost <amount>` gives one gate its own cost and `USE_COST=<amount>` in a shape file gives every gate of that shape one; `default` hands a gate back to `economy-use-cost`.
- **A view level per gate**: `/wormhole gate edit <gate> view <horizon|behind|open|default>` wins over `gate-view`, and `/wormhole mirror debug <gate>` says how a gate's view is drawn and, if it is not, why.
- **No ring light by default**: `gate-dial-spin` now defaults to `none`, as the original plugin dialled; `/wormhole config gate-dial-spin top` brings back the 1.12 default.
- **Captures move to `data/captures/`**, `gates/` and `mirrors/`, so `data/gates/` holds gate files only. A script reading the old capture folders must look in the new ones.
- **Gate views**: `gate-view: behind` leaves a gate with a lava or opaque horizon as it is, and a gate's view is retaken only from chunks that are loaded, so the retake no longer loads or generates chunks.
- **bStats counts gate frame blocks**: a new `gate_material` pie sends the game's own block name, never a name a server chose; `metrics-enabled: false` stops it with the rest.
- **Fixes**: `/wormhole complete` and `/wormhole gate build` without `net=` use the Public network instead of refusing a builder holding only `wormhole.build`, a sign gate dials the gate its sign shows after a restart or `/wormhole refresh`, and a window no longer leaves creatures hidden when the server refuses to hide one.
- **Internals**: the window drawing, gates and build previews were reorganised into smaller classes; nothing a player or admin sees changes.

[Full changelog](https://github.com/khanjal/Wormhole-X-Treme/blob/main/CHANGELOG.md#1130-2026-11-01)
````

Spigot update message:

```
[B]Upgrading from 1.12 - nothing to change.[/B] Your [ICODE]config.yml[/ICODE] is kept as it is, [ICODE]gate-dial-spin[/ICODE] included, so gates dial with the ring light they had. The new [ICODE]max-gates-per-player[/ICODE] is appended at [ICODE]0[/ICODE], no limit, the first time it loads. Captures move to [ICODE]data/captures/[/ICODE] by themselves the first time they are needed. From 1.10 or earlier, read the 1.11.0 upgrade note below first: an unedited [ICODE]config.yml[/ICODE] is replaced there, and gets the new [ICODE]gate-dial-spin: none[/ICODE]. A first install needs none of this.

[LIST]
[*][B]Build permission per shape[/B]: [ICODE]wormhole.build.<shape>[/ICODE] lets a rank build one gate shape, its name in lower case with anything but a letter, digit or underscore made an underscore ([ICODE]wormhole.build.grand_gate[/ICODE]); [ICODE]wormhole.build[/ICODE] still allows every shape. A rank given [ICODE]wormhole.build.*[/ICODE] now builds every shape.
[*][B]A gate limit per player[/B]: [ICODE]max-gates-per-player[/ICODE] caps the gates one player may own, off at [ICODE]0[/ICODE]; [ICODE]wormhole.gates.max.<n>[/ICODE] gives a group its own limit, [ICODE]wormhole.gates.unlimited[/ICODE] lifts it, and [ICODE]/wormhole gate list[/ICODE] shows a limited player their count and limit.
[*][B]A use cost per gate or shape[/B]: [ICODE]/wormhole gate edit <gate> cost <amount>[/ICODE] gives one gate its own cost and [ICODE]USE_COST=<amount>[/ICODE] in a shape file gives every gate of that shape one; [ICODE]default[/ICODE] hands a gate back to [ICODE]economy-use-cost[/ICODE].
[*][B]A view level per gate[/B]: [ICODE]/wormhole gate edit <gate> view <horizon|behind|open|default>[/ICODE] wins over [ICODE]gate-view[/ICODE], and [ICODE]/wormhole mirror debug <gate>[/ICODE] says how a gate's view is drawn and, if it is not, why.
[*][B]No ring light by default[/B]: [ICODE]gate-dial-spin[/ICODE] now defaults to [ICODE]none[/ICODE], as the original plugin dialled; [ICODE]/wormhole config gate-dial-spin top[/ICODE] brings back the 1.12 default.
[*][B]Captures move to [ICODE]data/captures/[/ICODE][/B], [ICODE]gates/[/ICODE] and [ICODE]mirrors/[/ICODE], so [ICODE]data/gates/[/ICODE] holds gate files only. A script reading the old capture folders must look in the new ones.
[*][B]Gate views[/B]: [ICODE]gate-view: behind[/ICODE] leaves a gate with a lava or opaque horizon as it is, and a gate's view is retaken only from chunks that are loaded, so the retake no longer loads or generates chunks.
[*][B]bStats counts gate frame blocks[/B]: a new [ICODE]gate_material[/ICODE] pie sends the game's own block name, never a name a server chose; [ICODE]metrics-enabled: false[/ICODE] stops it with the rest.
[*][B]Fixes[/B]: [ICODE]/wormhole complete[/ICODE] and [ICODE]/wormhole gate build[/ICODE] without [ICODE]net=[/ICODE] use the Public network instead of refusing a builder holding only [ICODE]wormhole.build[/ICODE], a sign gate dials the gate its sign shows after a restart or [ICODE]/wormhole refresh[/ICODE], and a window no longer leaves creatures hidden when the server refuses to hide one.
[*][B]Internals[/B]: the window drawing, gates and build previews were reorganised into smaller classes; nothing a player or admin sees changes.
[/LIST]

[URL=https://github.com/khanjal/Wormhole-X-Treme/blob/main/CHANGELOG.md#1130-2026-11-01]Full changelog[/URL]
```

## 1.12.0

Released 2026-10-25.

| Field | Value |
|---|---|
| Version (all four sites) | `1.12.0` |
| Title (Spigot update title, Modrinth version title) | `1.12.0 - Players' language, config reload, shape switches` |
| Minecraft (Modrinth game versions, Hangar platform versions, CurseForge Bukkit versions) | 1.20 - 26.3 |

Modrinth, Hangar and CurseForge changelog:

````markdown
**Upgrading from 1.11 - nothing to change.** Your `config.yml` is kept as it is, and the settings 1.12 adds (`server-language`, `min-gate-distance`) are appended with their defaults the first time it loads: messages stay English until a player's client language has a lang file, and gates can still be built anywhere. From 1.10 or earlier, read the 1.11.0 upgrade note below first: an unedited `config.yml` is replaced there. A first install needs none of this.

- **Messages in your players' language**: chat messages follow each player's client language, falling back to `server-language` and then English; put a `lang/<code>.yml` in the plugin folder to add or reword one, and `/wormhole lang export [code]` writes a starter file. More messages become translatable each release; sign text stays English.
- **Reload the config without a restart**: `/wormhole config -reload` lists each setting it changed, names any value it refused and keeps the old one.
- **Switch a gate shape off**: `/wormhole gate shapes disable <shape>` and `enable` stop and allow building it without deleting its file; gates already standing keep working.
- **A minimum distance between gates**: `min-gate-distance` refuses a gate built too close to another in its world; `wormhole.build.mindistance.bypass` exempts. Off until set.
- **Fixes**: `log-level: off` no longer stops the plugin loading, shape commands refuse a name that leaves the shapes folder, a doubled apostrophe in the mirror capture messages is a single one, and a far-side capture is logged once.

[Full changelog](https://github.com/khanjal/Wormhole-X-Treme/blob/main/CHANGELOG.md#1120-2026-10-25)
````

Spigot update message:

```
[B]Upgrading from 1.11 - nothing to change.[/B] Your [ICODE]config.yml[/ICODE] is kept as it is, and the settings 1.12 adds ([ICODE]server-language[/ICODE], [ICODE]min-gate-distance[/ICODE]) are appended with their defaults the first time it loads: messages stay English until a player's client language has a lang file, and gates can still be built anywhere. From 1.10 or earlier, read the 1.11.0 upgrade note below first: an unedited [ICODE]config.yml[/ICODE] is replaced there. A first install needs none of this.

[LIST]
[*][B]Messages in your players' language[/B]: chat messages follow each player's client language, falling back to [ICODE]server-language[/ICODE] and then English; put a [ICODE]lang/<code>.yml[/ICODE] in the plugin folder to add or reword one, and [ICODE]/wormhole lang export [code][/ICODE] writes a starter file. More messages become translatable each release; sign text stays English.
[*][B]Reload the config without a restart[/B]: [ICODE]/wormhole config -reload[/ICODE] lists each setting it changed, names any value it refused and keeps the old one.
[*][B]Switch a gate shape off[/B]: [ICODE]/wormhole gate shapes disable <shape>[/ICODE] and [ICODE]enable[/ICODE] stop and allow building it without deleting its file; gates already standing keep working.
[*][B]A minimum distance between gates[/B]: [ICODE]min-gate-distance[/ICODE] refuses a gate built too close to another in its world; [ICODE]wormhole.build.mindistance.bypass[/ICODE] exempts. Off until set.
[*][B]Fixes[/B]: [ICODE]log-level: off[/ICODE] no longer stops the plugin loading, shape commands refuse a name that leaves the shapes folder, a doubled apostrophe in the mirror capture messages is a single one, and a far-side capture is logged once.
[/LIST]

[URL=https://github.com/khanjal/Wormhole-X-Treme/blob/main/CHANGELOG.md#1120-2026-10-25]Full changelog[/URL]
```

## 1.11.0

Released 2026-10-17.

| Field | Value |
|---|---|
| Version (all four sites) | `1.11.0` |
| Title (Spigot update title, Modrinth version title) | `1.11.0 - A steadier server, fewer settings and tidier messages` |
| Minecraft (Modrinth game versions, Hangar platform versions, CurseForge Bukkit versions) | 1.20 - 26.3 |

Modrinth, Hangar and CurseForge changelog:

````markdown
**Upgrading from 1.10 - settings change.** A `config.yml` still exactly as 1.10 wrote it is replaced with a new grouped layout and its new defaults, the old one kept as `config.yml.bak`; one you have edited is kept as it is, and any setting 1.11 removed is ignored and named in the log. The seven ring timings and six beaming timings are now `ring-animation-speed` and `beam-animation-speed`, and `view-depth` replaces the three view depths.

- **A steadier server**: looking into a mirror or a gate view no longer stalls the server, nor hitches the first time it is drawn after a restart, and captures take less memory and disk.
- **Fewer settings**: one speed for the ring animation, one for beaming, one view depth, and the timing and housekeeping settings nobody tuned gone, their defaults kept.
- **Tunnels between gates of the same size**: `gate-view-funnel-min-length` lets same-size gates be seen down a straight tunnel. Off until set.
- **Tidier messages**: every gate, ring, beam, mirror and admin message uses one colour palette and says what to type next; refusals open with `:: error ::`.
- **An iris no longer covers the wormhole** for a moment as it opens or shuts.
- **More in bStats**: anonymous counts also say which gate shapes, portal materials, view and iris settings and optional plugins are in use. Nothing names a player, gate or place.

[Full changelog](https://github.com/khanjal/Wormhole-X-Treme/blob/main/CHANGELOG.md#1110-2026-10-17)
````

Spigot update message:

```
[B]Upgrading from 1.10 - settings change.[/B] A [ICODE]config.yml[/ICODE] still exactly as 1.10 wrote it is replaced with a new grouped layout and its new defaults, the old one kept as [ICODE]config.yml.bak[/ICODE]; one you have edited is kept as it is, and any setting 1.11 removed is ignored and named in the log. The seven ring timings and six beaming timings are now [ICODE]ring-animation-speed[/ICODE] and [ICODE]beam-animation-speed[/ICODE], and [ICODE]view-depth[/ICODE] replaces the three view depths.

[LIST]
[*][B]A steadier server[/B]: looking into a mirror or a gate view no longer stalls the server, nor hitches the first time it is drawn after a restart, and captures take less memory and disk.
[*][B]Fewer settings[/B]: one speed for the ring animation, one for beaming, one view depth, and the timing and housekeeping settings nobody tuned gone, their defaults kept.
[*][B]Tunnels between gates of the same size[/B]: [ICODE]gate-view-funnel-min-length[/ICODE] lets same-size gates be seen down a straight tunnel. Off until set.
[*][B]Tidier messages[/B]: every gate, ring, beam, mirror and admin message uses one colour palette and says what to type next; refusals open with [ICODE]:: error ::[/ICODE].
[*][B]An iris no longer covers the wormhole[/B] for a moment as it opens or shuts.
[*][B]More in bStats[/B]: anonymous counts also say which gate shapes, portal materials, view and iris settings and optional plugins are in use. Nothing names a player, gate or place.
[/LIST]

[URL=https://github.com/khanjal/Wormhole-X-Treme/blob/main/CHANGELOG.md#1110-2026-10-17]Full changelog[/URL]
```

## 1.10.0

Released 2026-10-12.

| Field | Value |
|---|---|
| Version (all four sites) | `1.10.0` |
| Title (Spigot update title, Modrinth version title) | `1.10.0 - Views through gates, ripples and more web maps` |
| Minecraft (Modrinth game versions, Hangar platform versions, CurseForge Bukkit versions) | 1.20 – 26.3 |

Modrinth and Hangar changelog:

````markdown
**Upgrading from 1.9 — one thing may change.** The `pegasus` and `chevron` dial spins have swapped: a gate, group or `gate-dial-spin` set to either now dials the other way, so set it to the other name to keep the old look. A new `config.yml` is laid out commonest first; an existing file keeps its own order. Everything else new is off until you turn it on, except the tunnel (`gate-view-funnel`, which only matters with `gate-view` on) and clickable previews (`clickable-chat`). Coming from 1.8 or earlier? Each release you skipped has notes that still apply. Installing for the first time? None of this applies. Drop the jar in and start.

- **See where a gate goes**, as an experiment: `gate-view` draws the far side behind an open gate, or clears the horizon to show it, and a big gate dialling a small one shows it down a tunnel.
- **Gates ripple**: `gate-ripple` sends rings across the horizon as something goes through and while somebody is near.
- **Creatures and players in the view**: `mirror-show-entities` and `mirror-show-players` show who and what is in a mirror's or an open gate's far room, as copies only the viewer sees.
- **BlueMap, squaremap and Pl3xMap** join Dynmap: gates, rings, public beam destinations and mirrors on each map. Set `bluemap-enabled`, `squaremap-enabled` or `pl3xmap-enabled`.
- **Build previews have buttons**: after `gate build`, each action is clickable. `show-gate-welcome-message` can tell a player where they arrived.

[Full changelog](https://github.com/khanjal/Wormhole-X-Treme/blob/main/CHANGELOG.md#1100-2026-10-12)
````

Spigot update message:

```
[B]Upgrading from 1.9 - one thing may change.[/B] The [ICODE]pegasus[/ICODE] and [ICODE]chevron[/ICODE] dial spins have swapped: a gate, group or [ICODE]gate-dial-spin[/ICODE] set to either now dials the other way, so set it to the other name to keep the old look. A new [ICODE]config.yml[/ICODE] is laid out commonest first; an existing file keeps its own order. Everything else new is off until you turn it on, except the tunnel ([ICODE]gate-view-funnel[/ICODE], which only matters with [ICODE]gate-view[/ICODE] on) and clickable previews ([ICODE]clickable-chat[/ICODE]). Coming from 1.8 or earlier? Each release you skipped has notes that still apply. Installing for the first time? None of this applies. Drop the jar in and start.

[LIST]
[*][B]See where a gate goes[/B], as an experiment: [ICODE]gate-view[/ICODE] draws the far side behind an open gate, or clears the horizon to show it, and a big gate dialling a small one shows it down a tunnel.
[*][B]Gates ripple[/B]: [ICODE]gate-ripple[/ICODE] sends rings across the horizon as something goes through and while somebody is near.
[*][B]Creatures and players in the view[/B]: [ICODE]mirror-show-entities[/ICODE] and [ICODE]mirror-show-players[/ICODE] show who and what is in a mirror's or an open gate's far room, as copies only the viewer sees.
[*][B]BlueMap, squaremap and Pl3xMap[/B] join Dynmap: gates, rings, public beam destinations and mirrors on each map. Set [ICODE]bluemap-enabled[/ICODE], [ICODE]squaremap-enabled[/ICODE] or [ICODE]pl3xmap-enabled[/ICODE].
[*][B]Build previews have buttons[/B]: after [ICODE]gate build[/ICODE], each action is clickable. [ICODE]show-gate-welcome-message[/ICODE] can tell a player where they arrived.
[/LIST]

[URL=https://github.com/khanjal/Wormhole-X-Treme/blob/main/CHANGELOG.md#1100-2026-10-12]Full changelog[/URL]
```

## 1.9.0

Released 2026-10-03.

| Field | Value |
|---|---|
| Version (all three sites) | `1.9.0` |
| Title (Spigot update title, Modrinth version title) | `1.9.0 - WorldGuard flags, Dynmap markers and an update check` |
| Minecraft (Modrinth game versions, Hangar platform versions) | 1.20 – 26.3 |

Modrinth and Hangar changelog:

````markdown
**Upgrading from 1.8 — nothing you have to do.** The new integrations are off until you turn them on. The update check is on: at startup it looks online for a newer release, sending only the plugin and Minecraft versions, and `update-check: false` turns it off. With `same-world-only` on, carts, mobs, items and arrows now stay in their own world too. Coming from 1.7? 1.8.0's notes still apply. Installing for the first time? Drop the jar in and start.

- **WorldGuard regions can refuse gates**: `wormhole-build` and `wormhole-use` flags stop gates being built or used in a region. Set `worldguard-enabled: true` and restart.
- **Gates, rings, public beam destinations and mirrors on a Dynmap web map**, each as its own layer. Set `dynmap-enabled: true`. On 1.21.11 use Dynmap 3.8; there is no Dynmap for 26.x yet.
- **A newer release is announced at startup**, in the console and to ops as they join. Nothing is downloaded.
- **`same-world-only` now keeps carts, mobs, items and arrows in their own world too**, and ten settings that waited for a restart now apply at once from `/wormhole config`.
- **Gate fixes**: thrown and dispensed items go through an open gate, tipped arrows keep their effect, and a cart stops at the face of a shut iris instead of rolling into it.

[Full changelog](https://github.com/khanjal/Wormhole-X-Treme/blob/main/CHANGELOG.md#190-2026-10-03)
````

Spigot update message:

```
[B]Upgrading from 1.8 - nothing you have to do.[/B] The new integrations are off until you turn them on. The update check is on: at startup it looks online for a newer release, sending only the plugin and Minecraft versions, and [ICODE]update-check: false[/ICODE] turns it off. With [ICODE]same-world-only[/ICODE] on, carts, mobs, items and arrows now stay in their own world too. Coming from 1.7? 1.8.0's notes still apply. Installing for the first time? Drop the jar in and start.

[LIST]
[*][B]WorldGuard regions can refuse gates[/B]: [ICODE]wormhole-build[/ICODE] and [ICODE]wormhole-use[/ICODE] flags stop gates being built or used in a region. Set [ICODE]worldguard-enabled: true[/ICODE] and restart.
[*][B]Gates, rings, public beam destinations and mirrors on a Dynmap web map[/B], each as its own layer. Set [ICODE]dynmap-enabled: true[/ICODE]. On 1.21.11 use Dynmap 3.8; there is no Dynmap for 26.x yet.
[*][B]A newer release is announced at startup[/B], in the console and to ops as they join. Nothing is downloaded.
[*][B][ICODE]same-world-only[/ICODE] now keeps carts, mobs, items and arrows in their own world too[/B], and ten settings that waited for a restart now apply at once from [ICODE]/wormhole config[/ICODE].
[*][B]Gate fixes[/B]: thrown and dispensed items go through an open gate, tipped arrows keep their effect, and a cart stops at the face of a shut iris instead of rolling into it.
[/LIST]

[URL=https://github.com/khanjal/Wormhole-X-Treme/blob/main/CHANGELOG.md#190-2026-10-03]Full changelog[/URL]
```

## 1.8.1

Released 2026-09-28.

| Field | Value |
|---|---|
| Version (all three sites) | `1.8.1` |
| Title (Spigot update title, Modrinth version title) | `1.8.1 - Rings and mirrors from command blocks; travel fixes` |
| Minecraft (Modrinth game versions, Hangar platform versions) | 1.20 – 26.3 |

Modrinth and Hangar changelog:

````markdown
**Upgrading from 1.8.0 — nothing you have to do.** Coming from 1.7? 1.8.0's notes still apply. Installing for the first time? Drop the jar in and start.

- **Rings and mirrors from the console and command blocks**: `ring build`, `ring fire` and `mirror create` take a world and coordinates, and `~` coordinates count from the command block, so a map's command blocks keep working wherever it is pasted.
- **Nothing goes through a gate until its kawoosh is over.**
- **A following pet comes with its owner into another world**, and a ridden boat, cart or mount goes through a gate or ring on Paper 1.20.4.
- **An item dropped into a gate comes out below the far gate**, not thrown several blocks from it.
- A ring's transport now flashes glowstone by default. An existing `config.yml` and existing rings keep the redstone lamp.

[Full changelog](https://github.com/khanjal/Wormhole-X-Treme/blob/main/CHANGELOG.md#181-2026-09-28)
````

Spigot update message:

```
[B]Upgrading from 1.8.0 - nothing you have to do.[/B] Coming from 1.7? 1.8.0's notes still apply. Installing for the first time? Drop the jar in and start.

[LIST]
[*][B]Rings and mirrors from the console and command blocks[/B]: [ICODE]ring build[/ICODE], [ICODE]ring fire[/ICODE] and [ICODE]mirror create[/ICODE] take a world and coordinates, and [ICODE]~[/ICODE] coordinates count from the command block, so a map's command blocks keep working wherever it is pasted.
[*][B]Nothing goes through a gate until its kawoosh is over.[/B]
[*][B]A following pet comes with its owner into another world[/B], and a ridden boat, cart or mount goes through a gate or ring on Paper 1.20.4.
[*][B]An item dropped into a gate comes out below the far gate[/B], not thrown several blocks from it.
[*]A ring's transport now flashes glowstone by default. An existing [ICODE]config.yml[/ICODE] and existing rings keep the redstone lamp.
[/LIST]

[URL=https://github.com/khanjal/Wormhole-X-Treme/blob/main/CHANGELOG.md#181-2026-09-28]Full changelog[/URL]
```

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
