# Modrinth

Copy for a Modrinth project at <https://modrinth.com/plugins>. Facts and prose come from
[`shared.md`](shared.md); what is here is Modrinth's own field set and markup.

Fields marked **(assumed)** come from how Modrinth normally works, not from looking at the form —
its docs are not reachable from the sessions this was written in. Check each one against the page
and correct it here.

Modrinth takes **Markdown**, not BBCode, so the description below is close to `shared.md` as
written. A project is **submitted for review** before it goes public **(assumed)**; expect a wait
between filling this in and it appearing.

---

## Fields

| Field | Value |
|---|---|
| Name | `Wormhole X-Treme` |
| Slug / URL | `wormhole-x-treme` |
| Summary | see [Summary](#summary) below |
| Project type | Plugin |
| Loaders **(assumed)** | Bukkit, Spigot, Paper, Purpur — **not** Folia, which is unsupported |
| Game versions | 1.20 through 26.3 |
| Client side **(assumed)** | Unsupported |
| Server side **(assumed)** | Required |
| Categories **(assumed: 3 max)** | Transportation, Game Mechanics, Utility |
| Licence | `GPL-3.0-or-later` |
| Issue tracker | `https://github.com/khanjal/Wormhole-X-Treme/issues` |
| Source code | `https://github.com/khanjal/Wormhole-X-Treme` |
| Wiki | `https://github.com/khanjal/Wormhole-X-Treme/tree/main/docs/guide` |
| Discord | leave blank — see the note in [`shared.md`](shared.md#links) |
| Donation links | none |
| Icon **(assumed: 512×512)** | PNG rendered from `docs/images/logo.svg` |

**On the licence field.** `LICENSING.md` and the README both say "GPL-3.0" without qualifying it,
but `NOTICE.txt` carries the full grant: "either version 3 of the License, or (at your option) any
later version". That is `GPL-3.0-or-later` in SPDX, which is what Modrinth's picker wants. Picking
`GPL-3.0-only` would be narrower than what the project actually grants.

**On loaders.** Modrinth treats Bukkit, Spigot, Paper, Purpur and Folia as separate loader tags.
Tick the four this supports and leave Folia off — ticking it would put the plugin in front of
exactly the operators it does not work for.

**On the version list.** Modrinth wants versions picked from its own list rather than a range, so
this is 1.20 through 26.3 inclusive. CI proves the ones at each boundary where the API moved; the
rest are in-between versions expected to work, which is the normal meaning of the field. Leave
the snapshots toggle off.

## Summary

Modrinth's summary cap is 256 characters **(assumed)**, which is roomier than Spigot's 100. The
tagline from [`shared.md`](shared.md#tagline) fits with space to spare, and there is room to say
what it costs to run:

```
Stargate-style travel: dialling gates, transport rings, beaming and quantum mirrors. Minecraft 1.20-26.3 in one jar, Java 17, no dependencies and no database.
```

(158 characters.) If the field turns out to be tighter, fall back to the 97-character preferred
tagline in [`shared.md`](shared.md#tagline).

## Description

Paste from here down. Modrinth renders GitHub-flavoured Markdown and strips most raw HTML
**(assumed)**, so this uses no HTML — the centred banner and images from the Spigot version become
ordinary Markdown images.

````markdown
![Wormhole X-Treme](https://raw.githubusercontent.com/khanjal/Wormhole-X-Treme/main/docs/images/logo-banner.svg)

## Stargate-style travel for Bukkit, Spigot and Paper

**Minecraft 1.20 through 26.3 · Java 17 · no dependencies**

**A maintained fork of the original 2011 plugin** by Lologarithm and alron,
[GPL-3.0](https://github.com/khanjal/Wormhole-X-Treme/blob/main/LICENSE), with full credits at the
bottom and the source at
[github.com/khanjal/Wormhole-X-Treme](https://github.com/khanjal/Wormhole-X-Treme). It brings the
plugin to modern Minecraft and adds transport rings, beaming and quantum mirrors.

Four ways to get somewhere, each a different trade between what you build and what you get.

- **Stargates** — a frame of blocks. Press the button, then `/dial`; or fit a dial sign and dial it with a button or redstone. Reaches any gate, across worlds.
- **Transport rings** — a circle of slabs, in pairs. Walk in. Same world.
- **Beaming** — build nothing at all. `/wormhole beam to <name>` from anywhere.
- **Quantum mirrors** — one banner on a wall. Right-click to choose, punch to go. Across worlds.

---

## ★ Stargates

![Dialling a gate](https://raw.githubusercontent.com/khanjal/Wormhole-X-Treme/main/docs/images/gates/gate-dial.webp)

- **Dialling the way the show does it.** Chevrons light in order — down the right side, up the left, the top one last — at half a second each on a Standard gate and a little slower on bigger ones. The last one holds a second and locks in with its own sound. Then the kawoosh.
- **Dial-spin patterns.** The dialling gate's inner ring turns before each chevron locks: `top` sweeps half the ring and reverses each glyph, `chevron` lands on the chevron itself, `lap` takes a whole turn clockwise, `fill` lights the ring behind it as it goes, `pegasus` dials as an Atlantis gate does, and `none` turns it off. No pattern changes how fast a gate dials.
- **An eighth chevron** locks when the destination is in another world, after the top one.
- **The gate shapes that ship** — Standard, Large, Grand, Massive, Minimal and Horizontal, the last lying flat to be dropped into rather than walked through. Shapes are plain text files: copy one, edit the grid, and `/wormhole gate shapes reload` tries it without a restart. Shipped files are written out on first run and never overwrite yours.
- **Material groups.** A shape is geometry; a group is what it is built from — frame, portal, iris, chevron, light and sign block. Build `Standard` in obsidian or in lapis and get a different-looking gate from one shape file. Several groups ship, you can write as many as you like, and a gate framed in a material no group declares gets one added for it automatically. Per-gate overrides beat the shape, which beats the group.
- **A building assistant.** `/wormhole gate build <shape>` stands the shape full size in front of you, seen by you alone and made of no blocks, so you can build straight into it. Then: `-materials` lists what it will cost you block by block; `-guide` draws what is still to place, outlines a wrong block in red and makes a correct one disappear; `-layer` steps through a deep gate a layer at a time; `-activate` test-dials it; `-iris`, `-chevrons`, `-dhd` and `-material` redress it; `-share` shows it to another player or the whole world; and `-place` builds it for real.
- **An iris, with remote codes.** A closed iris bounces anyone dialling in. Give a gate an IDC and callers can open it from the other end.
- **Sign dialling and redstone.** A dial sign steps through destinations on right-click, with the selection coloured and wrapped in `» «` so it reads for a colourblind player. Wire redstone to the marked cell and a pulse dials whatever the sign shows; a second marked cell drives a lever while the gate is open, for doors and lamps.
- **Networks, owners and per-gate settings** — shutdown and activate timeouts, cooldown, redstone on or off, iris code, owner, and per-gate material overrides.
- **Gates work in the Nether and the End**, and a wormhole runs one way: nothing comes back up an open gate.

## ★ Transport rings

![Rings deploying](https://raw.githubusercontent.com/khanjal/Wormhole-X-Treme/main/docs/images/rings/ring-cycle.webp)

- **Built from slabs, in two patterns.** Lay a circle — 16 slabs across 7, or 12 across 6 — stand inside it and run `/wormhole ring create`. Do it again elsewhere in the same world and the two are paired. A refusal says exactly what is wrong, and `ring cancel` gives your slabs back.
- **The material is whatever you laid.** Deepslate slabs rise as deepslate. Every end can then be re-dressed on its own: `ring` (any slab, including one a data pack adds), `light` for the pad while it works, `flash` for the light running through the stack, and `built` for what `reset` puts back.
- **Two deployment styles, per end.** `CONCURRENT` sends several rings up together; `SEQUENTIAL` raises them one at a time. Set the default server-wide, or `ring edit style fast|slow` one end at a time.
- **Floor rings and ceiling rings.** Bottom slabs on a floor make a ring that rises out of it; top slabs under a ceiling make one whose rings fall to the floor around you.
- **A countdown you can walk out of.** The floor opens and counts down; step clear before it commits and it stands down. A pair then rests before it fires again.
- **Everything in the ring travels** — players, mobs, items, vehicles. Ride in on a horse and you arrive still on it. Only players are checked for access.
- **It refuses an unfit arrival**: every square clear, solid ground under it, water and lava not counting. A refused trip costs nothing, and the pattern is flashed to whoever was turned away.
- **Owners, access and quotas.** Pairs start public or private, `ring allow` and `ring deny` manage a private pair's list, `ring owner` hands it over, and a per-player quota caps how many anyone may own.
- **Every timing is a setting** — countdown, cooldown, animation speed, how long the stack settles, how long each ring stays lit, how long the pad glows afterwards — as are the maximum distance and height between two ends.
- **Name an end** and its partner tells travellers where they are heading.

## ★ Beaming

![Beaming up](https://raw.githubusercontent.com/khanjal/Wormhole-X-Treme/main/docs/images/beams/beam-up.webp)

- **Nothing to build.** A destination is a named point somebody stood on once. A column of light takes you there from anywhere.
- **Public destinations and private places.** Staff curate a public list; every player keeps their own places. A name is looked up in your own first, then the public list.
- **Crosses worlds freely**, and a destination in an unloaded world says so rather than loading it.
- **Charge for it, per destination.** Set a default cost, then override it on any public destination — through Vault, and only if you want to.
- **Admin travel.** Beam yourself or another player straight to a player, a destination, or raw coordinates in any world — from console or a command block too.
- **The whole sequence is tick-by-tick configurable**: how long the glow gathers, how far into it you vanish, how long the column rises, when the teleport lands, how long it descends and fades. The two "at step" values are clamped inside their phase, so a traveller can never be left frozen and invisible.
- **An optional per-player cooldown.**
- **You arrive facing the way the destination was saved**, and on the nearest safe spot if the ground has since changed.

## ★ Quantum mirrors

![A mirror opening onto another world](https://raw.githubusercontent.com/khanjal/Wormhole-X-Treme/main/docs/images/mirrors/mirror-effects.webp)

- **One banner on a wall** and `/wormhole mirror create <name>`. That is the whole job. Hang two banners side by side and the pair is one mirror, two wide and two tall.
- **It opens onto the room beyond.** Walk up and the banner gives way to an opening its own size showing another world's room in real blocks — so the view has depth and shifts as you move past it. Nothing in the world changes; only the players looking in are sent the view.
- **Right-click to choose, punch to travel.** Right-click steps through the other mirrors by name; punch it and you land in front of that mirror's banner, facing out into its room. A whole round trip takes three seconds and no commands.
- **A look for every biome in the game**, plus `hub`, `exit`, `market`, `warning`, `private`, `shrine`, `vault` and more. They are plain text files: edit one and it stays edited, delete one and it comes back, add your own and the plugin offers it.
- **Stamp a look from the room itself.** `mirror set <name> -stamp` reads the room and paints the banner from it: the biome picks the frame — rising flame for the Nether, white crests over blue for an ocean — and the blocks around it become coarse squares in their dominant colours. Indoors, the room's own blocks decide, so a library comes back the brown of its shelves.
- **The view is a capture**, taken once and kept on disk, so a mirror onto an archived world still shows it without loading that world.
- **Tunable depth.** How far the room is drawn is a setting (about as far as a server sends, by default). A deep redraw rests before the next, so a mirror can never take more than a quarter of the server's time however close you stand.
- **It says what it is.** Look at one from a few blocks and it tells you above the hotbar what a click will do. `mirror debug` lists every fact about a mirror in green and red — a gap in the wall, another mirror too near, a missing capture.
- **Paper bonus:** optional fog pulled in to where the room ends, so the far edge is fog rather than this world's hills.

---

## For the people running the server

- **Everything travels** — minecarts and boats with their passengers, ridden horses, camels, pigs, donkeys, llamas and striders with their riders, arrows and tridents and ender pearls in mid-flight, and mobs, items and XP orbs that wander into an open gate. Tamed wolves, cats and parrots follow their owner through any of the four.
- **Configured in game.** `/wormhole config <setting> <value>` changes any setting on the spot — no reload, no restart. `/wormhole config sign` searches them.
- **Every sound is a setting**, resource pack sounds included, with a volume per subsystem and `none` to silence any one of them. A gate even sounds its size: deeper and louder on a big gate, lighter on a small one.
- **Works with or without a permissions plugin.** Vault and LuckPerms if you have them, a built-in fallback if you do not.
- **Plain YAML storage**, one file per gate. No database.
- **Events for other plugins** to watch or cancel travel.
- **Importer** for gates from older Wormhole X-Treme forks' SQLite databases.

### Getting started

Drop the jar in `plugins/` and start the server. Nothing else is needed.

```
/wormhole gate build Standard           # preview it, then lay the frame in obsidian
/wormhole gate complete Home            # click the DHD button first
/wormhole ring create                   # standing in a circle of slabs
/wormhole beam place set home           # where you stand
/wormhole mirror create home            # looking at a wall banner
```

---

## Compatibility

- **Minecraft 1.20 – 26.3.** CI builds and runs the test suite across that range, at every boundary where the API moved.
- **Spigot** is the primary target — the API this is compiled against. **Paper** is supported and built against at every version. **CraftBukkit** works, but has no action bar, so ring countdowns and mirror names do not appear above the hotbar. **Purpur** and **Pufferfish** are best effort. **Folia is not supported.**
- **Java 17** or later for the plugin itself. Minecraft 1.20.5+ needs the server on Java 21, and 26.1+ on Java 25 — that is the server's requirement, not this plugin's.

What CI proves is that the plugin compiles and its tests pass against each version. It is not a
claim that every gate has been played on every one of them. Bug reports are welcome and get
answered.

## Under the hood

![Coverage](https://sonarcloud.io/api/project_badges/measure?project=khanjal_Wormhole-X-Treme&metric=coverage)
![Maintainability](https://sonarcloud.io/api/project_badges/measure?project=khanjal_Wormhole-X-Treme&metric=sqale_rating)
![Reliability](https://sonarcloud.io/api/project_badges/measure?project=khanjal_Wormhole-X-Treme&metric=reliability_rating)
![Security](https://sonarcloud.io/api/project_badges/measure?project=khanjal_Wormhole-X-Treme&metric=security_rating)

- **Tested.** A test suite covering gate detection, dial sequencing, ring geometry, beam timing, mirror captures, config parsing and the command layer. Coverage is on the badge above and is measured on every push, not quoted from memory.
- **Every push builds and tests the whole matrix.** Java 17 and Java 25; every supported Minecraft version on the Spigot API; Paper at every one of them; and Purpur's newest. A Minecraft version is only claimed as supported if it is in that matrix.
- **Compiled against the oldest supported API on purpose.** A plugin built against an old API runs on newer servers; one built against a new API can call something an old server has never heard of, and nothing catches that until a player reports a crash. Building against the floor makes the compiler enforce the floor — and the newest-version legs of the matrix catch the opposite case, an API that has been removed.
- **Static analysis on every pull request.** SpotBugs runs on each build, and SonarCloud fails a pull request that carries *any* open finding, not merely a coverage gate. A 2026-09 refactoring campaign cleared the open backlog and closed every "method too complex" finding on the way.
- **Nothing third-party in the jar.** Every dependency is provided or test scope; there is no shading, no bundled library, and no database. Gates are one YAML file each.
- **GPL-3.0, and the issue tracker is open.** Bug reports get answered and pull requests are welcome.

## Documentation

- [Setup, configuration, permissions and commands](https://github.com/khanjal/Wormhole-X-Treme/blob/main/docs/guide/SERVER.md)
- [Gates](https://github.com/khanjal/Wormhole-X-Treme/blob/main/docs/guide/GATES.md) · [Rings](https://github.com/khanjal/Wormhole-X-Treme/blob/main/docs/guide/RINGS.md) · [Beaming](https://github.com/khanjal/Wormhole-X-Treme/blob/main/docs/guide/BEAMS.md) · [Mirrors](https://github.com/khanjal/Wormhole-X-Treme/blob/main/docs/guide/MIRRORS.md)
- [Writing a plugin against this one](https://github.com/khanjal/Wormhole-X-Treme/blob/main/docs/API.md)
- [Changelog](https://github.com/khanjal/Wormhole-X-Treme/blob/main/CHANGELOG.md) · [Issues and bug reports](https://github.com/khanjal/Wormhole-X-Treme/issues)

## Credits

Wormhole X-Treme was written by **Lologarithm** (Ben Echols) and **alron** (Dean Bailey), with
contributions from **lirelent** (Ryan Metzger) and **Jeremy Wood**. **lycano** kept it alive after
the original went quiet, through the WolfNetDevelopment fork until 2015.

This fork descends from the original rather than from that one, and brings the plugin to modern
Minecraft with rings, beaming and mirrors added.

### Licence

Free software under
[**GPL-3.0**](https://github.com/khanjal/Wormhole-X-Treme/blob/main/LICENSE). The source is on
[GitHub](https://github.com/khanjal/Wormhole-X-Treme) and pull requests are welcome.

The **name and logo** are excluded from that licence and covered by
[TRADEMARK.md](https://github.com/khanjal/Wormhole-X-Treme/blob/main/TRADEMARK.md) instead — more
than one project carries this name, and the mark is what tells them apart. Every right the GPL
grants over the code is untouched: fork it, modify it, redistribute the jar.

### Not affiliated with the Stargate franchise

Wormhole X-Treme is an unofficial, fan-made Minecraft plugin. It is not affiliated with, endorsed
by, sponsored by or associated with MGM Studios or any rights holder in the Stargate franchise.
STARGATE and all related marks are the property of their respective owners, and the word is used
here only to describe the kind of gate the plugin builds.

The plugin ships no franchise material of any kind: every sound it plays is a stock Minecraft
sound, and every image is either a Minecraft screenshot or hand-drawn for this project.

### On how this is built

Wormhole X-Treme is a fifteen-year-old plugin brought forward, not a new one generated. The
original was written in 2011 by Lologarithm and alron; this fork modernises it and adds rings,
beaming and mirrors.

Development is AI-assisted — much of the modernisation work was done with Claude Code under
review, and the commit history records it. What that assistance does not do is decide what ships:
every change goes through the test suite, the full build matrix and the static analysis
described above before it is merged, and a maintainer reads it. The placeholder logo was drawn the
same way, which
[TRADEMARK.md](https://github.com/khanjal/Wormhole-X-Treme/blob/main/TRADEMARK.md) says in as many
words.

The code is all there under GPL-3.0. Read it, fork it, or tell me where it is wrong.
````

## Gallery

Modrinth has a real gallery with a title and description per image, and one image marked featured
**(assumed)**. That is better than Spigot's arrangement, so the six gallery PNGs go in the gallery
rather than inline in the description — which is why the description above ends at the credits
with no gallery section.

| Image | Title | Description |
|---|---|---|
| `docs/images/gates/gate-shapes.png` | The shapes that ship | Minimal, Standard, Large, Grand, Massive and Horizontal, built side by side from one camera position. **Feature this one.** |
| `docs/images/gates/gate-shapes-active.png` | The shapes, dialled | The same set with a wormhole open. |
| `docs/images/gates/gate-horizontal.png` | Horizontal lies flat | Dropped into rather than walked through. Idle and dialled. |
| `docs/images/gates/standard-palettes.png` | One shape, several palettes | The same Standard gate built in each material group that ships. The shape file is identical; only the blocks differ. |
| `docs/images/gates/standard-palettes-active.png` | The palettes, dialled | |
| `docs/images/gates/standard-palettes-iris.png` | The palettes with the iris closed | |

Upload the files rather than hotlinking `raw.githubusercontent.com` — a gallery entry wants an
image on Modrinth's own CDN.

## Version upload

| Field | Value |
|---|---|
| Version number | `1.7.0` |
| Version title | `Wormhole X-Treme 1.7.0 (MC 1.20-26.3)` |
| Release channel | Release |
| Loaders | Bukkit, Spigot, Paper, Purpur |
| Game versions | 1.20 through 26.3 |
| File | `WormholeXTreme-1.7.0.jar` |
| Changelog | the 1.7.0 section of [`CHANGELOG.md`](../../CHANGELOG.md), pasted as Markdown |

The changelog field takes Markdown, so the release section pastes in as it stands — this is the
one place Modrinth is less work than Spigot. Lead with the **Upgrading** paragraph; it is what an
operator on 1.6.0 needs before they download.

## Before you submit

1. **There is no 1.7.0 release yet.** The newest tag is `v1.6.0`. Tag and let the release workflow
   build the jar first.
2. **Render the icon.** Modrinth wants 512×512 **(assumed)**, not the 256×256 rendered for Spigot.
   The same `docs/images/logo.svg` at a larger size.
3. **The banner is an SVG.** Modrinth's Markdown renderer generally handles SVG where Spigot's
   BBCode does not, so it is worth trying as written — but check the preview, and fall back to a
   PNG render if it does not appear.
4. **The animated WebP captures** are 124 KB to 792 KB each. They serve with the right content
   type from `raw.githubusercontent.com`. If any fails to render, upload it to the gallery and
   point the description at the gallery URL.
