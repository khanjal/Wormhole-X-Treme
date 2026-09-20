# Modrinth

Copy for the Modrinth project at <https://modrinth.com/plugin/wormhole-x-treme>, published 2026-09-19. Facts and prose come from
[`shared.md`](shared.md); what is here is Modrinth's own field set and markup.

Rows marked **(confirmed)** were read off the real form while the project was being filled in on
2026-09-19. Rows still marked **(assumed)** come from how Modrinth normally works rather than from
looking; check those against the page and correct them here.

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
| Loaders **(confirmed)** | Bukkit, Spigot, Paper, Purpur. Folia, Sponge and the proxy loaders all **off**. |
| Game versions **(confirmed)** | tick every 1.20.x, 1.21.x and 26.x individually. Snapshots off. **Modrinth's auto-detection gets this wrong — see below.** |
| Client side **(assumed)** | Unsupported |
| Server side **(assumed)** | Required |
| Categories **(confirmed)** | Transportation, Game Mechanics, Adventure, Utility — the first three set as featured tags |
| Licence | `GPL-3.0-or-later` |
| Issue tracker **(confirmed)** | `https://github.com/khanjal/Wormhole-X-Treme/issues` |
| Source code **(confirmed)** | `https://github.com/khanjal/Wormhole-X-Treme` |
| Wiki **(confirmed)** | `https://github.com/khanjal/Wormhole-X-Treme/tree/main/docs/guide` |
| Discord **(confirmed)** | blank — see the note in [`shared.md`](shared.md#links) |
| Donation links **(confirmed)** | blank |
| Icon **(assumed: 512×512)** | a crop from `docs/images/gates/gate-shapes-active.png`. **Not the logo** — see [Rule 6](#rule-6-no-ai-generated-images-on-the-page) |
| Contains AI-generated content **(confirmed)** | **On**, with Code, Assets and Text all ticked — see below |
| Contains derivative content **(confirmed)** | **On**. Original work: `Wormhole X-Treme`. The link is an open item — see below |

**The two disclosure toggles are Modrinth's alone.** Spigot and Hangar have no equivalent, which
is why they live here rather than in `shared.md`. Both are structured fields with their own
explanation boxes, and both are enabled. `shared.md`'s [AI disclosure](shared.md#ai-disclosure)
prose stays as it is — the explanations below are built on it deliberately, so the two do not
drift apart when one is edited.

**Contains AI-generated content: on, all three boxes.** The form asks it to be enabled for any
AI-generated assets or text, or a substantial amount of AI-generated code.

- **Code** — the modernisation work, which the commit history records.
- **Text** — the documentation and this listing copy.
- **Assets — the one somebody will be tempted to untick, so do not.** The logo's SVG was written
  with Claude Code rather than by a designer: [`docs/LOGO.md`](../LOGO.md) and
  [`TRADEMARK.md`](../../TRADEMARK.md) both say so in as many words. It ships in the repository
  and in the docs, so the box is accurate even though the logo is deliberately kept **off** this
  page — see [Rule 6](#rule-6-no-ai-generated-images-on-the-page).
- **Not ticked, and worth saying out loud because people assume it:** nothing here calls a model
  at runtime. The plugin's design and functionality do not rely on generative AI.

> Wormhole X-Treme is a fifteen-year-old plugin brought forward, not a new one generated. The
> original was written in 2011 by Lologarithm and alron; this fork modernises it and adds
> transport rings, beaming and quantum mirrors.
>
> Much of that modernisation work was done with Claude Code under review, and the commit history
> records it. Nothing merges on that basis alone: every change goes through the test suite, the
> full build matrix and static analysis, and a maintainer reads it.
>
> The documentation and this listing were drafted the same way, as was the project's placeholder
> logo — its SVG was hand-authored as markup rather than image-generated, but written with Claude
> Code at a maintainer's direction, which TRADEMARK.md states in as many words.
>
> No image on this page is AI-generated. The icon, the gallery and every screenshot are unedited
> captures from a running Minecraft server. The placeholder logo is deliberately not used here;
> replacing it with a commissioned mark is tracked as issue #187.

### Rule 6: no AI-generated images on the page

This is a takedown rule, not a disclosure question, and it is the reason the icon and the banner
above differ from the other two sites. Modrinth's Content Rules, section 6.2.1:

> No images uploaded to a gallery, icon, description, or any other part of a project page may be
> created or derived from generative AI output. Any such images may be removed.

**The logo is caught by this.** "Created or derived from generative AI output" is broader than the
copyright distinction the repository draws: `TRADEMARK.md` is careful to say the SVG was
hand-authored as markup rather than image-generated, which matters for authorship, but it was
still written with Claude Code. So neither `docs/images/logo.svg` nor `docs/images/logo-banner.svg`
may appear anywhere on this page — not as the icon, not in the description, not in the gallery.

**What is not caught, so nobody strips it defensively.** Every clip and screenshot is a real
capture from a running server, recorded and encoded per [`docs/CAPTURES.md`](../CAPTURES.md).
Compositing and video encoding are not generative AI. The four animated WebP clips and the six
gallery PNGs all stay.

**On 6.2.2**, which says a project may not be "entirely or primarily comprised of content created
or derived from generative AI output": this project reads as clear — a human-written 2011 plugin
modernised under review, with the history to show it. But "primarily" is a moderator's judgement
rather than a test anyone can run, and the honesty of the explanation above is the protection.
Do not trim that explanation to look better.

**Rule text provenance:** quoted from Modrinth's Content Rules as Justin read them on 2026-09-20.
Re-read section 6 before the next upload; it is the sort of rule that gets tightened.

**Contains derivative content: on.** The form asks it to be enabled for a fork or a project
containing a substantial amount of someone else's work, which this is. Name of original work:
**Wormhole X-Treme**.

**The link to the original work is unresolved.** It wants the 2011 original, and no URL for it
has been verified — the original repository may not survive, and its BukkitDev page is not
something to guess at. The only such URL the repository itself cites is
`https://github.com/WolfNetDevelopment/Wormhole-X-Treme`, in the credits section of
[`README.md`](../../README.md), and that is the *later* fork, not the original. Use whichever of
the two actually resolves, and if it is the WolfNet one, say so in the explanation rather than
letting it read as the original. Record here whatever ends up in the field, so nobody re-derives
this.

> This is a fork of the original Wormhole X-Treme, a Bukkit plugin written in 2011 by Lologarithm
> (Ben Echols) and alron (Dean Bailey), with contributions from lirelent (Ryan Metzger) and
> Jeremy Wood. It went unmaintained years ago.
>
> This fork brings it forward to Minecraft 1.20–26.3 and adds three subsystems the original never
> had: transport rings, beaming and quantum mirrors. The stargate code is descended from the
> original rather than rewritten. Licence is unchanged at GPL-3.0-or-later.
>
> It descends from the original rather than from the later WolfNetDevelopment fork, which lycano
> maintained until 2015.

That last paragraph is deliberate. It is the same lineage point that settled lycano's place in
[the credits](shared.md#credits), and it heads off confusion with the other Wormhole X-Treme
listing on Spigot. Both blocks name the supported range, so a release that moves the range has to
touch them as well as the fields table.

**On the licence field.** `LICENSING.md` and the README both say "GPL-3.0" without qualifying it,
but `NOTICE.txt` carries the full grant: "either version 3 of the License, or (at your option) any
later version". That is `GPL-3.0-or-later` in SPDX, which is what Modrinth's picker wants. Picking
`GPL-3.0-only` would be narrower than what the project actually grants.

**On the categories.** Modrinth does not cap categories at three, as first assumed: it offers
all nineteen and has a separate **Featured tags 0/3** field picking which three show on the card.
So tick four and feature the first three.

- **Transportation** and **Game Mechanics** are the core of it.
- **Adventure** because Modrinth uses that for content giving players somewhere new to go, which
  a gate network is.
- **Utility** is ticked but deliberately *not* featured. Preview mode, the building assistant and
  the admin commands justify it, but leading with it makes the plugin read as an admin tool.

Excluded on purpose, so nobody re-litigates them: **Library** (see below), **Magic** (the fiction
is science fiction), **Decoration** (gates are functional, not ornamental) and **Management**
(that means server administration).

The full nineteen, for reference: Adventure, Cursed, Decoration, Economy, Equipment, Food, Game
Mechanics, Library, Magic, Management, Minigame, Mobs, Optimization, Social, Storage, Technology,
Transportation, Utility, World Generation.

**Library stays off, on both sites.** `docs/API.md` exists and other plugins can build against
this one, but Library on these sites means a dependency installed underneath something else,
which this is not. Ticking it would put the plugin in front of developers looking for a
dependency and hide it from the operators who want a plugin.

**On loaders.** Modrinth treats each of these as a separate loader tag. Tick Bukkit, Spigot,
Paper and Purpur; leave Folia, Sponge and the proxy loaders off. Ticking Folia would put the
plugin in front of exactly the operators it does not work for.

**On the version list, and the trap in it.** Modrinth reads `api-version` out of `plugin.yml` and
offers to fill the game versions from it. `plugin.yml` says `api-version: "1.20"`, so what it
auto-detects is **1.20.x and nothing else** — which would advertise the plugin as supporting none
of 1.21 or 26.x. Override it by hand: tick every 1.20.x, 1.21.x and 26.x version individually,
and leave snapshots off. This is a consequence of compiling against the floor on purpose, so
expect it again at every release.

CI proves the versions at each boundary where the API moved; the rest are in-between versions
expected to work, which is the normal meaning of the field.

## Summary

Modrinth's summary cap is 256 characters **(assumed)**, which is roomier than Spigot's 100. The
tagline from [`shared.md`](shared.md#tagline) fits with space to spare, and there is room to say
what it costs to run:

```
Stargate-style travel: dialling gates, transport rings, beaming and quantum mirrors. Minecraft 1.20-26.3 in one jar, Java 17, no dependencies and no database.
```

(158 characters.) If the field turns out to be tighter, fall back to the 97-character preferred
tagline in [`shared.md`](shared.md#tagline).

Two fuller alternates, both measured, if the 256 cap holds:

```
Stargate-style travel for Bukkit, Spigot and Paper: dialling gates, transport rings, beaming, and quantum mirrors that open onto another world. Minecraft 1.20 through 26.3 in one jar, Java 17, no dependencies and no database.
```
(225)

```
A maintained fork of the 2011 original, bringing Stargate-style travel to modern Minecraft: dialling gates, transport rings, beaming and quantum mirrors. One jar covers 1.20 through 26.3 on Bukkit, Spigot, Paper and Purpur.
```
(223 — lineage-leading. Worth preferring where the summary is the only thing a reviewer reads,
since it answers the "whose plugin is this" question before anyone asks.)

## Description

Paste from here down. Modrinth renders GitHub-flavoured Markdown and strips most raw HTML
**(assumed)**, so this uses no HTML — the images from the Spigot version become ordinary Markdown
images. The banner that leads the Spigot description is **not** here; the gate-dialling clip leads
instead, for the reason in [Rule 6](#rule-6-no-ai-generated-images-on-the-page).

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
| Version number | `1.7.1` |
| Version title | `Wormhole X-Treme v1.7.1 (MC 1.20-26.3)` — matches the GitHub release name |
| Release channel | Release |
| Loaders | Bukkit, Spigot, Paper, Purpur |
| Game versions | every 1.20.x, 1.21.x and 26.x, ticked individually — the auto-detected list is wrong, see [Fields](#fields). Snapshots off. |
| File | `WormholeXTreme-<version>.jar` from the release |
| Changelog | the short form in [`shared.md`](shared.md#release-notes) |

The changelog field takes Markdown, so it pastes in as it stands. Use the short release notes
rather than the full `CHANGELOG.md` section: that runs to about 150 lines and reads as a wall on a
download page. It leads with the **Upgrading** bullets, which are what an upgrading operator
needs before they download.

## Keeping it current

Published 2026-09-19 at <https://modrinth.com/plugin/wormhole-x-treme>. The jar blocker is gone: `v1.7.0` and `v1.7.1` are both released,
so there is a jar to upload. What is left:

1. **Check which version the project carries.** `v1.7.1` is the newest release; the listing went
   up around `v1.7.0`.
2. **Re-check the game versions after every release.** Modrinth's auto-detection ticks 1.20.x
   alone, as above, so a new version upload can silently narrow what the page claims.
3. **Crop the icon from a capture, never from the logo.** Modrinth wants 512×512 **(assumed)**.
   `docs/images/gates/gate-shapes-active.png` is the source; the Massive gate dialled reads at
   that size. Rendering `logo.svg` here would breach Rule 6.2.1 — see below. One such crop was
   prepared on 2026-09-20 and measured 512×512; it is not in the repository, so if it has been
   lost, re-crop from the same capture.
4. **Do not add the banner back.** Whether Modrinth's renderer handles SVG is beside the point
   now; `logo-banner.svg` is AI-authored and may not go on the page at all.
5. **The animated WebP captures** are 124 KB to 792 KB each. They serve with the right content
   type from `raw.githubusercontent.com`. If any fails to render, upload it to the gallery and
   point the description at the gallery URL.
