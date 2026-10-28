# Changelog

All notable changes to this project are documented in this file.

One line an entry, under the subsystem it belongs to: what changed, and the setting or step an
operator or a player needs. No paragraphs, no why, no limits and no numbers; the detail lives in
[docs/](docs/) beside the code, linked from the entry when it helps. A release nobody can scroll
through is a release nobody reads.

Releases 0.3 to 0.854 (January to May 2011) are the original plugin's notes, kept verbatim:
somebody else's release notes, and the record of where this fork came from.

**Releases:**

- [1.13.0 (unreleased)](#1130-unreleased)
- [1.12.0 (2026-10-25)](#1120-2026-10-25)
- [1.11.0 (2026-10-17)](#1110-2026-10-17)
- [1.10.0 (2026-10-12)](#1100-2026-10-12)
- [1.9.0 (2026-10-03)](#190-2026-10-03)
- [1.8.1 (2026-09-28)](#181-2026-09-28)
- [1.8.0 (2026-09-26)](#180-2026-09-26)
- [1.7.1 (2026-09-19)](#171-2026-09-19)
- [1.7.0 (2026-09-19)](#170-2026-09-19)
- [1.6.0 (2026-09-16)](#160-2026-09-16)
- [1.5.0 (2026-09-09)](#150-2026-09-09)
- [1.4.0 (2026-09-05)](#140-2026-09-05)
- [1.3.0 (2026-09-03)](#130-2026-09-03)
- [1.2.0 (2026-09-02)](#120-2026-09-02)
- [1.1.0 (2026-09-01)](#110-2026-09-01)
- [1.0.0 (2026-05-14)](#100-2026-05-14)
- [0.854 (5/17/11 @ 16:13 PST)](#0854-51711--1613-pst)
- [0.853 (5/13/11 @ 23:05 PST)](#0853-51311--2305-pst)
- [0.852 (5/12/11 @ 07:55 PST)](#0852-51211--0755-pst)
- [0.851 (5/10/11 @ 21:56 PST)](#0851-51011--2156-pst)
- [0.850 (5/5/11 @ 16:15 PST)](#0850-5511--1615-pst)
- [0.833 (4/9/11 @ 23:36 PST)](#0833-4911--2336-pst)
- [0.832 (4/5/11 @ 15:04 PST)](#0832-4511--1504-pst)
- [0.831 (4/4/11 @ 23:13 PST)](#0831-4411--2313-pst)
- [0.830 (4/4/11 @ 01:12 PST)](#0830-4411--0112-pst)
- [0.821 (3/30/11 @ 17:42 PST)](#0821-33011--1742-pst)
- [0.820 (3/29/11 @ 17:31 PST)](#0820-32911--1731-pst)
- [0.812 (3/23/11 @ 15:17 PST)](#0812-32311--1517-pst)
- [0.811 (3/21/11 @ 20:27 PST)](#0811-32111--2027-pst)
- [0.810 (3/20/11 @ 00:18 PST)](#0810-32011--0018-pst)
- [0.801 (3/15/11 @ 22:33 PST)](#0801-31511--2233-pst)
- [0.800 (3/9/11 @ 23:33 PST)](#0800-3911--2333-pst)
- [0.755 (3/4/11 @ 16:51 PST)](#0755-3411--1651-pst)
- [0.754 (3/3/11 @ 00:54 PST)](#0754-3311--0054-pst)
- [0.753 (3/2/11 @ 19:27 PST)](#0753-3211--1927-pst)
- [0.752 (2/28/11 @ 20:57 PST)](#0752-22811--2057-pst)
- [0.751 (2/25/11 @ 07:53 PST)](#0751-22511--0753-pst)
- [0.750 (2/22/11 @ 22:19 PST)](#0750-22211--2219-pst)
- [0.741 (2/21/11 @ 21:48 PST)](#0741-22111--2148-pst)
- [0.740 (2/21/11 @ 05:45 PST)](#0740-22111--0545-pst)
- [0.736 (2/20/11 @ 03:20 MST)](#0736-22011--0320-mst)
- [0.735 (2/19/11 @ 03:40 PST)](#0735-21911--0340-pst)
- [0.730 (2/16/11 @ 20:40 MST)](#0730-21611--2040-mst)
- [0.727 (2/16/11 @ 13:39 PST)](#0727-21611--1339-pst)
- [0.726 (2/15/11 @ 05:40 PST)](#0726-21511--0540-pst)
- [0.725 (2/13/11 @ 21:15 MST)](#0725-21311--2115-mst)
- [0.71 (2/12/11 @ 22:42 MST)](#071-21211--2242-mst)
- [0.70 (2/12/11 @ 04:10 MST)](#070-21211--0410-mst)
- [0.67 (2/7/11 @ 18:57 MST)](#067-2711--1857-mst)
- [0.66 (2/7/11 @ 15:31 MST)](#066-2711--1531-mst)
- [0.65 (2/7/11 @ 12:10 MST)](#065-2711--1210-mst)
- [0.62 (2/5/11 @ 10:37 MST)](#062-2511--1037-mst)
- [0.61 (2/4/11 @ 15:41 MST)](#061-2411--1541-mst)
- [0.60 (2/4/11 @ 15:02 MST)](#060-2411--1502-mst)
- [0.59 (2/2/11 @ 00:07 MST)](#059-2211--0007-mst)
- [0.55 (2/1/11 @ 13:10 MST)](#055-2111--1310-mst)
- [0.51 (1/31/11 @ 11:22 MST)](#051-13111--1122-mst)
- [0.50 (1/30/11)](#050-13011)
- [0.39 (1/27/11)](#039-12711)
- [0.32 (1/26/11)](#032-12611)
- [0.31 (1/26/11)](#031-12611)
- [0.3 (1/26/11)](#03-12611)

## 1.13.0 (unreleased)

### For server admins

**Added**

- **`wormhole.build.<shape>` lets a rank build one gate shape** without `wormhole.build`, which stays the wildcard for every shape, so nothing changes until a server leaves it off a rank. The node is the shape's name in lower case with anything but a letter, digit or underscore made an underscore (`wormhole.build.grand_gate`); each is declared to the server when the shapes load, so a permissions plugin lists them. `wormhole.use.<shape>` is not part of this. A server that gave a rank `wormhole.build.*` (which never included `wormhole.build` itself, so built nothing) now lets it build every shape ([#631](https://github.com/khanjal/Wormhole-X-Treme/issues/631)).
- **`/wormhole complete <name>` without `net=` no longer refuses a builder holding only `wormhole.build`.** A network left out was read as a network called "", which only `wormhole.network.build.` admitted, so a player who was not an operator had to type `net=Public`; it is the Public network now, for `/wormhole gate build` without `net=` as well.
- **`max-gates-per-player` limits how many gates one player may own** (default `0`, no limit, so nothing changes on upgrade). A player at the limit is refused another, before any fare, when the design is found, on `gate complete` and on `gate preview place`, which then places nothing. `wormhole.gates.max.<n>` gives a group its own limit (the highest a player holds wins, up or down), `wormhole.gates.unlimited` lifts it (operators have it), and `/wormhole gate list` ends with "Gates owned: 2. Your limit: 5." for a limited player. Only gates that stand count. Handing a gate to another player with `owner` is not held to the new owner's limit ([#633](https://github.com/khanjal/Wormhole-X-Treme/issues/633)).
- **A gate or a shape can have its own use cost.** `economy-use-cost` charged every gate alike. `/wormhole gate edit <gate> cost <amount>` gives one gate a cost, `USE_COST=<amount>` in a shape file gives every gate of that shape one, and `default` hands a gate back. The gate's own wins, then its shape's, then the setting; `0` is free and only an unset cost falls through. The fare is the entered gate's, asked before the trip and taken after it as before ([#632](https://github.com/khanjal/Wormhole-X-Treme/issues/632)).
- **`/wormhole gate edit <gate> view <horizon|behind|open|default>` gives a gate its own view level**, which wins over `gate-view` even for `horizon` at a server on `open` ([#682](https://github.com/khanjal/Wormhole-X-Treme/issues/682)).
- **`/wormhole mirror debug <gate>` says how a gate's view is being drawn**: the `gate-view` level, and whether the view is drawn and, if not, why ([#685](https://github.com/khanjal/Wormhole-X-Treme/issues/685)).

**Changed**

- **Captures move to `data/captures/`**, `gates/` for a gate's and `mirrors/` for a mirror's, so `data/gates/` holds gate files only ([#655](https://github.com/khanjal/Wormhole-X-Treme/issues/655)). They are moved the first time they are needed, and nothing is overwritten. A script reading `data/gates/captures/` or `data/mirror/captures/` must look in the new folders.
- **`gate-view: behind` leaves a gate with a lava or opaque horizon as it is**; `open` clears it for the view ([#684](https://github.com/khanjal/Wormhole-X-Treme/issues/684)).
- **A gate's view is retaken only from chunks that are loaded.** The retake every ten minutes or so at a gate somebody stands at read the far side through `getChunkAt`, which loads a chunk the server had let go of and generates one never visited. It now starts only when the whole box is loaded, and gives up, keeping the old capture, if a chunk is let go meanwhile. The first capture of a gate still loads what it must, since nothing can be drawn until it exists ([#683](https://github.com/khanjal/Wormhole-X-Treme/issues/683)).
- **`gate-dial-spin` defaults to `none`**, as the original plugin dialled: no ring light until a gate, group or the setting asks for one. A `config.yml` keeps its value, unless it is still exactly as 1.10 or earlier wrote it, which is rewritten and gets `none`; `/wormhole config gate-dial-spin top` brings back the 1.12 default ([#697](https://github.com/khanjal/Wormhole-X-Treme/issues/697)).

**Fixed**

- **A sign gate dials the gate its sign shows.** The sign's choice was kept as a place in a list, saved only when the server stopped cleanly, and `/wormhole refresh` dropped it, so a gate came back dialling the first one in the list while its sign, which lives in the world, still named another. It is kept by the gate's name as well now, saved the moment the sign is clicked, and kept by a refresh; a name no longer in the list falls back to the old place ([#706](https://github.com/khanjal/Wormhole-X-Treme/issues/706)).

## 1.12.0 (2026-10-25)

### For server admins

**Added**

- **`/wormhole config -reload` reads `config.yml` again** after it is edited or deployed, without a restart: it lists each setting it changed, names any value it refused and keeps the old one, and a file that cannot be read changes nothing. It does not write the file, apart from what turning `gate-material-groups-autodiscover` on does, as with the command; edited material groups still wait for a restart ([#634](https://github.com/khanjal/Wormhole-X-Treme/issues/634)). [Guide](docs/guide/SERVER.md#configuration)
- **Chat messages are in each player's client language**, falling back to `server-language` (default `en`, also the language of messages to the console and command blocks) and then English, with a `lang/<code>.yml` of your own in the plugin folder read over the plugin's; the guide lists which messages are translatable so far (every command usage and help line is), more follow, and sign text stays English ([#26](https://github.com/khanjal/Wormhole-X-Treme/issues/26)). [Guide](docs/guide/TRANSLATING.md)
- **`/wormhole lang export [code]` writes a starter lang file**: with no code, `lang/en.yml` with every message commented out, to reword only the lines you uncomment; with a code such as `de`, the English to translate. It never replaces a file already there, and needs `wormhole.config` ([#26](https://github.com/khanjal/Wormhole-X-Treme/issues/26)). [Guide](docs/guide/TRANSLATING.md#rewording-the-english)
- **`/wormhole gate shapes disable <shape>` and `enable`** stop and allow building a gate shape without deleting its file; gates already standing keep working. The choice is kept in `shapes/gate/disabled-shapes.txt` and survives editing, resaving and updating the shape ([#636](https://github.com/khanjal/Wormhole-X-Treme/issues/636)).

**Fixed**

- **`gate shapes validate` and `reload` refuse a name with `/`, `\` or `:` in it**, so they only read files in the shapes folder ([#644](https://github.com/khanjal/Wormhole-X-Treme/issues/644)).
- **`log-level: off` no longer stops the plugin loading**: a bare `off` is the level `OFF`, `/wormhole config log-level off` is written back in quotes, and a value that is no level is the default, said once ([#647](https://github.com/khanjal/Wormhole-X-Treme/issues/647)).
- **A doubled apostrophe in the mirror capture messages** ("'museum''s room") is a single one now.
- **A far-side capture is logged as "Captured world ..."**, and retaking one is logged at `FINE`.

### Stargates

- `min-gate-distance` (default 0, off) refuses a gate built closer than that to another in its world, before any fare, naming the nearest; `wormhole.build.mindistance.bypass` exempts ([#635](https://github.com/khanjal/Wormhole-X-Treme/issues/635)).

### Internals

- The configuration, the gate walk-through, the ring step handling, the cell keys and the occlusion functions moved out of their large classes into smaller ones; nothing a player or admin sees changes ([#430](https://github.com/khanjal/Wormhole-X-Treme/issues/430)).
- Javadoc `@param` tags that only repeated the parameter name (`@param gate` / `the gate`) are gone ([#50](https://github.com/khanjal/Wormhole-X-Treme/issues/50)).
- Collections are built with the diamond (`new ArrayList<>()`) instead of repeating their type arguments, in `src/main` and the tests ([#50](https://github.com/khanjal/Wormhole-X-Treme/issues/50)).
- Chat and sign colours come from the plugin's own colour table rather than Bukkit's `ChatColor`; nothing a player sees changes ([#476](https://github.com/khanjal/Wormhole-X-Treme/issues/476)).
- On Paper, ring countdowns and mirror names go above the hotbar through Paper's own action bar rather than Spigot's. Spigot keeps its own ([#476](https://github.com/khanjal/Wormhole-X-Treme/issues/476)).
- On Paper, the plugin's version is read from Paper's plugin meta, and Freya and window stand-ins are named through Paper's own name components. Spigot keeps Bukkit's ([#476](https://github.com/khanjal/Wormhole-X-Treme/issues/476)).
- On Paper, gate and dial signs are written and read through Paper's own sign line components. Spigot keeps Bukkit's ([#476](https://github.com/khanjal/Wormhole-X-Treme/issues/476)).
- On Paper, the name over a far player's stand-in is read from Paper's own display name component. Spigot keeps Bukkit's ([#476](https://github.com/khanjal/Wormhole-X-Treme/issues/476)).
- The Paper builds list deprecation warnings in `src/main` again; the tests' stay hidden there ([#476](https://github.com/khanjal/Wormhole-X-Treme/issues/476)).

## 1.11.0 (2026-10-17)

### For server admins

**Added**

- Anonymous usage counts to bStats also say which gate shapes and portal materials are in use, the gate view, ripple, sound and iris settings, and which optional plugins are enabled. Nothing that names a player, gate or place. [Guide](docs/guide/SERVER.md#metrics)

**Changed**

- **`ring-animation-speed` replaces the seven ring timings**: 1.0 is the old timing, 2.0 twice as fast, 0.5 half as fast, and nothing outside that range; the settle, hold and lingering lights can no longer be 0, and are 10 ticks at 2.0.
- **`beam-animation-speed` replaces the six beaming timings** the same way.
- **`view-depth` replaces `gate-view-depth`, `gate-view-full-depth` and `mirror-view-depth`**: one depth for a mirror's room and a gate's view; a file without it takes the shallower of `mirror-view-depth` and `gate-view-full-depth`, so a lowered gate depth (0 meaning its first step) now shallows mirrors too, and a gate's first step is otherwise back at 32.
- **A `config.yml` holding a removed setting still loads**: the line is ignored and left as it is, and the log names them at startup.
- **A `config.yml` still exactly as an earlier release wrote it is replaced** with the new grouped layout and its new defaults, the old one kept as `config.yml.bak`; a value that is not one of those defaults leaves it as it is.
- The comment on `permissions-support-disable` says what it does: gates ignore their permission nodes even with a Vault permission provider installed ([#475](https://github.com/khanjal/Wormhole-X-Treme/issues/475)).

**Removed**

- **`mirror-proximity-ticks`, `entity-scan-interval-ticks` and `gate-sound-ambient-ticks`**: the sweeps keep their old default pace.
- **`gate-iris-step-ticks`, `gate-iris-sweep-max-ticks` and `gate-iris-horizon-ticks`**: an iris crosses as it did by default; `gate-iris-animation` stays.
- **`gate-arrival-splash-ticks`**: the arrival splash always shows, for as long as it did by default.
- **`ring-max-link-height` and `ring-default-material`**: rings reach a whole world's height, and a ring whose slab is unknown is drawn in smooth stone.
- **`permissions-auto-fallback`**: with no Vault permissions provider the simple fallback is always on, so a server on a Bukkit-permissions plugin without Vault that had set it `false` is now in simple mode.
- **`mirror-fog-at-depth`**: a mirror no longer pulls a viewer's fog in, and the code behind it is gone with the `fog` line in `mirror debug`.

### Stargates

**Added**

- Gates the same size, or a small gate dialling a big one, can be seen down a straight tunnel too, and every tunnel runs straight before it narrows, longer for a bigger gate ([#587](https://github.com/khanjal/Wormhole-X-Treme/issues/587)). Off until `gate-view-funnel-min-length` is set. [Details](docs/GATES.md)

**Changed**

- Every message to a player is coloured by one shared palette ([#438](https://github.com/khanjal/Wormhole-X-Treme/issues/438)): `/wx list` shows gate and network names in aqua with grey separators, a transport ring that turns you away opens with `:: error ::` like a gate does, and the lines after a valid design or locked chevrons say what to type next in the same colours, with the optional `[idc=IDC] [net=NET]` part now grey rather than gold.
- Every `gate`, `mirror`, `config` and other admin command answers with `::` or `:: error ::` and the same colours, a gate name it cannot find gets one reply saying `/wormhole list` shows them, and a list of options goes on a line of its own; `/wormhole owner` now reads `Abydos is now owned by Ada.` and `/wormhole remove` `Removed Abydos.` ([#438](https://github.com/khanjal/Wormhole-X-Treme/issues/438)).
- The last plain lines a gate traveller saw open with `::` too: a dial sign click reads `Dialer set to Abydos.`, a timeout `Gate Abydos timed out and deactivated.`, a gate cooldown is one line, `You must wait 28 seconds before using a stargate again.`, and a shut far iris reads `The far gate's iris is closed.` ([#438](https://github.com/khanjal/Wormhole-X-Treme/issues/438)).

**Fixed**

- Behind an open gate, shutting or opening its iris no longer covers your wormhole for a moment before moving the iris beyond it: the wormhole stays in the ring and the iris sweeps in a block further off ([#429](https://github.com/khanjal/Wormhole-X-Treme/issues/429)). [Guide](docs/guide/GATES.md#how-it-arrives)

### Transport rings and beaming

**Changed**

- Every `ring` and `beam` reply opens with `::` or `:: error ::` and colours names, blocks, numbers and what to type, as gates do; usage lines read `Usage: /wormhole ring fire <id>|<world> <x> <y> <z>`, beam names are no longer in quotes, and a beam on cooldown says how long is left in one line ([#438](https://github.com/khanjal/Wormhole-X-Treme/issues/438)).

### Performance

- A mirror's or gate's view no longer stalls the server while somebody looks into it ([#609](https://github.com/khanjal/Wormhole-X-Treme/issues/609)). [Details](docs/MIRRORS.md#how-the-room-is-built)
- A mirror's or gate's view no longer makes the server hitch the first time it is drawn after a restart or a while unwatched: its capture is read off the disk off the main thread, the banner or horizon showing until it is in ([#528](https://github.com/khanjal/Wormhole-X-Treme/issues/528)). [Details](docs/GATES.md#seeing-through-a-gate-experimental)
- Mirror and gate captures are kept in sections with palettes, as the game keeps chunks, so they take less memory and disk and read faster ([#525](https://github.com/khanjal/Wormhole-X-Treme/issues/525)). Older capture files still load and are rewritten when next taken; going back to an earlier release logs each new file as unreadable once and takes that view again. [Details](docs/MIRRORS.md#how-a-capture-is-kept)

### Internals

- Code nothing ran any more is gone: a flat-file settings writer and reader, empty shutdown hooks, and a gate-facing helper nothing called ([#475](https://github.com/khanjal/Wormhole-X-Treme/issues/475)).

## 1.10.0 (2026-10-12)

### For server admins

**Added**

- Gates, rings, public beam destinations and quantum mirrors show on [BlueMap](https://bluemap.bluecolored.de). Off until `bluemap-enabled` is set. [Guide](docs/guide/SERVER.md#bluemap)
- The same marks show on [squaremap](https://modrinth.com/plugin/squaremap). Off until `squaremap-enabled` is set. [Guide](docs/guide/SERVER.md#squaremap)
- The same marks show on [Pl3xMap](https://modrinth.com/plugin/pl3xmap). Off until `pl3xmap-enabled` is set. [Guide](docs/guide/SERVER.md#pl3xmap)
- Mobs in a mirror's or an open gate's far room show through it ([#296](https://github.com/khanjal/Wormhole-X-Treme/issues/296)). Off until `mirror-show-entities` is set; the chunks in front of the far side are held loaded (`mirror-entity-load-radius`). [Details](docs/MIRRORS.md#seeing-creatures)
- Players in that far room show too. Off until `mirror-show-players` is set, and it needs `mirror-show-entities`. The skin is the real one, so leave it off on a server that hides identities.

**Changed**

- A new `config.yml` is laid out commonest first, with a "start here" list and sub-headings ([#586](https://github.com/khanjal/Wormhole-X-Treme/issues/586)). An existing file keeps its own order. [Guide](docs/guide/SERVER.md#configuration)

### Stargates

**Added**

- An open gate's event horizon can ripple ([#579](https://github.com/khanjal/Wormhole-X-Treme/issues/579)). Off until `gate-ripple` is set. [Guide](docs/guide/GATES.md#the-ripple)
- An open gate can show where it goes, as an experiment ([#516](https://github.com/khanjal/Wormhole-X-Treme/issues/516)). Set `gate-view` to `behind` or `open`; the default is `horizon`.
- At `gate-view: open` the horizon clears only for whoever is drawn the view; everyone else sees the gate's own portal material.
- A big gate dialling a smaller one shows the view at the end of a tunnel ([#583](https://github.com/khanjal/Wormhole-X-Treme/issues/583)). On by default; turn off with `gate-view-funnel: false`, and `gate-view-funnel-length` sets the longest tunnel.
- Gate preview actions can be clicked. On by default; `clickable-chat: false` turns it off.
- A player coming out of a gate can be told which gate they arrived at. Off until `show-gate-welcome-message` is set.

**Changed**

- The `pegasus` and `chevron` dial spins have swapped. Set `gate-dial-spin` to the other name to keep the old look.

**Fixed**

- Walking round a gate while its iris opens or closes no longer leaves a block on your side that is not there ([#447](https://github.com/khanjal/Wormhole-X-Treme/issues/447)).
- A build preview's see-through iris no longer shows empty cells part way through a sweep ([#432](https://github.com/khanjal/Wormhole-X-Treme/issues/432)).

### Internals

- The code that draws a far side through an opening moved to `model/window`, no longer named for mirrors (#522).
- A gate's view is drawn from a window source, not as a stand-in mirror.
- The build no longer fails at random with a corrupt-zip error.

[Back to top](#changelog)

## 1.9.0 (2026-10-03)

### For server admins

**Added**

- **[WorldGuard](https://enginehub.org/worldguard) regions can refuse gates**, with two flags:
  `wormhole-build` stops gates being built there, and `wormhole-use` stops them being dialled
  from or travelled through, at either end. A flag only takes away: a player still needs the
  usual permission, and WorldGuard's region bypass gets past the flag. Off until
  `worldguard-enabled` is set; turning it on takes a restart. Rings, beams and mirrors are not covered
  yet. See [the guide](docs/guide/SERVER.md#worldguard).
- **Gates, rings, public beam destinations and quantum mirrors show on a
  [Dynmap](https://www.spigotmc.org/resources/dynmap.274/) web map**, each as its own layer. A
  gate lights up while its wormhole is open, with a line to the gate it joins. Off until
  `dynmap-enabled` is set; `map-show-gates`, `-rings`, `-beams` and `-mirrors` leave a layer
  out, and `map-show-iris-gates: false` hides gates with an iris code. Players' private beam
  places are never shown. 1.21.11 needs Dynmap 3.8, and no Dynmap runs on 26.x yet. See
  [the guide](docs/guide/SERVER.md#dynmap).
- **A newer release is announced at startup**: one console line with where to get it, and a line
  to players with `wormhole.update.notify` (ops) as they join. Nothing is downloaded, and nothing
  is said to a server running something newer. `update-check: false` turns it off.

### Server

- **Tested on Minecraft 1.21.8.** CI builds against Spigot's and Paper's 1.21.8 API and boots a
  Paper 1.21.8 server, as for the other eleven versions.

**Fixed**

- **Every setting changed with `/wormhole config` applies at once, as the guide says.** Ten waited
  for a restart: `gate-sound-ambient-ticks`, `entity-scan-interval-ticks`, `mirror-proximity-ticks`,
  `gate-iris-horizon-ticks`, `ring-reach`, `ring-max-ceiling-drop`, `economy-enabled`,
  `placeholders-enabled`, `permissions-auto-fallback` and `gate-material-groups-autodiscover`.

**Removed**

- **`help-support-disable`**, which nothing read: the Help plugin it turned off is long gone. A line
  for it in `config.yml` is ignored and left where it is.

### Stargates

**Fixed**

- **An item thrown or dispensed into an open gate goes through it.** Most flew across the opening
  between two entity sweeps and landed behind the ring; a shut iris destroys them as before.
- **The build guide takes the frame block in an `[S:C]` chevron, as detection does.** A gate built
  that way was detected but never shown as built, `gate preview needs` named only the chevron
  block, and `gate regen -fill` refused to fill a gate with one.
- **The player who pulls an iris lever sees the iris sweep in, as everyone else does.** Their own
  arm swing redrew the whole iris a tick later, over the sweep.
- **A detector rail wired to a gate dials it on Paper 1.21.11.** Paper reports a rail's press as
  a current of 15 going to 15, which read as no change, so a cart over the rail did nothing.
- **`same-world-only` keeps everything in its own world, not only players on foot.** A minecart
  carried its rider through to the Nether, and a mob, an item or an arrow went too. With it on, a
  gate in another world cannot be dialled, and `/dial` and the dial sign say why. A player walking
  in is told that before any cooldown or iris, and once, not at every block.
- **`/wormhole config` refuses a sign colour that is not one**, and names the sixteen there are.
  `sign-color-gate-name PINK` was accepted, and every sign written after it came out dark aqua.
- **A tipped arrow keeps its effect through a gate, and on Paper a Loyalty trident comes back.** The
  arrow arrived plain, and the trident unenchanted, stuck at the far side. A spectral arrow keeps how long
  it makes its target glow, a thrown potion its contents, and a crossbow firework its stars. On
  Spigot the trident keeps its enchantments but still does not come back, and a firework starts its
  flight over at the far gate.
- **A cart or boat stops at the face of an upright gate's shut iris**, from either side. It used
  to roll half into the iris before it was put back, still with its front inside.

### Transport rings

**Fixed**

- **A ring pair whose slab or pad light this server does not have loads**, drawn in
  `ring-default-material` or lit in `ring-default-light`, and is saved with the name it had. It was
  skipped as unreadable and lost at the next save, and `ring-default-material` was read by nothing.

### Performance

- **A mirror's name above the hotbar is looked for only near a mirror.** Every player in a world
  with one traced a ray each sweep; now only players within a chunk of a mirror's banner do.

### Quantum mirrors

**Fixed**

- **A mirror in a wall more than a block wide keeps its wide far part between wall readings.**
  For four seconds in five it fell back to a half-block one, and `mirror debug` said "wall 0".
- **A view sees six layers into leaves, not through a whole forest.** A capture kept every crown
  in its way; a mirror onto woodland now holds far fewer blocks. Retake one with `mirror set -capture`.
- **Two mirrors made at once on a new server both keep their view after a restart.** One of them
  logged "Could not write mirror capture", and after a restart showed its banner until the view
  was taken again.

### Internals

- **A test fails if Spigot-only API is used outside the classes that isolate it.** CraftBukkit
  has no `spigot()`, `net.md_5` or `org.spigotmc`, and no CI job builds against it.

[Back to top](#changelog)

## 1.8.1 (2026-09-28)

### For server admins

**Added**

- **Rings can be paired and fired from the console and command blocks.**
  `ring build <world> <x1> <y1> <z1> <x2> <y2> <z2>` pairs two laid circles, each named by a block
  inside it, and says where each end puts an arrival; `ring fire <id>` or
  `ring fire <world> <x> <y> <z>` sets a pair off with nobody in it. A player needs
  `wormhole.ring.admin`. A pair built this way has no owner, so it is public.
- **A mirror can be made from the console and command blocks**:
  `mirror create <name> <world> <x> <y> <z>` makes the wall banner there one.
- **Coordinates in `gate build`, `ring build`, `ring fire` and `mirror create` may be `~`**, counted
  from the command block or player that runs them, so a map's command blocks keep working wherever
  it is pasted. Under `/execute`, they still count from whoever ran it.

### Stargates

**Fixed**

- **Nothing goes through a gate until its kawoosh is over.** Players, mounts, mobs, items,
  vehicles and arrows stepping into the opening while the chevrons lock pass through the empty
  frame; the opening no longer shows water before the kawoosh to a player crossing a chunk nearby.
- **A ridden boat, cart or mount goes through a gate or ring on Paper 1.20.4.** It stayed behind
  with its rider aboard while the trip was reported and charged. One that cannot be moved now
  keeps its riders and costs nothing, and a boat or cart's rider pays once it has gone.
- **An item or mob dropped into an opening comes out below the far gate**, not thrown up to six
  blocks out of it: its fall no longer turns into a sideways speed on the way through.

### Quantum mirrors

**Fixed**

- **`mirror create` aimed off a banner no longer says a banner on a post will do.** It asks for a
  banner hung on a wall, which is the only kind `create` accepts. A mirror already on a post can
  still be renamed.

### Transport rings

**Changed**

- **The transport flash defaults to glowstone**, against the lit redstone lamp pad. A server with a
  `config.yml` keeps `ring-default-flash: REDSTONE_LAMP` until it is changed there, and existing
  rings keep theirs until `/wormhole ring edit [id] flash glowstone` or `reset`.

### Travel

**Fixed**

- **A following pet comes with its owner into another world** by gate, beam, ring or mirror,
  where it used to stay behind.

### Internals

- The travel boot test now also rides a ring pair with an item and a pig, and makes a mirror from
  the console and finds it again after a restart, on Paper 1.20.4, 1.21.11 and 26.3.

[Back to top](#changelog)

## 1.8.0 (2026-09-26)

### For server admins

**Added**

- **Anonymous usage counts go to [bStats](https://bstats.org)**: Minecraft version, server
  software, and how many gates, rings, beams and mirrors, in ranges. No names, coordinates or
  addresses. On by default; `metrics-enabled: false` stops it, and `plugins/bStats/config.yml`
  stops it for every plugin. See [the guide](docs/guide/SERVER.md#metrics).
- **Gate and ring construction can be logged to [CoreProtect](https://www.spigotmc.org/resources/coreprotect.8631/)**,
  so it can be rolled back: signs, levers, `gate remove -destroy`, `regen -fill`,
  `gate preview -place`, and the slabs `ring create` takes and `ring remove` gives back. Off
  until `coreprotect-enabled` is set; a running gate is not logged. See
  [the guide](docs/guide/SERVER.md#coreprotect).
- **Gates can be built and dialled from the console, command blocks and scripts.**
  `gate build <shape> <name> <world> <x> <y> <z> <facing>` builds and completes one with its DHD
  button on that block, and says where its opening and arrivals are; `gate dial <from> <to>` opens a
  wormhole between two named gates. Both need `wormhole.config`, and a player also the usual build or
  dial rights. A gate built this way has no owner
  until `gate edit owner` gives it one; shapes that dial by sign cannot be built this way.
- **A server upgrading from a `Settings.txt` build is told which of its settings to set again.**
  That file is still not read; the first start names only those that exist and differ from
  the default.

**Removed**

- **`plugin.yml` no longer names the 2011 `Permissions` plugin or `WormholeXTremeWorlds` as soft
  dependencies.** Neither did anything, and neither runs on a supported server. Vault and LuckPerms
  are unchanged.

**Fixed**

- **A fresh install loads the example `gate-material-groups` on its first start**, not its second.
  It used to warn that "Shapes framed in OBSIDIAN disagree on their other materials" about config
  nobody had written. A config.yml that cannot be read now falls back to the built-in obsidian
  group too. Nothing to do; an upgraded server was never affected.

### For other plugins

The plugin API is in [docs/API.md](docs/API.md).

**Added**

- **`StargateActivatedEvent` and `StargateShutdownEvent`**, raised when a gate's wormhole
  opens and when it closes, once per end. `StargateShutdownEvent.getReason()` says why:
  `TIMEOUT`, `MANUAL`, `FAR_END`, `REMOVAL` or `PLUGIN_DISABLE`.
- **A PlaceholderAPI expansion**, off until `placeholders-enabled` is set:
  `%wormhole_gates_total%`, `%wormhole_gates_open%`, `%wormhole_gates_owned%` and
  `%wormhole_nearest_gate%`. See [the guide](docs/guide/SERVER.md#placeholders).

### Stargates

**Added**

- **The iris animates**: `sweep` (a ring at a time, rim inwards), `spiral`, `rows` or
  `columns`, or `instant` for the old way, set by `gate-iris-animation`. `gate-iris-step-ticks`
  sets the pace and `gate-iris-sweep-max-ticks` caps a whole crossing (twenty ticks; `0` lifts
  it). Only the picture sweeps; the iris holds from the first tick. Previews sweep too. See
  [the guide](docs/guide/GATES.md#how-it-arrives).
- **A shut iris covers the wormhole instead of replacing it.** The wormhole is drawn a block
  behind, and whichever layer is nearer you takes the ring, swapping as you walk round.
  Behind a stained-glass iris it is drawn in ice (or purple and magenta concrete for
  `nether_portal`), because Minecraft hides a liquid behind a translucent block;
  `gate-iris-horizon-ticks` sets its shimmer and `0` holds it still. Previews do the same.
  Upright gates only.
- **Three more `gate-dial-spin` patterns**: `chase`, `universe` (Destiny's) and `overshoot`.
  See [the guide](docs/guide/GATES.md#dialling).
- **A gate or a material group can pick its own ring pattern and iris style**:
  `gate edit <gate> spin <pattern>` and `gate edit <gate> iris-animation <style>`, or
  `dial-spin:` and `iris-animation:` in a group; `default` follows the server again. New configs
  ship Atlantis on `pegasus` and Universe on `universe`; an existing `config.yml` keeps its
  groups, so add those lines by hand.
- **A gate's owner can set its iris code** with `gate edit <gate> idc`, without
  `wormhole.config`.

**Changed**

- **The default `top` dial rests on the top chevron** for half a second as each one locks, so a
  dial takes about three seconds longer. `gate-dial-spin: chevron` keeps the old pace.
- **The last chevron holds two seconds before the kawoosh**, not one.
- **An upright gate's iris is drawn on clients, not built from blocks.** The opening stays air,
  so a crash leaves nothing behind. A shut iris still refuses players, carts, arrows and dropped
  items, and nobody can build in the opening it covers. A horizontal gate's iris is still real
  blocks, since it is a floor. Gates saved with an iris shut have its blocks removed on first
  load; nothing to do.
- **`gate preview materials` is now `gate preview needs`**, one letter away from
  `gate preview material`. The old name still works.
- **Bundled shapes nobody edited are updated at startup again**, keeping the old copy as
  `<name>.shape.old`. An edited copy is left alone and named in the log.

**Fixed**

- **`gate regen` keeps every `gate edit` setting**: custom materials, woosh depth, redstone
  and an iris shut by default, which it used to drop.
- **Gate settings are saved as they are set** by `gate edit idc`, the iris lever and `owner`,
  `custom`, `portalmaterial`, `irismaterial`, `lightmaterial`, `wooshdepth` and `redstone`.
  They used to wait for shutdown, so a crash lost them. `gate edit group` is saved at all now.
- **An iris shut by default stays that way through a save taken mid-journey.** A gate already
  saved open this way needs its lever pulled once.
- **`force` and `idc -clear` open an iris for good.** Both used to leave it to shut again at the
  end of the next journey, and `idc -clear` could leave it with no way to open. A dial refused
  or failed after opening an iris, near or far, shuts it again.
- **No kawoosh through a closed iris**, on a gate or a preview.
- **`gate-sound-iris-open` and `gate-sound-iris-close` play.** They never did.
- **A `nether_portal` wormhole fills the opening on every gate**; half of them showed a sliver
  seen edge-on. Directional iris blocks, such as logs, are laid in the gate's plane too.
- **`pegasus` steps a glyph at a time and lights each chevron only as it locks.**
- **A cart turned back by a closed far iris lands in front of its own gate**, not in its frame
  or a wall.
- **A lever on the empty iris spot of a gate with no code does nothing, and can be broken.**
- **A gate with no arrival point refuses the trip** instead of throwing on every tick a cart
  rolled into it.
- **A dial sign hung on a placed preview works** once `gate complete` is run. A Horizontal frame
  completes as `HorizontalSignDial` instead of writing its name sign over the dial sign. Gates
  already completed without their sign are not changed.
- **A gate's saved minecart arrival point keeps its pitch and yaw the right way round.**
- **Stopping the server with a wormhole open saves every gate, ring, beam and mirror.** The
  open gate used to throw on its way down, and nothing after it was saved.
- **Riders can no longer dismount inside an open gate on 1.20 through 1.20.3.** Nothing stopped
  them there, and the server logged an error about `EntityDismountEvent` at startup.
- **`gate import` skips a damaged gate and says why**, instead of listing it as `null`, or
  stopping the whole import on one.

### Commands

**Added**

- **`/wormhole` on its own lists its commands by job**, with coloured usage, showing only what
  you can use. `/wormhole gate` does the same for gate verbs.
- **More tab completion**: `gate-iris-animation` styles, `wooshdepth` depths, `owner` players,
  `idc -clear`, `remove -destroy`, and the flat `build` and `regen` complete like their `gate`
  forms.
- **`gate preview material` roles take a dash**: `-frame`, `-chevron`, `-light`, `-portal`,
  `-iris`, `-sign`. The bare word still works.

**Fixed**

- **A mistyped command gets one short usage line for that command**, not the whole block from
  `plugin.yml`. `/dial` too.
- **`gate list` completes networks, not gate names**, and each `gate` verb completes the way the
  command it is short for does.
- **`gate edit <gate> idc` with no code reports the code** instead of clearing it; `-clear`
  clears it.

### For shape authors

**Added**

- **`[S:C]` marks a chevron where the frame material is also accepted**, so a shape can gain
  chevrons without breaking gates already built to it. See [docs/GATES.md](docs/GATES.md).

### Quantum mirrors

**Fixed**

- **A mirror whose capture throws partway is captured again** the next time someone looks at it,
  instead of never updating until a restart.
- **Less of a small mirror's picture lingers outside its opening as you move.** Openings of six
  blocks or fewer redraw twenty times a second rather than ten, inside the same cost cap.

### Transport rings

**Changed**

- **The transport light runs out from the pad putting a traveller back down.**

### Performance

- **Finding the nearest gate walks the list once** instead of copying and sorting it.
  `/wormhole compass` and `%wormhole_nearest_gate%` use it.

### Internals

- **The plugin runs on [MockBukkit](https://mockbukkit.org)'s simulated server** in the Paper
  1.21.11 and 26.2 CI jobs (`mockbukkit` profile, `src/mockbukkit/`, Java 21+). A player's trip
  through a gate, a beam, a ring and a mirror, a pet following through a gate and a beam, the
  iris and its code, and redstone dialling are tested end to end there.

[Back to top](#changelog)

## 1.7.1 (2026-09-19)

**Upgrading: take the new `Massive.shape`, then regenerate each `Massive` gate.**

- Delete `Massive.shape` if you did not edit it, and restart. The startup log names it.
- Run `/wormhole gate regen <gate>` on each `Massive` gate. Its name sign stood inside the ring,
  where a frame block was; regen puts the block back and hangs the sign on the front face.
- A `Massive` gate whose sign was ever taken down has a hole there instead, and regen reports it
  a block short. `/wormhole gate regen <gate> -shape Massive -fill` puts it back.

### Stargates

- **`Massive` is the same on both sides.** Its lamp rings had an extra column of frame on the
  right, and two blocks past the corner at each end of it.
- **`Massive`'s name sign hangs on the front of the gate.** Its `:N` was on the back ring, so the
  sign went into the layer in front of it.
- **`gate regen` takes any sign standing in the gate's frame down and puts the block back**
  before it reads the gate, and says where. Until then the frame came up a block short,
  `OAK_WALL_SIGN` where the ring should be, and the markers were left alone.
- **`gate regen -fill` places the frame blocks a gate is missing**, from its own frame and chevron
  materials, and names each one. At most three, or one in a hundred on a big gate, and only into
  air, water or lava; more than that, or a solid block where the frame goes, and it places nothing
  and says why.
- **Taking a name sign down from the frame puts the frame block back**, rather than leaving air
  where the ring was.
- **`gate preview place` fills in a gate it is laid over**, for a player with `wormhole.config`:
  it places only the blocks that gate is missing, never replacing one, then regenerates the gate,
  keeping its name, owner, network and IDC. Over a ring or two gates it is refused as before.
- **A name sign is never placed over the gate's frame or chevron material.** The gate goes
  without one, and the log says why.
- **`/wormhole gate shapes validate` catches a `:N` with a block in front of it**, a frame,
  chevron or portal cell, where the sign would land inside the gate. `gate shapes reload` refuses
  such a shape, 1.7.0's `Massive` among them; startup still loads it.

### Server

- **`/version WormholeXTreme` says when the jar was built**, in UTC, under the description, so two
  builds of one version can be told apart.

[Back to top](#changelog)

## 1.7.0 (2026-09-19)

**Upgrading: command keywords start with `-`, and the new gate shapes are yours to take.**

- A word that can stand where a name does is an option only with a dash, and no name may start
  with one. Scripts and command blocks need:

  | Was | Now |
  |---|---|
  | `gate remove <gate> -all` | `gate remove <gate> -destroy` |
  | `complete help` | `complete -help` |
  | `custom -clean confirm` | `custom -clean -confirm` |
  | `mirror set [name] stamp\|start\|capture` | `mirror set [name] -stamp\|-start\|-capture` |
  | `mirror set [name] start none` | `mirror set [name] -start -none` |
  | `mirror debug [name] all\|full\|on\|off` | `mirror debug [name] -all\|-full\|-on\|-off` |
  | `beam admin cost <name> default` | `beam admin cost <name> -default` |

- Shape files are never overwritten, so a server keeps 1.6's. The startup log names each bundled
  shape that differs from this version's: delete the ones you did not edit and restart to get the
  show's chevron order, the eighth chevron, the slower pace and the one-layer `Large`.
- Every gate's light order is rebuilt from its shape at startup, so the restart after that brings
  standing gates along. A gate whose frame no longer fits its shape is named in the log; run
  `/wormhole gate regen <gate>`, or `-shape <shape>` for one missing a block or two.
- A three-deep `Large` gate from 1.6 keeps working, but no longer matches `Large`. To keep building
  them, keep 1.6's `Large.shape` under another file name and another `Name=`.
- Gates the legacy importer recorded as `Standard` are found as the shape they really are by
  `gate regen <gate>`.
- `/wormhole gate regen` replaces `regenerate` and `/wormhole refresh`, which both still work.
- New settings are added to an existing `config.yml` at their defaults, under a marked block.

### In this release

- **[Stargates](#stargates)** — the show's dialling: chevrons in order at a pace you can follow,
  an eighth for another world, the inner ring turning, a lock-in sound, and sounds sized to the
  gate. Build previews. `gate regen` finds a gate's real shape.
- **[Travel](#travel)** — pets come with you.
- **[Command options](#command-options)** — keywords start with `-`.

Longer explanations are in [docs/GATES.md](docs/GATES.md) and [docs/guide/](docs/guide/).

### Stargates

**Dialling**

- **Chevrons light in the show's order**: down the right side, up the left, the top one last.
- **An eighth chevron locks when the other gate is in another world**, after the top one. `:L#8`
  marks it, at the bottom of every shipped ring gate but `Minimal`.
- **Pressing a gate's button lights every chevron at once**; `/dial` then darkens them and dials
  the ones it needs in order, with their sounds.
- **Chevrons lock at a pace you can follow**: half a second each on `Standard`, a little longer on
  bigger gates. A shape's `LIGHT_TICKS` and `WOOSH_TICKS` are read at last.
- **The last chevron holds a second and locks in with its own sound** (`gate-sound-lock`).
- **The dialling gate's inner ring turns**: before each chevron locks, a light sweeps half the
  ring to the top chevron, alternating direction. The gate being dialled only lights. `gate-dial-spin`
  picks the pattern: `top`, `chevron`, `lap`, `fill`, `pegasus` or `none`; none changes the dial's pace.
- **A sign dial opens at once** on both ends, with the lock-in sound and no chevron sequence.
- **A gate sounds its size**: deeper and louder on a bigger gate, lighter on a smaller one.
  `SOUND_SCALE` in a shape sets its own.

**Build previews**

- **`/wormhole gate build <shape> [group]` shows a shape full size in front of you**, seen by you
  alone (`wormhole.build.preview`, op by default).
- **`/wormhole gate preview <action>` does everything else to the preview you look at**;
  `gate build` only starts a gate. `clear [-all]` takes previews away.
- **A preview can be dialled, given an iris and redressed**: `activate`, `iris`,
  `material <group>|<role> <block>`, `chevrons`, `dhd`.
- **`materials` lists what it takes, `guide` marks what is left to build, `layer` shows a layer
  at a time**, and looking at a placed DHD button stands the preview on it.
- **`share <player>|-all` shows it to others** (`wormhole.build.preview.share`), and **`place`
  builds it for real** (`wormhole.build.preview.place`).
- **New settings:** `gate-preview-minutes` (10) and `gate-preview-max-blocks` (5000).
- **A Standard gate is previewed the classic way**, its chevrons drawn as frame.

**Regen**

- **`/wormhole gate regen` is the name**, and it now does what `refresh` did: detects the gate
  afresh from its frame, keeping its name, owner, iris code and network. With no gate named, it
  waits for a DHD click. An open or dialling gate is shut down first.
- **It finds the shape a gate really is** when the gate was recorded under the wrong one, and
  `-shape <shape>` takes a named shape if 90% of its frame is standing, listing what is missing.
- **A shape is laid where the gate's frame is**, searching a few blocks round the DHD's layout.
- **It relights a gate's chevrons in its shape's order**, reading no blocks, so `-all` covers
  unloaded gates too. `-all` names the gates it left alone, a line per reason.
- **`-water` clears water or lava stranded in a closed gate's opening.**
- **Its messages pick out the gate, shape, counts and coordinates in colour.**

**Shapes and other changes**

- **`Large` is one layer deep**, as `Standard` is, rather than three.
- **Startup names each bundled shape that differs from this version's**, and rebuilds every gate's
  light order from its shape.
- **A `config.yml` with no `gate-material-groups` gets the example groups** (Atlantis, Universe,
  MilkyWay beside Standard).
- **`/wormhole gate complete -cancel` cancels a waiting completion.**
- **`/wormhole gate edit <gate> owner` with no name reports the owner** instead of clearing it.
- **The entity sweep leaves display entities alone**, so holograms near an open gate stay put.
- **`/wormhole gate import` keeps each gate's owner**, who can now remove it with
  `wormhole.remove.own`, and after a restart. A gate imported by an earlier version has no owner:
  set one with `/wormhole gate edit <gate> owner <player>`.

### Travel

- **A player's pets travel with them** by gate, ring, beam or mirror: tamed wolves, cats and
  parrots within 12 blocks that are not sitting. `pets-follow-owner: false` turns this off.

### Command options

- **Keywords start with `-`** (see Upgrading), and gate, mirror, beam destination and place names
  may not. `-all` and `-clear` are recognised whatever their capitals.

### Quantum mirrors

- **A mirror's view no longer hides and re-shows display entities.**
- **`/wormhole mirror set <name> -start` with no mirror after it is refused.**

### Server

- **CraftBukkit no longer throws on every ring countdown and mirror approach.** It has no
  action bar, so the plugin says so once in the log and goes without.
- **Supported through Minecraft 26.3.** CI builds and tests against Paper at every supported
  version, and against Purpur's newest.
- **`log-level` below `INFO` reaches the console**, as lines marked `[FINE]`, and
  `/wormhole config log-level` takes effect at once.

### Documentation

- **The server guide has an [Upgrading](docs/guide/SERVER.md#upgrading) section**: what the first
  startup after a new jar moves, what it leaves alone in `config.yml` and `shapes/gate/`, and what
  a gate built by an older version wants doing to it. The README points at it, and at
  `gate import` for gates from another fork.
- **`SECURITY.md` says how to report a security problem privately**; the issue chooser links the
  private report form and the maintainer's Discord.
- **The docs show the plugin in game**, with a capture per subsystem on the README and on each
  guide page, and drawn sheets in place of ASCII sketches.
- **The docs say each thing once**: commands, settings and permissions in `docs/guide/` only.
- **The original plugin's 2011 notes are at the end of this file.**

### Internals

- **CI fails a pull request with any open Sonar finding**, and all 41 on main are cleared.
- **The Sonar scan is retried**, so a SonarCloud outage no longer fails a pull request.
- **The mirror window tests draw on stand-ins rather than mocks**, and run in 256 MB where they
  wanted 4 GB. The test fork has a 1 GB ceiling.
- **A Claude Code web session warms the Maven cache as it starts**, so its first
  `mvn test` runs tests instead of downloading the build. Local checkouts are unaffected.
- **`gate import` is tested against a real SQLite database**, with `sqlite-jdbc` as a test-only
  dependency; the jar still uses the server's driver.

[Back to top](#changelog)

## 1.6.0 (2026-09-16)

Sixteen commits' notes had been written into 1.5.0's section as though they had shipped in it, and
they are here instead.

**Upgrading: nothing to do, with five things worth knowing.**

- Files move themselves on the first startup. Gate shapes go from `GateShapes/` to `shapes/gate/`,
  and your gates, rings and beam destinations from `WormholeXTremeDB/` into `data/`. Files are
  moved rather than copied, a file already at the destination wins, every move is logged, and
  nothing is deleted, so putting 1.5.0's jar back finds the old folders as they were.
- `WormholeXTremeDB/` keeps its name, because another fork's SQLite database lives there too and
  `/wormhole gate import` finds it by that name. Only this plugin's own files come out of it.
- If your plugin folder is not `./plugins`, this build reads `config.yml` and the gate shapes from
  the folder the server names, where 1.5.0 read them from a guess. A server that had quietly been
  running on defaults will start reading the file you have been editing.
- New defaults reach only a fresh `config.yml`. Rings now count down for 100 ticks
  (`ring-countdown-ticks`) and rest for 600 (`ring-cooldown-ticks`); an existing file keeps 60 and
  1200 until you change them.
- Minecraft 26.1 and later need the server on Java 25. The plugin itself still runs on Java 17.

### In this release

- **[Quantum mirrors](#quantum-mirrors)** — new, and most of this release. A banner on a wall
  gives way to the room it opens onto, and every mirror is on one network.
- **[Stargates](#stargates)** — `gate validate`, `Even` dropped as a duplicate of `Large`, and
  seven fixes.
- **[Transport rings](#transport-rings)** — ceiling rings in deep rooms, and a room refused when
  it is built rather than when somebody stands in it.
- **[Beaming](#beaming)** — the sequence drawn as a timing strip, which found a wrong number.
- **[Configuration and documentation](#configuration-and-documentation)** — `config.yml` written
  in groups, the four design documents opened with summaries and cut by a fifth, and galleries
  drawn for gates, rings, beams and the looks.
- **[Performance](#performance)** — six things that scaled with how much exists rather than with
  how much is happening.
- **[Internals](#internals)** — one YAML write path and one logger where there were three of
  each, and a long pass over the test suite.

Longer explanations are not here. What a subsystem does and why it is built that way is in
[docs/](docs/): [GATES.md](docs/GATES.md), [RINGS.md](docs/RINGS.md), [BEAMS.md](docs/BEAMS.md)
and [MIRRORS.md](docs/MIRRORS.md), with the how-to in [docs/guide/](docs/guide/).

### Quantum mirrors

A fourth way to travel, and the first one you can see. Design notes in
[docs/MIRRORS.md](docs/MIRRORS.md); how to build one in
[docs/guide/MIRRORS.md](docs/guide/MIRRORS.md).

**Added**

- **A mirror opens onto where it goes.** Walk up to a wall banner, on its side, and the banner is
  gone (on plain 1.20 it stays, in front of the view): an opening its own size shows a room in real
  blocks, with depth as you move, reaching `mirror-view-depth` and nothing past it.
- **Every mirror is on one network.** It shows its own room flipped until somebody right-clicks
  it; a right-click moves it on to the next mirror, a punch goes through to that mirror's banner,
  and it goes back to its own room once nobody is near.
- **One mirror to a world by default** (`mirror-per-world-limit`), on solid wall a block out on
  every side, which cannot be broken while the mirror is there.
- **Two wall banners side by side make one mirror two wide.** Either banner answers a click and a
  traveller lands between them.
- **A mirror's room can end in your own fog** (`mirror-fog-at-depth`, off by default, Paper only).
- **A capture reaches as far as the room's world sends**, so changing the depth draws more or less
  of the same capture and never takes one again.
- **A mirror keeps its far room inside its edges**, and nothing shows past them from an angle.
- **A look for every biome in the game**, and twenty-three more for what a mirror is *for* —
  `exit`, `vault`, `library` and the rest, reached only by name.
- **A mirror can look like where it goes.** `set stamp` reads the far side and paints the banner
  from it: the biome picks the frame, the blocks there become coarse squares.
- **A mirror says what it is above the hotbar** while you look at it from about six blocks, along
  with the world it opens onto (`mirror-approach-message`).
- **`mirror debug`** says one thing a line, fits on a screen of chat with `all` for the rest, and
  tab-completes for whoever may run it.
- **A capture no mirror uses is deleted at startup**, and `mirror list` names each mirror's.
- **`mirror create` says what a mirror needs**: which block of wall is missing, when the wall is a
  block short of two, and when another mirror is near enough that neither can be drawn whole.
- **Four mirror verbs take the banner you are looking at** when you leave the name out.
- **Seven banner patterns came back**, including the only round one.

**Changed**

- **`mirror set` is the one door to what a mirror has.** The top of the command is `create`, `set`,
  `remove` and `list`; `set [name] <stamp|start|capture> ...` holds the rest.
- **`stamp` is about the banner and `capture` is about the room**, and nothing changes either on
  its own. `mode` is gone with the automatic restamping it existed for.
- **A room is streamed in a tick at a time** as a viewer comes into range, and taken back the same
  way, so coming to a mirror no longer freezes a frame. The cap on a room sent whole is gone.
- **A mirror on any wall reaches the full depth**, standing or walking: its room is held whole and
  clipped to each eye.
- **A clipped room's far part is judged for a whole cell of eyes at once**, as wide as the wall
  allows, and stands between small steps; a slow redraw earns a rest before the next.
- **A mirror needs a block of wall round its opening, not two**, and `create` says so when it is
  short of two.
- **The outer half of the wall's edge is a margin** nothing is drawn onto.
- **`mirror-view-depth` is 160 by default**, and a room cut to fit says so in `mirror debug`.
- **`mirror-proximity-radius` is `mirror-proximity-distance`.** An existing `config.yml` keeps its
  value and is rewritten under the new name.
- **A right-click never comes round to a mirror's own room.**
- **A right-click leaves no real banner showing**, not even for a tick.
- **`set stamp` counts the looks** once there are too many to list.
- **Mirror messages are no longer entirely grey.**

**Removed**

- **The cone walk**, and `mirror debug save` with it.
- **Two mirror settings nothing read**: `mirror-capture-radius` and `mirror-allow-same-world`.
- **`mirror set display`**: every mirror is a view now, so it did nothing. An old `Display` line
  is ignored.

**Fixed**

- **A redraw could hang the server for fifteen seconds** while standing on a block boundary.
- **The real banner no longer flickers on a right-click.**
- **A block placed or broken through a mirror's view acted on the real world behind it**, unseen.
- **Scrolling quickly between mirrors could leave part of the last room up** for a few seconds.
- **Changing the fog setting or the depth did nothing for somebody already at a mirror** until
  they walked away and back.
- **A banner on a post could not be named** from right next to it, and naming one now says where
  it has to be clicked.
- **A linked pair of mirrors sent you straight back where you came from.**
- **A mirror another plugin refused** looked exactly like a mirror pointing at itself.
- **Renaming a mirror used to quietly take it apart**, losing its destination and look.
- **A mirror onto the Nether was still dressed as somebody's living room.**

### Stargates

Design notes in [docs/GATES.md](docs/GATES.md), how-to in
[docs/guide/GATES.md](docs/guide/GATES.md).

**Added**

- **`/wormhole gate validate <gate>`** says what a gate is missing rather than only refusing to
  dial, and `-all` sweeps every gate and names the ones with something wrong.

**Changed**

- **`Even` no longer ships, because `Large` is the same gate.** Nine shape files now. A server
  that already has `Even.shape` keeps it and its gates go on being detected.
- **`Grand`'s lower chevrons mirror its upper ones**, so the gate is no longer lopsided.
- **Gate shapes live in `shapes/gate/`** rather than `GateShapes/`.
- **The four-step order that decides what block a gate shows is written down once**, and every
  caller asks the same place.

**Fixed**

- **A block could be dropped into a gate's opening and never broken out again.**
- **A gate came back from a restart not knowing which shape it was built from**, and the next save
  wrote the loss to disk.
- **A gate built before its shape file gained a marker could never pick it up.**
- **A shape that pins a material lost it after a restart.**
- **The marker reference at the top of the shipped shape files disagreed with itself.**
- **Regenerating a gate reattaches its dial sign** when the sign is still standing but no longer bound.
- **An arrow between two facing gates crossed over and over**, and one that bounced off a player
  in front of a gate was pushed on through them.

### Transport rings

Design notes in [docs/RINGS.md](docs/RINGS.md), how-to in
[docs/guide/RINGS.md](docs/guide/RINGS.md).

**Changed**

- **A ring counts down for five seconds and rests for thirty seconds**, where it was three and
  sixty. An existing `config.yml` keeps its own values.

**Fixed**

- **A ceiling ring in a room deeper than four blocks fired over and over and took nobody.**
- **A room that could never work is refused when the ring is laid**, not when somebody stands in
  it.

### Beaming

Design notes in [docs/BEAMS.md](docs/BEAMS.md), how-to in
[docs/guide/BEAMS.md](docs/guide/BEAMS.md).

**Added**

- **The beam sequence is drawn as a timing strip in the documentation**, and drawing it found a
  number that disagreed with the code.

### Configuration and documentation

**Added**

- **The gate shapes are drawn in the documentation, idle and dialled** — six gates rather than
  nine files, with what a sign dial adds drawn once.
- **The ring patterns, the stack and both deploys are drawn** in the documentation.
- **The ninety looks are drawn**, with the recipe beside each.

**Changed**

- **`config.yml` is written in groups**, ten of them under `# --- Transport rings ---` banners,
  with each setting's comment cut to a sentence or two. 256 lines from 361, same 82 settings. A
  file you already have keeps its own layout; the grouped one is what a fresh install writes.
- **The four design documents open with a summary and are a fifth shorter.**
- **The README is a front page again**, with the how-to moved into `docs/guide/`.
- **Supported through Minecraft 26.2**: CI now also builds and tests against 1.21.11, 26.1.2
  and 26.2, and against Paper's API as well as Spigot's. Servers on 26.1 or later need Java 25.

### Performance

A pass over what this plugin costs a busy server. Nothing here changes what it does; every item
was chosen for scaling with how much exists rather than with how much is happening.

- **Two per-second sweeps scaled with how many gates exist, not how many are open.** Both read the
  open-gate set now.
- **The block index no longer allocates to answer a question.** `getGateFromBlock` and
  `isBlockInGate` are keyed by world and a packed block position rather than by `Location`.
- **A gate deleted while its wormhole was open never left the open set.**
- **Six static maps kept every player who had ever left.**
- **Asking which chunk a block is in was loading that chunk.**
- **Three log lines on hot paths were built whether or not anyone was listening.**

### Internals

- **One YAML write path and one logger**, where gates, rings and beam destinations each carried
  their own copy and three "is there a plugin to log through" checks disagreed.
- **Gates, rings and beam destinations live in `data/`** rather than sharing a folder with another
  fork's database.
- **Every store is found by asking the server for the plugin's folder**, where half were found
  that way and half by guessing at the working directory.
- **The plugin singleton is installed in one place**, where seventy-eight tests each did it
  themselves.
- **Thirty-four command helpers that returned `true` and nothing else** return `void`, and ten
  class-level `@SuppressWarnings` went with them.
- **Nine unchecked casts in the tests** are gone, and reflective field access went from 139 sites
  across 87 files to 8 across 7.
- **`MirrorWindows` is being taken apart.** `MirrorSight`, `MirrorFace`, `MirrorWindowState` and
  `MirrorDrawing` are out of it; 2,449 lines from 2,786.
- **Forty Sonar findings on the mirror code are cleared**, including two typed as bugs that
  were not: a null check read across two contracts, and an exact integer subtraction.
- **The release workflow can be rehearsed without publishing.**
- **Nothing marked for removal in newer Bukkit is called directly.** An arrow through a gate
  keeps its weapon from 1.21, and its knockback and crossbow flag before.

**Fixed**

- **A tidy-up that failed on shutdown took every save with it.**
- **The startup banner tore itself apart on some consoles and not others.**
- **Gate shapes and `config.yml` were looked for in the working directory** rather than the folder
  the server names.

[Back to top](#changelog)

## 1.5.0 (2026-09-09)

Not a feature release. Rings arrived in 1.3.0 and beaming in 1.4.0; this is the release that
went back over all of it. Twenty-odd player-facing bugs are fixed, several of them older than
this fork, and the tree went through a quality pass that took SonarCloud from 893 open issues to
none and the test suite from about 490 tests to 1326.

**Upgrading:** nothing to do. Gate, ring and beam files load unchanged and every setting keeps
its current value.

### Fixed

- Gate protection no longer stops working when another plugin cancels the event first, which
  could let lava out of a gate frame ([#53](https://github.com/khanjal/Wormhole-X-Treme/issues/53)).
- A gate whose blocks WorldEdit removed now refuses to be dialled and says why, rather than
  going quietly dead ([#54](https://github.com/khanjal/Wormhole-X-Treme/issues/54)).
- Beaming off a horse left the horse behind; your mount comes with you now.
- A chevron was invisible while a gate sat idle.
- A closing iris no longer buries whoever is standing in the gate.
- A sign gate wired to redstone dialled twice on one press.
- A sign gate ignored its own sign until somebody cycled it by hand.
- `HorizontalSignDial` could not be built at all.
- Choosing a sign dial cost the gate its iris.
- Redstone wiring inside a gate could never be taken back up without removing the gate.
- A ring pair in a room too short to hold it fired anyway.
- Tab completion after `beam to` offered only public destinations, never your own places.
- Beaming hid the traveller but left their gear standing in the column.
- A gate named with an accent came back as `Caf?` after a restart.
- A gate's owner turned into a UUID.
- One unwritable gate stopped every gate after it from being saved.
- Saving the config destroyed the rest of the file, and a hand-edited line missing its colon
  ended the parse.
- `/wormhole ring edit` with nothing after it crashed.
- Every feature permission under `/wormhole` was unreachable without `wormhole.config`.
- A rider arrived facing the wrong way after a cart trip.
- A minecart lost its own kind through a failed vehicle teleport.
- `/wormhole cooldown` reported a setting it never saved, and `/wormhole restrict` did nothing
  at all.
- `/wormhole config` rejected settings spelled the way `config.yml` spells them, worked only in
  English, and accepted values it could not read back.
- A gate file that would not delete failed silently.
- A powered lever or button lost its facing when an old gate was rebuilt.

### Changed

- The name and logo are no longer covered by the GPL — see [TRADEMARK.md](TRADEMARK.md).
- Dial signs are coloured and mark the selected destination, so it no longer looks identical to
  the two either side.
- Ring pads light as a redstone lamp rather than glowstone.
- A redstone signal on an open gate extends it up to the existing maximum, instead of closing it.
- Every sign gate takes redstone, not only the one with "Redstone" in its name, and the DHD
  accepts a signal from any component rather than only from dust.
- The kawoosh sounds like water rather than an explosion, and sign colours are quieter.
- Gate, ring and beam sounds are each configurable, including to a resource pack's own sounds.

### Documentation

- `docs/GATES.md`, `docs/RINGS.md` and `docs/BEAMS.md` explain how each subsystem works and why.
- The README gained a Features list, and its sound documentation is no longer filed inside the
  transport rings section.
- `/dial` is described correctly: it names a destination for a gate that has no dial sign, and
  is not a fourth way to dial one.

### Under the hood

None of this changes what the plugin does. It is why the list above could be written with any
confidence.

- SonarCloud: **893 open issues to 0**, with A ratings for reliability, security and
  maintainability.
- Tests: **about 490 to 1326**; line coverage 42.9% to 73%.
- Every `catch (Throwable)` is gone, every YAML file is read and written as UTF-8, and the dead
  `LegacyCompat` class plus nine unused helpers were deleted.
- Three bugs in the list above were found by writing tests rather than by anybody hitting them.
  Two more turned out to be in methods nothing called, which is how the dead code was noticed.

[Back to top](#changelog)

## 1.4.0 (2026-09-05)

Beaming: a third way to travel, with no structure to build and nothing to dial. Three large gate
shapes and a fourth palette arrived with it, and the woosh finally makes a sound.

**Upgrading:** nothing to do. Beaming is off until somebody sets a destination, and the new
shapes and palette are additions rather than changes to existing gates.

### Added

- **Beaming**, point-to-point travel alongside gates and rings: staff curate public
  destinations, each player keeps their own private places, and no structure is built for either.
- `/wormhole beam admin goto` and `send`, usable from a player, the console or a command block.
- Public beam destinations can carry their own cost, which admins may bypass.
- Three big, deep gate shapes: `Large`, `Grand` and `Massive`.
- `Even`, the first even-width gate shape.
- A fourth palette, `MilkyWay` — deepslate frame, iron iris, shroomlight chevrons.
- A gate shape can be validated and reloaded without restarting the server.

### Fixed

- A beam that failed part-way through stranded the traveller, and left them in the dark.
- The traveller's own view arrived ahead of the beam.
- A beam charged for a message with nothing behind it.
- The woosh could leave portal material stuck outside a gate, or an extra layer inside it.
- The wormhole woosh sound never actually played.
- A gate's ambient hum outlived the gate.
- A timed-out gate's chevrons could stay lit forever.
- `/wormhole go` ignored private network permission, which dialling already respected.

### Changed

- The beam effect matches its reference footage: the glow gathers at the traveller rather than
  around them.
- The traveller can move, turn and react during the envelope instead of being frozen from the
  moment the command runs.
- Beam and gate arrivals correct for ground that has been built up or dug out since the
  destination was recorded.
- Woosh depth is proportional to a gate's size rather than a fixed number.

### Under the hood

- `BeamAnimation.Sequence` was split so every phase boundary and quantity can be tested without
  a running server, the same way the ring subsystem was split earlier.

[Back to top](#changelog)

## 1.3.0 (2026-09-03)

Transport rings: local, point-to-point travel that needs no structure above the floor and
nothing to dial. Gates stay the long-haul option. Alongside them, twenty-two subcommands
became four, gates can be imported from any other Wormhole X-Treme's database, and gate
management turned out never to have been permission-gated at all.

**Upgrading:** every old command name still works -- they are registered as hidden entries
and dispatch exactly as before, so nothing in a command block or a script breaks. One
breaking change for plugin developers: `StargateMinecartTeleportEvent` moved from
`com.wormhole_xtreme.wormhole.event` to `...events`, joining every other event.

### Added

- **Transport rings** -- an invisible, permanently paired pad set into a floor or
  ceiling. Walk in, and after a countdown both ends swap everything standing in them.
- `/wormhole ring create|cancel|list|remove|edit|allow|deny|owner`, with two ring shapes,
  per-end materials and deploy style, and four permission nodes of its own.
- `/wormhole gate import` reads the SQLite database every build descended from the 2011
  original writes, and converts those gates into this fork's storage. Nothing is written back
  to the old database and nothing is deleted.
- `/wormhole gate regenerate -all` recomputes every gate's arrival point in one pass and
  reports how many needed it.
- `gate edit <gate> <field> [value]` gains a `group` field that sets a whole material group
  at once rather than three materials one at a time.
- `compass reset` puts the compass back to pointing at world spawn, and `compass` now
  explains itself when nothing will show the result.
- Ring sounds: the pad opening, one per ring as the stack builds, the transport itself at
  both ends, the pad closing, and a refusal heard only by the player it concerns.
- `RingTravelEvent`, fired once per travelling player, cancellable, after both ends are read
  and before either is written.
- Tab completion for gate shape names on `gate build`, and every setting name on `config`.
- **docs/API.md** -- the events, what each carries, when each fires, and worked examples.

### Fixed

- **Gate management was never actually permission-gated.** Any player who could run
  `/wormhole` could reconfigure or reassign *any* gate on the server, and change server-wide
  settings that are not even per-gate. All of it now requires `wormhole.config`.
- Holding forward against a gate you cannot enter no longer fills chat with identical
  lines. The move is still refused every time; only the repeated message is suppressed.
- Ring block positions are packed into a `long`, and `y` lived where a plain mask loses its
  sign -- so a ring below y=0 would have restored its blocks thousands of blocks away.
- A registry question in `Ring`'s static initialiser could fail during plugin load on
  1.20.6 and later, which would have left rings dead for the rest of that server's run.

### Changed

- **Twenty-two subcommands became four:** `gate`, `ring`, `config` and `compass`. Nothing
  was re-implemented -- every verb hands off to the handler that already owned it.
- `config <setting> [value]` reaches every setting, not the four that had commands, and
  takes effect as it is typed rather than needing a restart.
- `gate regenerate` also recomputes a gate's arrival point from its portal blocks and
  facing, so a gate that landed travellers at its side can be corrected.
- The README is now for server owners; the plugin-developer material moved to docs/API.md.

[Back to top](#changelog)

## 1.2.0 (2026-09-02)

One jar covers Minecraft 1.20 through 1.21.10, and the range is measured rather than
assumed: every published `spigot-api` version was built against to find both ends.

**Upgrading:** nothing to do. Note that **Minecraft 1.20.5 and later require the server to
run Java 21** -- that is the server's requirement, not this plugin's, and the jar remains Java
17 bytecode.

### Added

- Support for Minecraft **1.20 through 1.21.10** from a single jar. CI builds and tests the
  floor, the ceiling, and the versions between them where the API actually changed.
- A test checks every material named in `config.yml` and the shape files against whatever
  API the build targets, so a renamed or removed material surfaces at build time.
- `spigot.api.version` in `pom.xml` selects the API, so CI can point a build at a different
  server version without editing anything.

### Fixed

- A boat that failed to teleport was respawned as `EntityType.BOAT`, which stopped existing
  in 1.21.3 when boats split per wood type. It now respawns as whatever the original boat
  was, which also stops a birch boat coming back oak.

### Changed

- The jar is compiled against the **oldest** supported server rather than the newest, so the
  compiler enforces the floor instead of a player finding the gap.
- `EntityDismountEvent` changed package mid-range, so there is a small listener for each and
  only the one the running server can load is registered.

### Known limits

Nothing here has been runtime-verified on a live server of any version. CI proves the plugin
compiles and its tests pass against each API, not that a gate behaves correctly in game.

[Back to top](#changelog)

## 1.1.0 (2026-09-01)

First published release. The original project ended at 0.854; 1.0.0 was an internal
milestone of this fork and was never tagged or released.

Requires **Java 17** and a **Minecraft 1.20.4** server. Runs on CraftBukkit, Spigot, Paper,
Purpur and Pufferfish from a single jar. Folia is not supported.

Gates stop writing to the world, start making noise, and carry riders, vehicles and
projectiles again. The jar drops from 15.3 MB to under 300 KB.

**Upgrading:** gates are read from their existing storage and rewritten as one YAML file
each. `StandardAtlantis.shape` and `StandardUniverse.shape` are removed -- both were
`Standard.shape` with four lines changed, and the palettes they provided are now material
groups in `config.yml`, chosen by what a gate is built from.

### Added

- **Gate sounds:** one as a gate begins to dial, one per chevron as it locks, one as the
  wormhole establishes, one as it closes, an iris pair, and an ambient loop while it stands
  open. Configured by name, so anything the client knows works.
- **Material groups.** Shapes describe geometry; `config.yml` describes palettes, selected by
  the material a gate is actually built from. One shape file now builds a Standard, Atlantis
  or Universe gate.
- Travellers surface on arrival -- a moment of water at eye height, drawn to that player
  only. `gate-arrival-splash-ticks: 0` turns it off.
- `/wormhole custom -clean` clears material overrides snapshotted by the old custom mode.

### Fixed

- Travellers no longer drown or float in a water gate: portal interiors are server-side
  `AIR` with the material drawn to nearby clients.
- Minecarts and boats teleport again -- they had stopped entirely. Ridden animals travel
  with their rider, and riderless mounts can walk through on their own.
- Projectiles cross gates and keep flying, retaining their shooter so kills stay credited.
- Redstone gates can actually be built: the shipped shapes marked their redstone cells one
  block off, so a gate watched a wall for a signal that never came.
- Travellers can walk out of the gate they arrived at, rather than being held in the ring
  and eventually disconnected.
- Standing in a portal no longer gets a player kicked for flying.
- A closed gate could keep showing its portal to anyone who was out of range when it shut.
- Arrival points that sat inside the portal are moved clear of it on load.
- The version the server reports now comes from the build; `plugin.yml` had drifted, so a
  jar built as 1.1.0 announced itself as 1.0.0.
- An NPE on every move event for a gate that was activated but never dialled.

### Changed

- **Nothing a gate does is written to the world any more.** Chevrons and the woosh are
  drawings sent to nearby clients, like the portal already was -- so a server stopped mid-dial
  can no longer leave lit chevrons welded into a frame. The one thing given up is real light.
- **Storage is one YAML file per gate.** HSQLDB and SQLite are gone, along with the storage
  abstraction and the `mysql`/`postgres` options that were advertised but never implemented.
  Custom materials are persisted by name rather than enum ordinal.
- Permission checks go through `Player.hasPermission()`, so LuckPerms and anything else
  Bukkit-compatible works with no glue. The legacy built-in permission system is removed.
- One registry drives dispatch, tab completion and help. Nine subcommands were offered by
  completion and dispatched by nothing.
- The jar bundles nothing and has no runtime dependencies: **15.3 MB to under 300 KB**.
- Shutdown logs one summary line rather than one per gate.

[Back to top](#changelog)

## 1.0.0 (2026-05-14)

An internal milestone of this fork. Never tagged and never released -- the first published
release is 1.1.0, and much of the storage work below was removed again there.

### Fixed

- Fixed DHD activation to recognise all button material variants (oak, spruce, birch, jungle, acacia, dark oak, mangrove, cherry, bamboo, crimson, warped, stone, polished blackstone) via new `LegacyCompat.isButton()` helper.
- Fixed 2D gate exit location search in Nether/End dimensions: replaced `Material.AIR` equality check with `isAir()` so `CAVE_AIR` (present in Nether/End) is correctly treated as traversable.

### Added

- **Vault Economy integration** — optional gate use and build costs configurable via `economy-enabled`, `economy-use-cost`, and `economy-build-cost` in `config.yml`. Gracefully no-ops when Vault or an economy provider is absent.
- Added `LegacyCompat` utility class consolidating modern-Bukkit material/block compatibility shims.
- Added pluggable storage backend support and config keys (`storage-backend`, `storage-sqlite-path`, `storage-jdbc-*`).
- Implemented `StorageBackend` interface and `SqliteStorage` scaffold.
- Added `/wormhole storage` CLI: `backend` (set runtime backend) and `migrate` (DB -> YAML supported).
- Added `StorageMigrator` to export existing DB gates to per-gate YAML files (non-destructive by default).
- Added two new default gate shapes: `StandardAtlantis` and `StandardUniverse` (bundled in resources).

### Changed

- `ConfigurationYAML` migration now excludes legacy permission keys and preserves storage keys when writing `config.yml`.
- Removed legacy `SimplePermission` support; Vault/LuckPerms recommended.
- Improved startup diagnostics and storage initialization logging.
- Fixed a number of persistence and teleport UX issues (teleport bounce mitigation and gate activation mapping fixes).

[Back to top](#changelog)

## 0.854 (5/17/11 @ 16:13 PST)

- Updated chunk (un)loading to happen when gate (de)activates and when dial lever state
  changes happen.

- Fixed iris levers not being added properly with 2d gates. (Oops, guess that code WAS needed)

- Fixed IndexOutOfBoundsException on 3d gate shapes without lighting blocks.

- Hamfisted fix for signs not updating. Now we nuke the sign and build it from scratch every
  time a gate sign is toggled. Causes a flash, but... who cares. It works EVERY time now.

## 0.853 (5/13/11 @ 23:05 PST)

- Fix for /dial gates breaking when a user who doesn't have dialer permission hit the lever.

- Switched to getTypeId() from getType(), hopefully this works around the getType() == Air bug
  that might be lingering. (doubt it :| )

## 0.852 (5/12/11 @ 07:55 PST)

- Added support for upcoming Permissions 3.0.x release.

- Fixed a NPE in 2d shape code dealing with light block positions.

- Added soft dependencies to plugin.yml.

- Minor log format changes for readability.

## 0.851 (5/10/11 @ 21:56 PST)

- Complete revamp of how we handle permissions checks. Lots more case statements, lots less
  if/else if. Much better. This is what enums are for.

- Added new gate use cooldowns. This feature will only work on complex permissions enabled
  servers. There are three groups you can assign a player to; 'wormhole.cooldown.groupone',
  'wormhole.cooldown.grouptwo', and 'wormhole.cooldown.groupthree'. If you have an '*' on any
  user/group, remember to '-wormhole.cooldown.groupone' etc. There is a new command to
  enable, disable, and modify cooldowns; 'wormhole cooldown [true|false|group] <time>', valid
  groups being 'one', 'two', and 'three', valid time being between 15 and 3600 seconds. There
  are also Settings.txt options for all of these new settings. Cooldowns are set when a player
  enters a stargate, not when they /dial. Cooldowns are removed via timer events, and even if
  the timer event fails, we do a fall back calculation when a player enters a stargate, and gets
  denied access.

- Added new gate build count restrictions. This feature will only work on complex permissions
  enabled servers. There are three groups you can assign a player to; 'wormhole.build.groupone',
  'wormhole.build.grouptwo', and 'wormhole.build.groupthree'. If you have an '*' on any
  user/group, remember to '-wormhole.build.groupone' etc. There is a new command to enable,
  disable, and modify build count restrictions; 'wormhole restrict [true|false|group] <count>',
  valid groups being 'one', 'two', and 'three', valid count being between 1 and 200. There are
  also Settings.txt options for all of these new settings.

- Updated the stargate sign dial sign reset code to be more reliable at causing the client to
  notice update changes.

- Added a thrown exception during stargate 3d shape parsing if the shape doesn't have an exit
  point. We depend on this location for pretty much everything. If it isn't there, really bad
  bad bad things happen.

- Now we have more than just Standard as our default shape. We extract Standard,
  StandardSignDial, Minimal, and MinimalSignDial to the gateShape folder if it is missing shapes.

- Now we don't toggle stargate signs when we start.

## 0.850 (5/5/11 @ 16:15 PST)

- iConomy support removed. I will not depend on plugins that decide to change their
  package name 5 major versions in and basically give everyone who depended on the
  package location the middle finger.

- 3d Gate shapes now implemented.

- Custom gate settings now in place (read: per gate material settings).
  This included the re-addition of the portalmaterial and irismaterial commands.
  lightmaterial, redstone, wooshdepth, and custom commands newly added.

- Massive internal overhaul, refactorings, cleanups, general goodness and bugfixes.

- Added support for Wormhole X-Treme Worlds. This allows Wormhole X-Treme to offload its
  chunk loading and world loading to WXW, for worlds that exist in WXW. Requires user to
  change Settings.txt option WORLDS_SUPPORT_ENABLED from false to true. This option requires
  Wormhole X-Treme Worlds v0.5 to be installed, and preferably configured for every existing
  world populated with stargates. If this option is set to true but WXW is not v0.5 (or not
  installed), WX will not load its stargates from its database.

- Removed many superfluous chunk load requests. Added graceful chunk unload queue when we
  are done with a chunk.

- Bumped supported version of permissions to include the 2.7 tree.

- Updated help text for new/modified commands.

- Added backwards compatibility, for those users who just don't want to upgrade to 3d shapes.

- Added loads of failsafe settings, for when users don't have any shapes installed, but have
  stargates already.

- wxidc now only works on non-sign powered gates which have iris activation blocks set.

## 0.833 (4/9/11 @ 23:36 PST)

- Fixed iConomy double(or many many more) charging issue. Tried to do something awesome,
  turned out to be a bad idea. We'll revisit these kind of changes when 3d shapes are in
  and I can do some major refactoring and method merges/splits.

- Bumped supported version of permissions to include the 2.6 tree.

- Merged some of the sign click schedule related methods. Should make sign click messages
  more reliable.

## 0.832 (4/5/11 @ 15:04 PST)

- Fixed NPE during database creation. Whoops, missing null-checks.

## 0.831 (4/4/11 @ 23:13 PST)

- Fixed erroneous messages sent when a plugin is attached to already and WXT receives
  a plugin event for it. Cosmetic bug, fixed.

## 0.830 (4/4/11 @ 01:12 PST)

- Water now will not flow over Stargate anythings. No more broken levers and magic
  blocks of water floating in their place.

- Buckets now will no longer work with stargate anythings. No free water and lava.

- Minor optimizations and code cleanups.

- PORTAL_MATERIAL, IRIS_MATERIAL, STARGATE_MATERIAL, ACTIVE_MATERIAL are all part of gate shape now.
    - All configuration values associated with these are gone now.
    - Gate shapes without these default to
      PORTAL_MATERIAL = STATIONARY_WATER
      IRIS_MATERIAL = STONE
      STARGATE_MATERIAL = OBSIDIAN
      ACTIVE_MATERIAL = GLOWSTONE
    - Updated default gate shapes that come in the zip to include these new values.
    - See gate shape files for more details
    - Known bug: If you teleport from a gate with portal type lava, to a gate
      that is NOT lava, you will be burned once you reach the other side.

- Removed version 1 DB conversion because new design doesn't allow for it anymore.
    - For users this means if you are upgrading from version 0.3 or less to this
      version you will need to remake your gates.

- Fixed NPE in onPlayerInteract caused by event not reporting the block the interact
  event was associated with.

- Logic tweak in the find safe teleport code. Should be *safererer*

- Sign powered stargates now can only target other sign powered stargates.

- Fixed so that when coming from a lava portal stargate to a non-lava portal stargate
  fire damage is canceled still. No more nasty fire after a teleport.

- Added the logic back in to stop people from randomly teleporting when next to the lever
  of an active gate. The side effect is, when block.getType() fails, gates don't work.
  Its one or the other.

- Fixed /wxcomplete permission deny issue with stargates on public networks.

- Added ICONOMY_OWNER_EXEMPT option to Settings.txt with a default value of true. When
  true this option disables the charging of gate owners for using their own gates.

## 0.821 (3/30/11 @ 17:42 PST)

- Update version of iConomy we build against and test for.

- Fix NPE in old non-shape based gates.

- Added custom StargateTeleportEvent for MinecartMania as we nuke the minecarts before
  teleporting them.

- Refactored package to com.wormhole_xtreme.wormhole in anticipation of adding more
  stargate related projects.

## 0.820 (3/29/11 @ 17:31 PST)

- Initial support for CraftBukkit Build 600.

- Got rid of the stupid double error that people got by not reading the readme. Now when
  we parse settings.txt, if the value is integer for the iconomy settings, we change it
  to a double by simply dropping a .0 at the end of it. Problem solved.

- Lots of optimizations to the distance finding method we were using. Also fixes to the
  gate shape parsing code. (Thanks lirelent)

- Overhaul of the way we handle permissions internally. More unified approach to the
  actual permissions checks.

- Gate block protection should now be compatible with plugins like mcMMO. "Should" being
  the operating word.

- Levers now properly move when used and toggle on and off when stargate
  and iris are activated.
  
- All Permissions deny events now log at Level.FINE. Got permissions problems with WXT?
  Now see what is happening.
  
- Optimized fire protection. Now we use timer events that go off 2 seconds after gates 
  close. This way we don't have to listen for fire type events 24/7. :)
  
- Fixed teleportation dropping people into very unsafe locations. Now we scan for safe
  place to drop people, if we can't find one we drop the player in front of the DHD. 
  This will also FIX wormholes by setting the stored teleport location to the new clean
  and safe location. 
  
- We now support using the help plugin along with WXT. 

- We now have settings.txt options to hard disable support of iConomy, Permissions, and
  the help plugin. No longer do we log a warning when unable to find the plugin we depend
  on. We log at INFO. :P
  
- Buttons are no longer really used. If a button exists on a stargate, it will be replaced
  with a shiny new lever on the first use. 

- Lava stargates are *really* safe to use now. For trees even.

- A whole host of debugging information has been added at Level.FINE. If you can trigger
  a bug reliably, set yourself to fine and provide the server.log details surrounding the
  bug. Not recomended for production servers as well... its exceissive.
  
- the '/wormhole regenerate' command is partially added. Will regenerate missing activation
  and iris levers.
  
- Minecarts work across chunks and worlds now! If you run into a location where it doesn't work,
  use the wormhole in both directions to correct the wormhole, then try again. :)

## 0.812 (3/23/11 @ 15:17 PST)

- /wxgo now works properly when traversing world bounderies. First we quickly pop into
  the default spawn location for the target world, then from there we go to our final
  destination. It is a hack, but it is a working hack. :)
  
- methodized the code to find closest stargates, and find distance from closest stargate
  blocks as well as the math to find distance.
  
- Updated block ignition events to only use proximity style checks. Block ignition event
  cancellation radius increased to active stargate woosh depth or 4 blocks, which ever is 
  further.
  
- Updated '/wxcompass' to use new FindClosestStargate method.

- Updated onEntityDamage to use only proximity style checks. On active gates a bubble of 
  no fire damage of either woosh_depth or 4 blocks, which ever is larger, is created. On
  closed gates a bubble of 2 blocks is created to stop fire ticks occuring right as a 
  gate closes. Stopped caring about potential drowning in stargate. If user decides they
  want to stand in the wrong side of a gate till they drown, that is their choice. 
  
- Re-added missing CONSTRUCT_NAME_TAKEN error string in ConfigManager. This stops an NPE
  in 'wxcomplete'.
  
- Added support for tkelly's Help plugin. Will generate proper config based on permissions
  type (simple or complex) or lack of permissions plugin altogether. 

- Refactored the heck out of iConomy and Permissions support. Own classes in a new package
  to go along with the Help support. Methodized a bunch of useful functions. Less
  duplicated code.
  
- Added some log output for 'wxforce'. Should help combat abuse.

- Ops are now always able to use 'wxremove'.

- The '/wormhole simple' command now refreshes Help entries to the proper permissions after
  being set.

## 0.811 (3/21/11 @ 20:27 PST)

- Came up with a proximity based check for stargates in the lava & fire event
  cancellation code. Now only 1 block radius around active lava portals
  gets its lava & fire events cancelled. STATIONARY_LAVA is safe for players
  to use in portals now. For reals.
  
- Version 4.5 of iConomy is now supported and verified as working.

- Now we actually check for Iris on gate use while in minecart...

- Back to the good ol kick the player out of the cart and stuff them through
  the stargate method. Doing a bit of a hackish teleport when going between 
  worlds as well. We tp to spawn, then instantly to destination. This is only
  when starting the tp while in minecart. If minecart is empty and passing
  into a stargate that will traverse worlds, we kick the minecart back. Otherwise
  it will dissapear into the void. 
  
- Now we cancel block ignite events on a proximity basis, same way we cancel 
  fire and lava events on player. No more trees bursting into flames near a stargate.
 

## 0.810 (3/20/11 @ 00:18 PST)

- Broke '/wxcompass' out into its own class. Removed '/wormhole compass'.

- Broke '/wxcomplete' out into its own class. Removed '/wormhole complete'.

- Broke '/wxidc' out into its own class.

- Broke '/wxremove' out into its own class. Removed '/wormhole remove'. 
  Fixed so it toggles iris to off state before removing gates with iris 
  active.
  
- Broke '/wxlist' out into its own class. Added no permissions error message.
  Removed from '/wormhole' command.

- Added command '/wxgo' and broke it out into its own class. Added no permissions 
  error message. Removed from '/wormhole' command.

- Broke '/dial' out into its own class.

- Broke '/wxbuild' out into its own class. 

- Broke '/wormhole' out into its own class.

- Added another message for active gates. Now it will say either remote activated, or 
  activated by someone else already. 

- Added SIMPLE_PERMISSIONS config option. The default value of 'false' makes permissions
  node settings use complex mode. While the setting of true sets the plugin to check for 
  extremely simplified permissions. Permission node details can be found in the README.
  
- Refactored the WXForce class to Force. Hopefully this shuts MSSE up. ^^;

- Moved a bunch of the initial loading out of onEnable and into onLoad. 
  Now we use onEnable only for events that should only happen at plugin Enable.
  
- Updated '/wormhole' command to have more descriptive errors and built in help. 
  Updated help information for this command as well. Command now has unified messaging
  string headers. Added new 'simple' option to enabling simple permissions while the game
  is live. Requires the user to have proper permissions node for configuration in target
  mode. Removed a bunch of duplicated permissions checks. Only one check is needed at 
  beginning of command call now. 
  
- Revamped readme to reflect important recent plugin package changes.

- Fire damage, combustion damage, and drown events now canceled in stargate. Now LAVA is 
  really a valid portal material. 
  
- Creeper explosions are now canceled when they would cause damage to stargates. This will
  stop signs and buttons from being destroyed during that mad dash to/from a stargate. ^^
  

## 0.801 (3/15/11 @ 22:33 PST)

- Update to the way data is pushed to signs in gate destruction and creation.
  Causes signs to update visually more reliably.
  
- Removed NPE during removal of sign gate if current sign gate's target doesn't
  have a gate target. 
  
- Initial addition of '/wxforce <gate|drop>' command, used to globally close all 
  gates and/or drop all irises temporarily (until they are dialed again). 
  Uses the 'wormhole.config' or 'wormhole.remove.all' permission nodes.
  
- Fixed '/wxremove' so that the permissions check doesn't fall through to the built
  in permissions check. :|
  
- Fixed permissions surrounding gate networks and WORMHOLE_USE_IS_TELEPORT

## 0.800 (3/9/11 @ 23:33 PST)

- Added pretty format messages! [dh/gyoza]

- Revamped the wormhole list and how it displays items. 

- Wormhole use cost of 0.0 no longer tells users that they were charged 0.0 when 
  using a gate. Also no longer bothers doing the iConomy calls with a 0.0 value.
  
- /wxbuild (/wormhole build) no longer blindly calls for permissions. This removes
  an NPE.
  
- /wxcompass (/wormhole compass) now has a permissions node. 'wormhole.use.compass'.
  Ops also can use the compass by default.

- New config value "WORMHOLE_USE_IS_TELEPORT"
    * Default is false (which doesn't change anything) (wormhole.use means a user
      can activate a gate, but others can still teleport form an active gate)
    * If set to true then users without wormhole.use will be unable to activate
      a gate OR TELEPORT from a gate.

- Fixed the gate active but no teleport bug (for real). 

- Fixed Iris to auto-open when dialing out. 
    * Dial gates iris will stay closed until actually connected.

- /wxcomplete properly checks for 'wormhole.network.build.NETNAME' permissions, 
  if permissions are enabled. 

- /dial now checks for 'wormhole.network.use.NETNAME' permissions if permissions 
    are enabled. The network 'Public' is always assumed to be just that. Public.
  
- /dial now properly kills timers associated with start gate when failing a dial.
  Instead of just the lights going out, and everything waiting for timers to finish.

- /wormhole go now has the permissions node 'wormhole.go'

- Check for WORMHOLE_USE_IS_TELEPORT in conjunction with 'wormhole.network.use.*'
  permissions node to disallow users who don't meet permissions requirements.

- Check for 'wormhole.network.use.*' permissions node on stargate activation 
  button/lever toggle.
  
- LAPIS_BLOCK is now an allowed Iris material.

- Properly tag gate sign dial signs with network gate was on at removal/break time.

- New command "/wxidc <gatename> <optional_set_idc>". The set can be "-clear" which 
  will clear the IDC. This command is available to OPs, wormhole.config, the console, 
  and the owner of the gate.

## 0.755 (3/4/11 @ 16:51 PST)

- Added /wxbuild, /wxlist, and /wxremove commands as short form of their /wormhole 
  counterparts.
  
- Added /wxcompass and /wxcomplete.

- /wormhole complete and /wxcomplete optional arguments require key=value
  * To add IDC you do idc=<value>
  * To add a network you would do net=<value>
  * Example: /wxcomplete MyGate idc=Haha net=Awesome

- Fixed bug in database code which was pushing data to wrong fields.

- Minor rework in PlayerListener code pretaining to players entering stargates.

- No longer scream about iConomy 4.2 or 4.3 being unsupported.

- No longer scream about Permissions 2.5.x being unsupported.

- Reduced potential thread safety issue when accessing Iconomy.

- Lots of logging added at Level.FINE and Level.FINEST for stargate operations. 
  Most useful for debugging.
  
- Closed a potential file descriptor leak in configuration code.

## 0.754 (3/3/11 @ 00:54 PST)

- Iris activation levers are now destroyed when a stargate is removed. Also if
  anything is in the iris activation lever block location when creating an 
  ICD protected gate it will be destroyed properly before lever is placed.
  
- We now properly destroy all name signs before replacing them with new name sign.
  Was not checking before causing a NPE. 

- Fixed '/wormhole build <gateshape>' to use the proper argument for <gateshape>
  This of course means that users with 'wormhole.build' permissions can use 
  '/wormhole build <gateshape>' after building a DHD, and press the button to 
  instantly generate a StarGate in the shape specified.
  
- Levers got the same treatment as signs now. No more wacky floating levers on
  idc enabled gates. Lever creation code broken out into own method. Stargate
  regeneration command is going to use this.

## 0.753 (3/2/11 @ 19:27 PST)

- Fixed Gate Sign placement. Now no longer at right angles to the gatesign block.

- Updated serverListener onPluginEnabled checks for iConomy and Permissions to
  only go off if the plugin is not already bound to. No more multiple
  notifications about attaching to Permissions. Also fixed a minor casting issue
  in the Permissions plugins attach section of onPluginEnabled. No more casting 
  error.
  
- Error messages for onEnable checks for iConomy and Permissions should be a bit 
  useful now as we may have been loaded before Permissions.

## 0.752 (2/28/11 @ 20:57 PST)

- Initial iConomy 4.1 Support 

- Initial Permissions 2.5 Support (should just be drop in)

- Fixed Stargate destruction detection. Removed blockDamageEvent detection and
  added blockBreakEvent detection in its place.

- Removed dead/unused code.

## 0.751 (2/25/11 @ 07:53 PST)

- Initial support for iconomy 3.0

## 0.750 (2/22/11 @ 22:19 PST)

- Major refactoring and package name changes.

- Initial support for minecraft 1.3.

 
## 0.741 (2/21/11 @ 21:48 PST)

- Stopped using playerListener for commands. Use the new onCommand structure. 
  This puts us as fully onCommand compliant for when they decide to put nags 
  about how horrible the coders are who are using the examples previously given 
  to them by bukkit. ^^
  
- Bumped up our BLOCK_PHYSICS and BLOCK_FLOW priorities to Highest. When other 
  plugins cancel these for us really bad things happen. (NPE)
  Bumped our PLAYER_MOVE up to High. Once again, other plugins canceling these 
  events causes us to break in most interesting ways. (NPE)
  
- Stopped listening on PLAYER_QUIT. We never did anything with it anyways. 
  No need to hold the resources.
  
- getLogLevel() added to ConfigManager for getting Log Level from the config. 
  getLevel() added to Settings for pulling the Level data from the ConfigKeys.

## 0.740 (2/21/11 @ 05:45 PST)

- Initial revamp of config system. Now with 100% less chances of a dereference 
  based NPE. EVERYTHING is checked for a null. Hard coded defaults. Will update
  this to use the defaults used to generate default conf file next config push.
  
- Minor logic changes in block listener code.

- Fixed sign not being breakable? Again?

- Broke DeleteBlocks() out into DeleteGateBlocks(), DeletePortalBlocks(), 
  DeleteNameBlock(), and DeleteTeleportSignBlock(). Allows use of these 
  functions in other commands individually.
  
- Added DeleteNameSign() for deleting the name sign. This gets called when using
  "/wormhole remove" and when destroying the gate by hand. No more magic free 
  signs when destroying stargates. Still no cure for free signs when destroying
  just the Name sign.
  
- DeleteTeleportSign() and ResetTeleportSign() added. Delete nukes the sign 
  altogether. Reset sets the name on it back to the old name of the Stargate and
  wipes all other lines of text. No more gates accidentally named -gatename-.

## 0.736 (2/20/11 @ 03:20 MST)
- Added configurable WOOSH_DEPTH for custom gate shapes.
- Updated to work properly with latest bukkit onEntityDamage changes.
- Stargates should no longer linger in half living states after a server shutdown while stargates are active.
  On onDisable we close all gates properly. onEnable we check and close gates again, just in case.
- Woosh Depth changes require user to remove one stargate and re-add it to set the woosh_depth properly. Any attempt to start up a stargate
  before doing this will cause a NPE. Also recommended users update their .shape files with 'WOOSH_DEPTH=3', or something similar, before doing this 
  (or conversely, remove their GateShapes folder).
- Fixed stray permission 'wormhole.use' which was causing problems. Permission was broken into two permissions few releases back. 
  'wormhole.use.dialer' and 'wormhole.use.sign' or 'wormhole.use.*' Also made it so players without 'wormhole.use.sign' can not change
  wormhole sign destinations.

## 0.735 (2/19/11 @ 03:40 PST)
- Modified Standard.shape to have 7 chevrons that light up.
- Modified shape parsing code to support an [O:E:L:S] block in the gate design.
- Rewrote logger setup. Now there is a config option for LOG_LEVEL. Uses Java logging.Level log levels.
  Only directly effects the server log file. Allows for some extra debugging of gate shapes. Defaults to INFO. 
  Directly effects the minimum level of logging in Bukkit, which at current is set to 'null'. Should have no effect on
  anything else. This is only able to be set at startup time.
- Updated gate destruction mechanics so if the gate is in a lit state but not portaling when it is destroyed, it flips back to
  an off visual state and disables all times associated with the gate. The act of turning off the lighting means that if the block
  that was destroyed was a lit block, it will be replaced with the initial material.
- DHD no longer lights up thus allowing the DHD to be properly destroyed allowing for one way gates.
- General code cleanups.
- Updated gate splash effect to use whatever PORTAL_MATERIAL is set to. Beware with STATIONARY_LAVA. It is LAVA. It hurts. BAD. Like REALLY BAD.
  BURNS BURNS BURNS. You have been WARNED. 
- Consider this build a Beta? 

## 0.730 (2/16/11 @ 20:40 MST)
- Added custom gate shapes!
      After starting server there should be a new directory and file plugins/WormholeXTreme/GateShapes/Standard.shape
      You can look in that file to see how to make custom gates.
- Flipping the Iris switch on a gate that is locked but not active will no longer create a false event horizon.
- The DHD on outbound dialing non-signed gates activates and deactivates properly with lightstone block effect and proper messages again.
  No more instant re-activating, must DHD must be deactivated first (or time out) before reactivation. 
- Small code cleanups and dead code removal (or commenting)
- Added '/wormhole irismaterial' command and associated it with the 'wormhole.config' permission.
  Valid materials are: STONE, DIAMOND_BLOCK, GLASS, IRON_BLOCK, BEDROCK. This is a global command. 
- Fixed gate light deactivation in conjunction with the inactive timer and the /dial command.
- Added 'wormhole.list' permissions node. 
- New gate design Minimal
- Added initial gate shape debugging output. 
- Added optional extra paramater to remove - /wormhole remove <name> all (the all is optional)
        If 'all' is specified at the end, the remove will also remove all blocks associated with the gate (other than the DHD)

## 0.727 (2/16/11 @ 13:39 PST)
- Made gate connection logic a bit more reliable with regards to scheduling system.
  Gates Now will never open in a half working state. If one doesn't open, neither will stay open.
  If scheduling for timed gate closings fails, try again, if that fails cry in the log and don't open 
  infinite time wormhole.
- Fixed iris causing inability to use a gate due to lack of wormhole event horizon (water) which is 
  destroyed by the iris closing/open
- Unstable wormholes event horizons are contained by Titanium/Trinium iris. (No splash/woosh through iris)
- Iris material now user configurable via Settings.txt. Defaults to STONE.
- Iris now unable to be broken, even if player is able to destroy wormhole. Use the lever to remove the iris.
  This stops accidental stargate breaking.
- Stargate will now deactivate and drain if it is active when any of its blocks are destroyed. This should stop the flooding issue.
In Progress/Partially working:
- Debug logs have been added for the gate scheduling system and will be added for most major break points. 
  Logs will take configuration setting allowing user to set the level of logging from the config file.
  Currently prettyLog only understands info, warning, and critical. Any call to it for anything else 
  like using 0 or 4 or 400 will result in an info log.
- Changed permissions for wormhole use and removal:
    Note : this means that you *MUST* change permissions or it won't work!
    wormhole.use.sign - lets a user use sign gates.
    wormhole.use.dialer - lets a user use /dial gates
    wormhole.remove.own - lets a user remove a gate that they own.
    wormhole.remove.all - lets a user remove any gate
- Woosh has been added back into the plugin
    There is a new configuration value that lets you disable this if desired.    

## 0.726 (2/15/11 @ 05:40 PST)
- Log output has been totally revamped. Now it should be easier to tell what we are doing in the logs.
- Fixed the ability to use the craftbukkit /reload command without giving an NPE.
- Removed a few potential file descriptor, memory, and database performance leaks.

## 0.725 (2/13/11 @ 21:15 MST)
- Iris and IDC is now completed for non-sign gates!
- When completing the gate you use /wormhole complete <name> <idc> to set the iris deactivation code.
- A lever will appear below the activation button.
- Pressing the lever will toggle the Iris on and off.
- When the Iris is active people will be unable to come through the gate!
- When dialing a remote gate you can optionally type "/dial <name> <idc>" if the IDC is correct you will deactivate the remote iris.
- Gates will not light up when active.
- Using double quotes in commands now works!
	Examples:
	/wormhole complete "A Fun Gate"
	/dial "A Fun Gate"
	/wormhole remove "A Fun Gate"

## 0.71 (2/12/11 @ 22:42 MST)
- Fixed an error message to be more specific (when trying to dial from an activated gate).
- Fixed problems with TickNextTick exception (needed to use sync instead of async).
- This may cause more issues though until the underlying issue is fixed in bukkit.
- This should be *more* stable than what was done before, but isn't guaranteed to be 100% perfect.
  Edit: Looks like the fix has been pulled into the repo - just need the next build and we should be good!
- Hitting the activate button again after you have previously hit it, but before you dial something will now 'deactivate' the gate.
- Due to these big issues the handling of double quotes in names has been pushed off for another day or two, sorry for everyone with that issue!

## 0.70 (2/12/11 @ 04:10 MST)
- Fixed multi-worlds!!!! GO AND EXPLORE THE MULTIVERSE!
- TIMEOUT_SHUTDOWN now works properly. Feel free to set this to something other than 0!!
- Gates now have owners
- First thing I am doing with owners is allowing owners to charge a percent of the iConomy cost
- After running this new version once you will see a new configuration value available.
- Settings are no longer overwritten on version upgrade 
- Logging is now done properly thanks to Alron @ github.
- There are some other small fixes I just don't remember them all right now.

## 0.67 (2/7/11 @ 18:57 MST)
- Version 0.66 is dumb, so I had to fix it up.
- Fixed the saving bug.
- Fixed the "gate timed out" message from repeating. FINALLY!!!
- You really want to get this version. It is much better than 65 and 66

## 0.66 (2/7/11 @ 15:31 MST)
- It just isn't a release without a bug....
- This release fixes a small bug from the last release where wormholes fail to save properly after shutting down the server.
- You DO NOT WANT TO UPGRADE TO THIS!!!!

## 0.65 (2/7/11 @ 12:10 MST)
- Wormhole now supports multiple worlds! (and works for the newest bukkit builds). Requires craftbukkit version 271+
- Wormhole sign targets are now stored across restarts
  This means that if you target a gate at a gate via the sign, then destroy the sign, the gate will always target the last targetted gate (Across restarts!)
- Wormholes that are active on server restart will remain active when restarted.
- IDC(Iris Deactivation Code)s now work! (only for non-sign gates)
  When creating a gate you can type /wormhole create <name> <idc>
  To dial a gate with an IDC you type /dial <name> <idc>
  Currently if there is an IDC set it will always be active (later you will be able to disable).
- Network can now be specified for a sign gate!
  When creating a signed gate the first line is the gate name, the second is the network name.
  If nothing is put in second line gates will default to the "Public" network.
  The sign will only cycle through gates with the same network !

## 0.62 (2/5/11 @ 10:37 MST)
- Fixed being unable to dial after newly creating a non-sign gate.

## 0.61 (2/4/11 @ 15:41 MST)
- Stupid copy/paste on an error loading iConomy fixed.

## 0.60 (2/4/11 @ 15:02 MST)
- Added configuration file
  Restart the server once and the file will be created, including your old settings.
  Changes to configuration require restart to go into effect (of course)
  Any changes made while the server is running will be written to the file on shutdown.
- Improved performance over previous fix for CraftBukkit.
- Fixed a few small issues
- Sign dialer should now be set and no longer have "No Other Gates" after a restart.
- Chunks should now be saved when server is shutdown 
- Download for jar file is now available at GitHub as per requested by several users.
  This is the jar file only!
- iConomy support!!!!
  Configuration file will have the values for this:
  Use cost: how much it costs to use a wormhole (cost to actually teleport, activating the gate is free. This should stop people from running into a gate someone else opened.)
  Build cost: How much it costs to build a wormhole.
  Ops Exempt: if set to true Ops will not be charged to use/build gates.
  I can add/change configurations as needed, but hopefully this is a good start.

## 0.59 (2/2/11 @ 00:07 MST)
- Quick patch to fix the issues with the newest version of CraftBukkit (231)

## 0.55 (2/1/11 @ 13:10 MST)
- Fixed some debug messages that shouldn't have been left in.
- Added /wormhole compass (Make compass point to nearest wormhole)
  Typing again will recalculate nearest wormhole.
In Progess:
- iConomy will be the next release, I just needed to remove those debug messages.

## 0.51 (1/31/11 @ 11:22 MST)
- Fixed the spamming of timeout message.
- Added config "/wormhole activate_timeout" to change the default timeout after activating a wormhole before it dials.
- Changed config name of timeout "/wormhole timeout" to "/wormhole shutdown_timeout"
  So now there is the activation timeout (activate_timeout) and the timeout after dialing when the gate needs to shutdown (/wormhole shutdown_timeout).
  Default value for shutdown_timeout is now 0 to reduce the instances of people falling through the earth.
- Added an OP only command (to go along with listing, you probably want to be able to see where the gate actually is)
  /wormhole go <NAME>
  This will instantly teleport you to the gate so know where it is.
  This was actually in 0.39 but I have been including the source in the JAR now and you can see my source at GitHub
  Feel free to comment and suggest changes in code if you want to.
  Feel free to also make changes on your own and send them to me via GitHub - I will integrate the changes if I like them!

## 0.50 (1/30/11)
- Minecarts now can go through wormholes.
  If a player is in the cart they will go through the gate but will not be in the cart when they arrive at the other side
  I would not recommend riding carts through the gate yet until I can figure out how to keep the player in the cart on the other side. Sometimes riding a cart through the gate results in weird behavior.
- Activated but not dialed gates now timeout after 60 seconds
- Fixed a bug with portal material of WATER (it can only be STATIONARY_WATER)
  It will automatically update any WATER to STATIONARY_WATER.
  I only added the STATIONARY_LAVA as a material on a whim - but when I found out people actually wanted to use it I tried to stop the damage but was unable.
  For now STATIONARY_LAVA is probably not your best option if you want to actually use the wormholes.
- Added command /wormhole list for Ops only. Lists all stargates on your server.
- Fixed a bug with portals being created out of thin air when you hit a button.
- Portals are now required to have AIR blocks for all blocks inside the gate when constructing.
- Possibly fixed issues with linux and DB
  changed the sql connection string to ./plugins/WormholeXTremeDB from plugins/WormholeXTremeDB
- I can't seem to find any issues with the newest craftbukkit (187)
Open issues:
- DHDs can't be repaired after removing a button/dialing sign on a sign-dialing gate (workaround is to /wormhole remove and then add the button/sign, and then it will function correctly.)

## 0.39 (1/27/11)
Added support for the Permissions plugin.
Without the plugin it defaults to the previous permission settings.
Changing the built in permissions will not change the Permissions plugin at all.
Permission nodes are as follows:
wormhole.use - Able to use wormholes
wormhole.build - Able to build new wormholes
wormhole.remove - Able to remove wormholes
wormhole.config - Able to configure settings like material and timeout. (By default Ops will be able to do this as well)
Fixed the weird names on the signs issue.
Part of fixing the falling through the world is pre-loading chunks
You will need bukkit v157 or newer!
Fixed a bug stopping the PORTAL material from working (Thanks Dinnerbone)
/wormhole timeout 0 should help STOP falling through the world.
I have tried to fix the point where you teleport when making a new gate. Hopefully this fixes it it.
Existing gates SHOULD start to work again unless you are really unlucky. If a gate just refuses to put you in the right place, a /wormhole remove <name> and then just add it again and it should be fine!

## 0.32 (1/26/11)
Again fixing small issue stopping gates from being built.

## 0.31 (1/26/11)
Forgot to change one last reference to /stargate (as all commands are now /wormhole)

## 0.3 (1/26/11)
Name change to "WORMHOLE EXTREME"!!!
To successfully use this rename and already had "Stargates" you will need to
Rename the "StargatesDB" and all files inside it to "WormholeXTremeDB"
Delete the old Stargates.jar
Fixed sign scrolling again.
Breaking the DHD will not remove the gate - it just makes it unable to dial out
Replacing the DHD will re-enable dialing.
Permissions are now fully working and stored
Use /wormhole perms for more detail
Configuration options are now available
/wormhole timeout (3-60) : # seconds to timeout wormhole
/wormhole material <MATERIAL> : Gets or sets your portal material (air, water, lava, etc)
Possible performance improvement using ConcurrentHashMap instead of locking and using HashMap.
Iris is still in progress 

[Back to top](#changelog)
