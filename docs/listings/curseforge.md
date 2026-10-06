# CurseForge

Copy for the CurseForge project, **not yet published**: the project is being created as of
2026-10-06. Put its URL here, in [`README.md`](README.md) and in
[`shared.md`](shared.md#where-the-listings-live) once it exists. Facts and prose come from
[`shared.md`](shared.md); what is here is CurseForge's own field set and markup.

Rows marked **(documented)** come from CurseForge's own support articles, read on 2026-10-06 (see
[Sources](#sources)). Rows marked **(assumed)** come from how the site normally works rather than
from looking; check those against the form and correct them here.

CurseForge's editor has a **WYSIWYG** mode (the default) and a **Markdown** mode. Pick Markdown
and paste the description below into it; it is the Modrinth description with the CurseForge
differences noted. The maintainer saw both modes on the form on 2026-10-06, and CurseForge's
[Project Submission Guide and Tips](https://support.curseforge.com/en/support/solutions/articles/9000199552)
lists "WYSIWYG/Markdown" as a field on the submission page. No CurseForge page read says which
Markdown flavour it renders, whether raw HTML survives, or whether external or animated WebP
images embed — check the preview before submitting.

A project goes to **moderation** once a file is uploaded, and is not public until a moderator
approves it **(documented)**.

---

## Fields

| Field | Value |
|---|---|
| Game | Minecraft |
| Project name **(documented)** | `Wormhole X-Treme` — English, unique, and no versions or class names in it |
| Logo **(documented)** | PNG, 400×400, rendered from `docs/images/logo.svg`; not WebP. See [AI-generated images](#ai-generated-images) |
| Summary | see [Summary](#summary) below |
| Class **(assumed)** | Bukkit Plugins |
| Main category **(assumed)** | Teleportation |
| Additional categories **(documented: up to 4)** | Mechanics, Fun |
| Allow comments | off — support goes to the issue tracker, see [`shared.md`](shared.md#links) |
| Experimental | off |
| Editor **(confirmed)** | Markdown |
| Description | see [Description](#description) below |
| Licence **(assumed)** | GNU GPL v3 from the dropdown; if it offers `-or-later`, take that — see the note in [`modrinth.md`](modrinth.md#fields) |
| Issues tracker **(documented)** | external: `https://github.com/khanjal/Wormhole-X-Treme/issues` |
| Source **(documented)** | `https://github.com/khanjal/Wormhole-X-Treme` |
| Wiki **(documented)** | `https://github.com/khanjal/Wormhole-X-Treme/tree/main/docs/guide` |
| Donation method **(documented)** | none |
| Project relations **(documented)** | none — see below |

Source, Wiki, Issues and Donation are on the project's settings once it exists, not on the
creation form **(documented)**. There is no Discord field in the articles read; leave any such
field blank, for the reason in [`shared.md`](shared.md#links).

**On the class and categories.** CurseForge's Minecraft classes include Bukkit Plugins, and its
public Bukkit Plugins browse page filters by these categories: Admin Tools, Anti-Griefing Tools,
Chat Related, Developer Tools, Economy, Fixes, Fun, General, Informational, Mechanics,
Miscellaneous, Role Playing, Teleportation, Twitch Integration, Website Administration, World
Editing and Management, World Generators. That list was read off the browse page, not the form,
hence **(assumed)**. Teleportation is the core of it; Mechanics matches Modrinth's Game Mechanics,
and Fun stands in for Adventure. Admin Tools stays off for the reason Utility is not featured on
Modrinth, and Developer Tools for the reason Library is off there. CurseForge warns that unrelated
categories send a project back for changes.

**On server types.** Modrinth tags Bukkit, Spigot, Paper and Purpur separately. No CurseForge page
read shows an equivalent tag for Bukkit plugins: the browse page's loader filter lists Forge,
Fabric, NeoForge and Quilt only. If the form offers server types, tick Bukkit, Spigot, Paper and
Purpur and leave Folia off, as on Modrinth.

**On project relations.** `plugin.yml` declares no `depend:`, only `softdepend:`, so there is no
required dependency. CurseForge offers embedded, optional and required libraries, tools and
incompatibilities, and a Required Dependency is downloaded automatically by the app
**(documented)**. Leave them empty, as on Hangar: the listing's strongest line is "no
dependencies". If the integrations are ever wanted discoverable, add them as Optional Dependency,
and only after finding each one's project on CurseForge.

**On the fork rule.** CurseForge's moderation policies say a fork must credit and link the
original creator and describe what changed, and may not copy the original's description
**(documented)**. The lineage line and the credits do the first and the second; this description
is new. The link to the original is the same open item as on Modrinth — see
[`modrinth.md`](modrinth.md#rule-6-no-ai-generated-images-on-the-page), under *Contains derivative
content* — so the credits link the repository rather than guess at a URL.

### AI-generated images

**CurseForge has no ban like Modrinth's 6.2.1.** Its Moderation Policies (modified 2026-09-08,
read 2026-10-06) carry one AI rule, under *AI Misleading Content Disclosure*: an AI-modified
showcase image "that may misrepresent the mod's actual content" must carry a clear disclaimer, on
the image or in the description. The Mod Author Terms (last updated 2025-07-21) and Overwolf's
website Terms of Use were searched for the same and say nothing about AI. The logo guidance asks
for "your original graphic", meaning not another project's avatar or the game's logo.

**So the logo goes on as the avatar.** It is the project's own mark, not a showcase of the mod's
content, and the description's *On how this is built* says the logo was drawn with Claude Code
anyway, which would satisfy the disclosure rule if it applied. Every clip and screenshot is a real
capture, so none of them needs the disclaimer.

**The banner stays out of the description**, as on Modrinth, but for a different reason:
`logo-banner.svg` is SVG, and nothing read says CurseForge renders SVG in a description. The
gate-dialling clip leads instead.

Re-read the moderation policies before each upload; Modrinth's rule arrived after its page did.

## Summary

CurseForge asks for one sentence on what the project does, "not who created it, or who is it for"
**(documented)**. No character limit was found in its articles; read it off the form and record it
here. The preferred tagline from [`shared.md`](shared.md#tagline) fits any cap the other sites set:

```
Stargate-style travel: dialling gates, transport rings, beaming and quantum mirrors. MC 1.20-26.3
```

(97.) The lineage-leading alternate in [`modrinth.md`](modrinth.md#summary) does not suit this
field: it leads with who made it, which CurseForge asks summaries not to do.

## Description

Paste into the editor's **Markdown** mode. This is the Modrinth description with one change: the
non-affiliation notice takes `shared.md`'s wording, since the logo is on this page.

````markdown
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

- **Dialling the way the show does it.** Chevrons light in order — down the right side, up the left, the top one last — at half a second each on a Standard gate and a little slower on bigger ones. The last one holds two seconds and locks in with its own sound. Then the kawoosh.
- **Dial-spin patterns.** The dialling gate's inner ring turns before each chevron locks: `top` sweeps half the ring and reverses each glyph, `chevron` steps from each locked chevron to the next, `lap` takes a whole turn clockwise, `fill` lights the ring behind it as it goes, `pegasus` dials as an Atlantis gate does, `universe` as Destiny's, `chase` and `overshoot` add two more, and `none` turns it off. A gate, or a whole material group, can pick its own.
- **An eighth chevron** locks when the destination is in another world, after the top one.
- **The gate shapes that ship** — Standard, Large, Grand, Massive, Minimal and Horizontal, the last lying flat to be dropped into rather than walked through. Shapes are plain text files: copy one, edit the grid, and `/wormhole gate shapes reload` tries it without a restart. Shipped files update themselves when you have not edited them, and never overwrite one you have.
- **Material groups.** A shape is geometry; a group is what it is built from — frame, portal, iris, chevron, light and sign block. Build `Standard` in obsidian or in lapis and get a different-looking gate from one shape file. Several groups ship, you can write as many as you like, and a gate framed in a material no group declares gets one added for it automatically. Per-gate overrides beat the shape, which beats the group.
- **A building assistant.** `/wormhole gate build <shape>` stands the shape full size in front of you, seen by you alone and made of no blocks, so you can build straight into it. Then: `-materials` lists what it will cost you block by block; `-guide` draws what is still to place, outlines a wrong block in red and makes a correct one disappear; `-layer` steps through a deep gate a layer at a time; `-activate` test-dials it; `-iris`, `-chevrons`, `-dhd` and `-material` redress it; `-share` shows it to another player or the whole world; and `-place` builds it for real.
- **An iris, with remote codes.** A closed iris bounces anyone dialling in, and sweeps shut a ring at a time, or as a spiral, rows or columns. Give a gate an IDC and callers can open it from the other end.
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
- **Build and fire them from command blocks.** `ring build` pairs two laid circles by their coordinates and `ring fire` sets a pair off with nobody in it, from console or a command block, and `~` counts from the block — so a map's rings keep working wherever it is pasted.

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

- **One banner on a wall** and `/wormhole mirror create <name>`. That is the whole job. Hang two banners side by side and the pair is one mirror, two wide and two tall. From console or a command block, give the banner's world and coordinates instead.
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
- **Events for other plugins** to watch or cancel travel, and to hear a wormhole open and close.
- **PlaceholderAPI**, if you want it: gates total, gates open, gates owned and the nearest gate, for a scoreboard or tab list.
- **CoreProtect**, if you want it: gate and ring construction is logged so an admin can roll it back. Off until `coreprotect-enabled` is set.
- **WorldGuard**, if you want it: `wormhole-build` and `wormhole-use` region flags refuse building and using gates in a region. Off until `worldguard-enabled` is set.
- **Dynmap**, if you want it: gates, rings, public beam destinations and mirrors on its web map, each as its own layer, with a line between dialled gates. Players' private beam places are never shown. Off until `dynmap-enabled` is set; on 1.21.11 use Dynmap 3.8, and there is no Dynmap for 26.x yet.
- **Anonymous usage counts** go to [bStats](https://bstats.org/plugin/bukkit/Wormhole%20X-Treme/34269): Minecraft version, server software, and how many gates, rings, beams and mirrors, in ranges. `metrics-enabled: false` turns it off.
- **Update check**: at startup, looks online for a newer release and says in the console, and to operators as they join, if there is one. Sends only the plugin and Minecraft versions. Never downloads anything. `update-check: false` turns it off.
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
- **Nothing third-party in the jar but bStats.** Every other dependency is provided or test scope, and bStats is relocated so it never meets another plugin's copy. No database: gates are one YAML file each.
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

## Images

The project's **Images** tab takes uploaded images with a title and description each
**(documented)**; for a plugin it is optional. Upload the six gallery PNGs with the titles and
descriptions in [`modrinth.md`](modrinth.md#gallery), rather than inlining them in the description.

## File upload

CurseForge calls a version a file. The project is not reviewed until one is uploaded.

| Field | Value |
|---|---|
| File | `WormholeXTreme-<version>.jar` from the release. The articles say most games need a `.zip`; a Bukkit plugin is a jar **(assumed)** |
| Display name **(documented: optional)** | that version's title in [`versions.md`](versions.md) |
| Release type **(documented)** | Release. A project needs at least one Release file to sync to the CurseForge app |
| Release options **(documented)** | live once it passes moderation |
| Supported versions **(documented)** | that version's Minecraft range in [`versions.md`](versions.md), every release ticked. Whether Bukkit plugins list each patch or only minor versions is unverified |
| Changelog | that version's Markdown block in [`versions.md`](versions.md), with the changelog type set to Markdown |
| Related projects **(documented)** | none, per [Fields](#fields) |

**On the changelog type.** CurseForge's upload API takes a changelog as `text`, `html` or
`markdown`, defaulting to text **(documented)**. If the web form offers the same choice, pick
Markdown, or the block shows its asterisks.

## Keeping it current

1. **Record the URL** in the opening paragraph above, in [`README.md`](README.md) and in
   [`shared.md`](shared.md#where-the-listings-live), once moderation approves the project.
2. **Correct the (assumed) rows** from the form: class, categories, licence, server types, the
   summary's character limit and how supported versions are listed.
3. **Check the description preview** for the animated WebP clips and the Sonar badges. If either
   fails to render, upload the image to the Images tab and point the description at it.
4. **Re-read the moderation policies' AI rule** before each upload, per
   [AI-generated images](#ai-generated-images).

## Sources

Read on 2026-10-06.

- [Moderation Policies](https://support.curseforge.com/en/support/solutions/articles/9000197279),
  modified 2026-09-08 — the AI disclosure rule, avatar size and WebP bug, fork rule, summary
  guidance.
- [Project Submission Guide and Tips](https://support.curseforge.com/en/support/solutions/articles/9000199552),
  modified 2024-12-23 — every creation-form field, the 400×400 PNG logo, WYSIWYG/Markdown, the
  settings fields.
- [Creating and Submitting a Project](https://support.curseforge.com/en/support/solutions/articles/9000197241),
  modified 2024-09-24 — the form fields and the file upload fields.
- [File & Project Types and Additional Fields](https://support.curseforge.com/en/support/solutions/articles/9000197242),
  modified 2026-07-09 — release types and relations.
- [CurseForge Upload API](https://support.curseforge.com/en/support/solutions/articles/9000197321),
  modified 2026-09-27 — changelog types and relation types.
- [Mod Authors Terms](https://legal.overwolf.com/docs/curseforge/mod-authors-terms), last updated
  2025-07-21, and Overwolf's [website Terms of Use](https://legal.overwolf.com/docs/overwolf/website/terms-of-use/)
  — no AI clause in either.
- The public [Bukkit Plugins](https://www.curseforge.com/minecraft/bukkit-plugins) browse page —
  the category names.
