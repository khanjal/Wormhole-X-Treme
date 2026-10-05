# CurseForge

Copy for a **new** CurseForge project in the Bukkit Plugins section. **Not published yet.** Facts and
prose come from [`shared.md`](shared.md); what is here is CurseForge's own field set and markup.

Nothing in this file has been read off the real form. Every row is **(assumed)** from how
CurseForge normally works, so check each against the form as it is filled in and correct it here,
the way the other site files were corrected after their first publish.

**CurseForge takes HTML, not Markdown or BBCode.** Its description is a rich-text editor with a
source view **(assumed)**, so the page below is HTML and does not paste into the other two sites
or the reverse. Everything else in this file is the same job as the other site files.

## The existing project, and why this is a new one

An older Wormhole X-Treme project already sits at
<https://www.curseforge.com/minecraft/bukkit-plugins/wormhole_x-treme>. Its last file is from 2015
and it belongs to **lycano**'s WolfNetDevelopment fork, a different line from this one: this fork
descends from the original at 0.854 (2011), see the [lineage](shared.md#credits) note. That is why
this is a separate project rather than a takeover, and why the description has to say so.

Before publishing, **ask the owner first**. They were active on the site this year, so a message
reaches them, and the options in order of preference are:

1. A note on their page pointing to this one.
2. A team invite or transfer, if they would rather hand it over.
3. A separate project, as written below, if they decline or do not answer.

Do not call the old project abandoned in a message or on the page. It is not, and it is not worth
a dispute.

Whatever happens, **do not position this fork against theirs**, per the convention in
[`README.md`](README.md#conventions). The lineage line says it once and that is all that is
needed.

## Fields

| Field | Value |
|---|---|
| Project name | `Wormhole X-Treme`. If the form rejects an exact duplicate of the existing project, use `Wormhole X-Treme (Continued)`. Keep the base name first so search still finds it. |
| Slug | `wormhole_x-treme` is taken. Try `wormhole-x-treme`. If that is taken too, `wormhole-x-treme-continued`. |
| Section | Bukkit Plugins |
| Main category | Teleportation, if the list has it |
| Additional categories | Fun and Mechanics, if offered, and nothing more. Pick the closest of whatever the form lists. |
| Summary | see [Summary](#summary) below |
| Licence | the form wants a picker, not a name. GPL-3.0 if it offers the family; otherwise the nearest option, never "All Rights Reserved", which the project is not. |
| Source | `https://github.com/khanjal/Wormhole-X-Treme` |
| Issues | `https://github.com/khanjal/Wormhole-X-Treme/issues` |
| Wiki | `https://github.com/khanjal/Wormhole-X-Treme/tree/main/docs/guide` |
| Discord | blank. Not the direct-message link, for the reason in [`shared.md`](shared.md#links). |
| Donations | blank |
| Relations | leave every row empty. `plugin.yml` declares only `softdepend`, and a required-dependency row would warn operators off installing it. If the integrations are wanted discoverable, add them as **Optional**, never Required. See [`hangar.md`](hangar.md#fields). |
| Logo | cropped from `docs/images/gates/gate-shapes-active.png`, not rendered from `logo.svg`. See the AI-image note below. |

**On the AI-image question.** CurseForge's rules **have not been checked** for an equivalent of
Modrinth's Rule 6.2.1, which bans page images "created or derived from generative AI output" —
see [`modrinth.md`](modrinth.md#rule-6-no-ai-generated-images-on-the-page). The logo SVG was
written with Claude Code, so until the rules are read, treat this site like Modrinth: **crop the
logo from a capture and leave the banner off the description.** The description below already has
no banner. If CurseForge turns out not to ban it, the banner can go in later; the reverse costs a
moderation round.

**On the section.** CurseForge splits Minecraft content into sections, and a plugin goes in
**Bukkit Plugins**, not Mods. That is the section the old project is in.

## Summary

CurseForge's summary field has a character cap that is **not known**. The 97-character preferred
tagline from [`shared.md`](shared.md#tagline) is safe against any plausible cap:

```
Stargate-style travel: dialling gates, transport rings, beaming and quantum mirrors. MC 1.20-26.3
```

If the field turns out to be roomier, the 158-character Modrinth summary in
[`modrinth.md`](modrinth.md#summary) is a better one, because it says what it costs to run.

**Do not write one that lands exactly on a stated cap.**

## Description

Paste into the editor's **source view** **(assumed)**, then switch back to the rich view and
check it before saving. CurseForge strips tags and attributes it does not allow, so the thing to
look at is whether the table and the images survived. Every tag below is a plain one
(`h2`, `h3`, `p`, `ul`, `table`, `a`, `img`, `pre`) for that reason, and nothing is styled inline.

The images are the four animated WebP captures and one PNG, hotlinked from `main` as on the other
sites. If the editor rewrites or rejects external image URLs, upload each through the editor
instead; the paths are in [`shared.md`](shared.md#images).

```html
<h2>Stargate-style travel for Bukkit, Spigot and Paper</h2>

<p><strong>Minecraft 1.20 through 26.3 · Java 17 · no dependencies</strong></p>

<p><strong>A maintained fork of the original 2011 plugin</strong> by Lologarithm and alron, <a href="https://github.com/khanjal/Wormhole-X-Treme/blob/main/LICENSE">GPL-3.0</a>, with full credits at the bottom and the source at <a href="https://github.com/khanjal/Wormhole-X-Treme">github.com/khanjal/Wormhole-X-Treme</a>. It brings the plugin to modern Minecraft and adds transport rings, beaming and quantum mirrors.</p>

<p>It descends from the original rather than from the later WolfNetDevelopment fork, which lycano maintained until 2015. That fork has its own page on this site.</p>

<p>Four ways to get somewhere, each a different trade between what you build and what you get.</p>

<table>
  <thead>
    <tr><th></th><th>What you build</th><th>Reaches</th></tr>
  </thead>
  <tbody>
    <tr><td><strong>Stargates</strong></td><td>a frame of blocks and a button</td><td>any gate, across worlds</td></tr>
    <tr><td><strong>Transport rings</strong></td><td>a circle of slabs, in pairs</td><td>its pair, same world</td></tr>
    <tr><td><strong>Beaming</strong></td><td>nothing at all</td><td>any named destination, across worlds</td></tr>
    <tr><td><strong>Quantum mirrors</strong></td><td>one banner on a wall</td><td>any other mirror, across worlds</td></tr>
  </tbody>
</table>

<p>Each has its own section below.</p>

<p><img src="https://raw.githubusercontent.com/khanjal/Wormhole-X-Treme/main/docs/images/gates/gate-dial.webp" alt="Dialling a gate" /></p>

<h3>Stargates</h3>

<p>A frame of blocks, a button, and <code>/dial</code>. Chevrons light in the show's order, the last one locks in with its own sound, and then the kawoosh. Shapes ship from Minimal to Massive, plus a Horizontal one that lies flat; a choice of dial-spin patterns, per gate if you like; an eighth chevron for a cross-world destination; an iris that sweeps shut, with remote codes; sign and redstone dialling. Shapes and material palettes are plain text files you can edit, and <code>/wormhole gate build</code> stands the shape full size in front of you as a guide before you place a block.</p>

<h3>Transport rings</h3>

<p><img src="https://raw.githubusercontent.com/khanjal/Wormhole-X-Treme/main/docs/images/rings/ring-cycle.webp" alt="Rings deploying" /></p>

<p>Lay a circle of slabs, run one command, do it again elsewhere, and the two are paired. The rings rise in whatever material you laid. A countdown you can step out of, floor and ceiling variants, two deployment styles, and everything in the circle travels: players, mobs, items, a horse with you still on it.</p>

<h3>Beaming</h3>

<p><img src="https://raw.githubusercontent.com/khanjal/Wormhole-X-Treme/main/docs/images/beams/beam-up.webp" alt="Beaming up" /></p>

<p>Nothing to build. A destination is a named point somebody stood on once, and a column of light takes you there from anywhere, across worlds. Staff curate a public list; every player keeps their own places. Charge per destination through Vault if you want to.</p>

<h3>Quantum mirrors</h3>

<p><img src="https://raw.githubusercontent.com/khanjal/Wormhole-X-Treme/main/docs/images/mirrors/mirror-effects.webp" alt="A mirror opening onto another world" /></p>

<p>Hang a banner on a wall and run one command. Walk up to it and the banner gives way to an opening showing another world's room in real blocks, with depth that shifts as you move past it. Right-click to choose where it leads, punch it to go. A look ships for every biome, and <code>-stamp</code> paints a banner from the room it stands in.</p>

<p><strong>On Paper:</strong> optional fog pulled in to where the room ends, so the far edge is fog rather than this world's hills.</p>

<h2>For the people running the server</h2>

<ul>
  <li><strong>Everything travels:</strong> minecarts and boats with their passengers, ridden horses, camels, pigs, donkeys, llamas and striders with their riders, arrows and tridents and ender pearls in mid-flight, and mobs, items and XP orbs that wander into an open gate. Tamed wolves, cats and parrots follow their owner through any of the four.</li>
  <li><strong>Configured in game.</strong> <code>/wormhole config &lt;setting&gt; &lt;value&gt;</code> changes any setting on the spot, with no reload and no restart. <code>/wormhole config sign</code> searches them.</li>
  <li><strong>Every sound is a setting</strong>, resource pack sounds included, with a volume per subsystem and <code>none</code> to silence any one of them. A gate even sounds its size: deeper and louder on a big gate, lighter on a small one.</li>
  <li><strong>Works with or without a permissions plugin.</strong> Vault and LuckPerms if you have them, a built-in fallback if you do not.</li>
  <li><strong>Plain YAML storage</strong>, one file per gate. No database.</li>
  <li><strong>Events for other plugins</strong> to watch or cancel travel, and to hear a wormhole open and close.</li>
  <li><strong>PlaceholderAPI</strong>, if you want it: gates total, gates open, gates owned and the nearest gate, for a scoreboard or tab list.</li>
  <li><strong>CoreProtect</strong>, if you want it: gate and ring construction is logged so an admin can roll it back. Off until <code>coreprotect-enabled</code> is set.</li>
  <li><strong>WorldGuard</strong>, if you want it: <code>wormhole-build</code> and <code>wormhole-use</code> region flags refuse building and using gates in a region. Off until <code>worldguard-enabled</code> is set.</li>
  <li><strong>Dynmap</strong>, if you want it: gates, rings, public beam destinations and mirrors on its web map, each as its own layer, with a line between dialled gates. Players' private beam places are never shown. Off until <code>dynmap-enabled</code> is set; on 1.21.11 use Dynmap 3.8, and there is no Dynmap for 26.x yet.</li>
  <li><strong>Anonymous usage counts</strong> go to <a href="https://bstats.org/plugin/bukkit/Wormhole%20X-Treme/34269">bStats</a>: Minecraft version, server software, and how many gates, rings, beams and mirrors, in ranges. <code>metrics-enabled: false</code> turns it off.</li>
  <li><strong>Update check</strong>: at startup, looks online for a newer release and says in the console, and to operators as they join, if there is one. Sends only the plugin and Minecraft versions. Never downloads anything. <code>update-check: false</code> turns it off.</li>
  <li><strong>Importer</strong> for gates from older Wormhole X-Treme forks' SQLite databases.</li>
</ul>

<h3>Getting started</h3>

<p>Drop the jar in <code>plugins/</code> and start the server. Nothing else is needed.</p>

<pre>/wormhole gate build Standard           # preview it, then lay the frame in obsidian
/wormhole gate complete Home            # click the DHD button first
/wormhole ring create                   # standing in a circle of slabs
/wormhole beam place set home           # where you stand
/wormhole mirror create home            # looking at a wall banner</pre>

<h2>Compatibility</h2>

<ul>
  <li><strong>Minecraft 1.20 – 26.3</strong>, one jar. CI builds and runs the test suite across that range, at every boundary where the API moved.</li>
  <li><strong>Paper</strong> is built and tested against at every one of those versions. <strong>Spigot</strong> is the API the jar is compiled against. <strong>CraftBukkit</strong> works, but has no action bar, so ring countdowns and mirror names do not appear above the hotbar. <strong>Purpur</strong> and <strong>Pufferfish</strong> are best effort. <strong>Folia is not supported.</strong></li>
  <li><strong>Java 17</strong> or later for the plugin itself. Minecraft 1.20.5+ needs the server on Java 21, and 26.1+ on Java 25. That is the server's requirement, not this plugin's.</li>
</ul>

<p>What CI proves is that the plugin compiles and its tests pass against each version. It is not a claim that every gate has been played on every one of them. Bug reports are welcome and get answered.</p>

<h2>Under the hood</h2>

<p><img src="https://sonarcloud.io/api/project_badges/measure?project=khanjal_Wormhole-X-Treme&amp;metric=coverage" alt="Coverage" />
<img src="https://sonarcloud.io/api/project_badges/measure?project=khanjal_Wormhole-X-Treme&amp;metric=sqale_rating" alt="Maintainability" />
<img src="https://sonarcloud.io/api/project_badges/measure?project=khanjal_Wormhole-X-Treme&amp;metric=reliability_rating" alt="Reliability" />
<img src="https://sonarcloud.io/api/project_badges/measure?project=khanjal_Wormhole-X-Treme&amp;metric=security_rating" alt="Security" /></p>

<ul>
  <li><strong>Tested.</strong> A test suite covering gate detection, dial sequencing, ring geometry, beam timing, mirror captures, config parsing and the command layer. Coverage is on the badge above and is measured on every push, not quoted from memory.</li>
  <li><strong>Every push builds and tests the whole matrix.</strong> Java 17 and Java 25; every supported Minecraft version on the Spigot API; Paper at every one of them; and Purpur's newest. A Minecraft version is only claimed as supported if it is in that matrix.</li>
  <li><strong>Compiled against the oldest supported API on purpose.</strong> A plugin built against an old API runs on newer servers; one built against a new API can call something an old server has never heard of, and nothing catches that until a player reports a crash. Building against the floor makes the compiler enforce the floor, and the newest-version legs of the matrix catch the opposite case, an API that has been removed.</li>
  <li><strong>Static analysis on every pull request.</strong> SpotBugs runs on each build, and SonarCloud fails a pull request that carries <em>any</em> open finding, not merely a coverage gate.</li>
  <li><strong>Nothing third-party in the jar but bStats.</strong> Every other dependency is provided or test scope, and bStats is relocated so it never meets another plugin's copy. No database: gates are one YAML file each.</li>
  <li><strong>GPL-3.0, and the issue tracker is open.</strong> Bug reports get answered and pull requests are welcome.</li>
</ul>

<h2>Documentation</h2>

<ul>
  <li><a href="https://github.com/khanjal/Wormhole-X-Treme/blob/main/docs/guide/SERVER.md">Setup, configuration, permissions and commands</a></li>
  <li><a href="https://github.com/khanjal/Wormhole-X-Treme/blob/main/docs/guide/GATES.md">Gates</a> · <a href="https://github.com/khanjal/Wormhole-X-Treme/blob/main/docs/guide/RINGS.md">Rings</a> · <a href="https://github.com/khanjal/Wormhole-X-Treme/blob/main/docs/guide/BEAMS.md">Beaming</a> · <a href="https://github.com/khanjal/Wormhole-X-Treme/blob/main/docs/guide/MIRRORS.md">Mirrors</a></li>
  <li><a href="https://github.com/khanjal/Wormhole-X-Treme/blob/main/docs/API.md">Writing a plugin against this one</a></li>
  <li><a href="https://github.com/khanjal/Wormhole-X-Treme/blob/main/CHANGELOG.md">Changelog</a> · <a href="https://github.com/khanjal/Wormhole-X-Treme/issues">Issues and bug reports</a></li>
</ul>

<h2>Credits</h2>

<p>Wormhole X-Treme was written by <strong>Lologarithm</strong> (Ben Echols) and <strong>alron</strong> (Dean Bailey), with contributions from <strong>lirelent</strong> (Ryan Metzger) and <strong>Jeremy Wood</strong>. <strong>lycano</strong> kept it alive after the original went quiet, through the WolfNetDevelopment fork until 2015.</p>

<p>This fork descends from the original rather than from that one, and brings the plugin to modern Minecraft with rings, beaming and mirrors added.</p>

<h3>Licence</h3>

<p>Free software under <a href="https://github.com/khanjal/Wormhole-X-Treme/blob/main/LICENSE"><strong>GPL-3.0</strong></a>. The source is on <a href="https://github.com/khanjal/Wormhole-X-Treme">GitHub</a> and pull requests are welcome.</p>

<p>The <strong>name and logo</strong> are excluded from that licence and covered by <a href="https://github.com/khanjal/Wormhole-X-Treme/blob/main/TRADEMARK.md">TRADEMARK.md</a> instead. More than one project carries this name, and the mark is what tells them apart. Every right the GPL grants over the code is untouched: fork it, modify it, redistribute the jar.</p>

<h3>Not affiliated with the Stargate franchise</h3>

<p>Wormhole X-Treme is an unofficial, fan-made Minecraft plugin. It is not affiliated with, endorsed by, sponsored by or associated with MGM Studios or any rights holder in the Stargate franchise. STARGATE and all related marks are the property of their respective owners, and the word is used here only to describe the kind of gate the plugin builds.</p>

<p>The plugin ships no franchise material of any kind: every sound it plays is a stock Minecraft sound, and every image is either a Minecraft screenshot or hand-drawn for this project.</p>

<h3>On how this is built</h3>

<p>Wormhole X-Treme is a fifteen-year-old plugin brought forward, not a new one generated. The original was written in 2011 by Lologarithm and alron; this fork modernises it and adds rings, beaming and mirrors.</p>

<p>Development is AI-assisted: much of the modernisation work was done with Claude Code under review, and the commit history records it. What that assistance does not do is decide what ships: every change goes through the test suite, the full build matrix and the static analysis described above before it is merged, and a maintainer reads it. The placeholder logo was drawn the same way, which <a href="https://github.com/khanjal/Wormhole-X-Treme/blob/main/TRADEMARK.md">TRADEMARK.md</a> says in as many words.</p>

<p>The code is all there under GPL-3.0. Read it, fork it, or tell me where it is wrong.</p>
```

**What differs from [`modrinth.md`](modrinth.md#description).** The second paragraph is new: it names
the old CurseForge project's lineage on the page, because two projects with one name on one site is
a question readers will ask, and the other sites have no equivalent. It says only where this fork
came from, as the convention requires. The Modrinth gallery section is absent for the same reason
as on Modrinth: see below.

## Gallery

CurseForge has an images tab **(assumed)** that takes uploads with a title and description, and
the six gallery PNGs go there rather than inline. The set, titles and descriptions are the same as
[`modrinth.md`](modrinth.md#gallery). Upload the files rather than hotlinking.

## Version upload

| Field | Value |
|---|---|
| Display name | that version's title in [`versions.md`](versions.md) |
| Release type | Release |
| Game versions | that version's Minecraft range in [`versions.md`](versions.md), each one ticked **by hand**. Do not trust an auto-detected list: Modrinth's reads `api-version` and ticks 1.20.x alone, and this one may do the same. Snapshots off. |
| Java version | 17 **(assumed that the form has this field)** |
| File | `WormholeXTreme-<version>.jar` from the release |
| Changelog | that version's block in [`versions.md`](versions.md). The changelog field takes HTML, or possibly Markdown **(assumed)**: if the editor shows a toolbar, paste the Markdown block in the source view and convert the few bullets by hand rather than relying on a conversion. |

## Keeping it current

Not published. Before the first upload:

1. **Message the owner of the old project**, per [the section above](#the-existing-project-and-why-this-is-a-new-one),
   and give them time to answer before creating anything.
2. **Read CurseForge's rules for an AI-image clause** and for duplicate project names. Settle the
   logo, then record the result here and in [`shared.md`](shared.md#images) as was done for
   Modrinth.
3. **Check which version the project would carry.** `v1.9.0` is the newest release.
4. **Crop the logo from a capture**, per the note above. Size unverified.
5. **Once it is approved**, add the link to the README's download row and to the table in
   [`shared.md`](shared.md#where-the-listings-live), and add a CurseForge row to each of the
   release steps in [`README.md`](README.md#updating-for-a-release). A badge may exist on shields.io under
   `curseforge/dt/<project id>`, which is the numeric id rather than the slug **(assumed)**.
