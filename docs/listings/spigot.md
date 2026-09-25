# SpigotMC

Copy for the Spigot resource at <https://www.spigotmc.org/resources/wormhole-x-treme.138936/>, published 2026-09-19. Facts and prose come from
[`shared.md`](shared.md); what is here is Spigot's own field set and markup.

Spigot is the only one of the three that takes **BBCode** rather than Markdown, and it has the
tightest tagline cap, so it is the site the shared tagline is written to.

Fields marked **(assumed)** come from the standard XenForo resource form, not from looking at
Spigot's. The ones that are *not* marked were confirmed against a live resource page.

---

## Fields

| Field | Value |
|---|---|
| Title | `Wormhole X-Treme` |
| Tag line | `Stargate-style travel: dialling gates, transport rings, beaming and quantum mirrors. MC 1.20-26.3` |
| Category **(assumed)** | Transportation, under Spigot Plugins. Mechanics is the second choice. |
| Version | `1.8.0` — newest release |
| Native Major MC Version | `1.20` |
| Tested Major MC Versions | everything from 1.20 through 26.3 |
| Tags **(assumed: 5 max)** | `stargate`, `teleport`, `portal`, `transportation`, `wormhole` |
| Icon **(assumed: 256×256 PNG)** | rendered from `docs/images/logo.svg` — Spigot will not take SVG |
| Resource type | **Upload the jar**, not an external link |
| Source code | `https://github.com/khanjal/Wormhole-X-Treme` |
| Contributors | `Lologarithm, alron, lirelent, Jeremy Wood, Khan Jal` |
| Additional Information URL | `https://github.com/khanjal/Wormhole-X-Treme` |
| Alternative Support URL | `https://github.com/khanjal/Wormhole-X-Treme/issues` |
| Donation link | blank |

**The tag line cap is 100 characters, measured.** Not 128. A longer one pastes in and is cut at
exactly 100. The value above is 97. Alternates are in [`shared.md`](shared.md#tagline).

**Native version is `1.20` on purpose.** `pom.xml` compiles against `1.20.4-R0.1-SNAPSHOT`, so
that is the API the jar is natively built for, and the whole compatibility story is that the floor
is deliberate. The tested list carries the range. Putting 26.3 there would read as more current
and is defensible, but it is the less literal reading of the field.

**Tested versions.** CI actually builds and tests 1.20, 1.20.1, 1.20.4, 1.20.6, 1.21.1, 1.21.4,
1.21.10, 1.21.11, 26.1.2, 26.2 and 26.3 — the boundaries where the API moved. Select the
in-between versions too; that is what the field means on Spigot.

**Upload the jar rather than linking it.** The rules require an external download URL to be a
direct link, and the jar's filename carries the version, so any link needs re-pointing every
release — the same work as uploading, minus Spigot's version history, native download counts and
update notifications. A versionless `WormholeXTreme.jar` asset added to `release.yml` would make
`releases/latest/download/WormholeXTreme.jar` stable if that ever changes.

**The two URL fields want different things.** Additional Information asks for a demo or extended
description, which is the README. Alternative Support asks where questions get answered, which is
the issue tracker, not the guide — the guide is reading material and cannot answer anyone. It is
linked from the description anyway.

**The icon can wait.** It is editable from the resource's edit page at any time, unlike the
download. But a resource with no icon shows a blank placeholder in search and category listings,
so upload it at creation if the file is in hand.

## Description

Paste the whole block below into the description field.

```bbcode
[CENTER][IMG]https://raw.githubusercontent.com/khanjal/Wormhole-X-Treme/main/docs/images/logo-banner.svg[/IMG]

[SIZE=5][B]Stargate-style travel for Bukkit, Spigot and Paper[/B][/SIZE]
[SIZE=4]Minecraft 1.20 through 26.3 · Java 17 · no dependencies[/SIZE][/CENTER]


[SIZE=3][B]A maintained fork of the original 2011 plugin[/B][/SIZE] by Lologarithm and alron, [URL=https://github.com/khanjal/Wormhole-X-Treme/blob/main/LICENSE]GPL-3.0[/URL], with full credits at the bottom and the source at [URL=https://github.com/khanjal/Wormhole-X-Treme]github.com/khanjal/Wormhole-X-Treme[/URL]. It brings the plugin to modern Minecraft and adds transport rings, beaming and quantum mirrors.


Four ways to get somewhere, each a different trade between what you build and what you get.

[LIST]
[*][B]Stargates[/B] — a frame of blocks. Press the button, then [ICODE]/dial[/ICODE]; or fit a dial sign and dial it with a button or redstone. Reaches any gate, across worlds.
[*][B]Transport rings[/B] — a circle of slabs, in pairs. Walk in. Same world.
[*][B]Beaming[/B] — build nothing at all. [ICODE]/wormhole beam to <name>[/ICODE] from anywhere.
[*][B]Quantum mirrors[/B] — one banner on a wall. Right-click to choose, punch to go. Across worlds.
[/LIST]


[SIZE=5][B]★ Stargates[/B][/SIZE]

[CENTER][IMG]https://raw.githubusercontent.com/khanjal/Wormhole-X-Treme/main/docs/images/gates/gate-dial.webp[/IMG][/CENTER]

[LIST]
[*][B]Dialling the way the show does it.[/B] Chevrons light in order — down the right side, up the left, the top one last — at half a second each on a Standard gate and a little slower on bigger ones. The last one holds two seconds and locks in with its own sound. Then the kawoosh.
[*][B]Dial-spin patterns.[/B] The dialling gate's inner ring turns before each chevron locks: [ICODE]top[/ICODE] sweeps half the ring and reverses each glyph, [ICODE]chevron[/ICODE] lands on the chevron itself, [ICODE]lap[/ICODE] takes a whole turn clockwise, [ICODE]fill[/ICODE] lights the ring behind it as it goes, [ICODE]pegasus[/ICODE] dials as an Atlantis gate does, [ICODE]universe[/ICODE] as Destiny's, [ICODE]chase[/ICODE] and [ICODE]overshoot[/ICODE] add two more, and [ICODE]none[/ICODE] turns it off. A gate, or a whole material group, can pick its own.
[*][B]An eighth chevron[/B] locks when the destination is in another world, after the top one.
[*][B]The gate shapes that ship[/B] — Standard, Large, Grand, Massive, Minimal and Horizontal, the last lying flat to be dropped into rather than walked through. Shapes are plain text files: copy one, edit the grid, and [ICODE]/wormhole gate shapes reload[/ICODE] tries it without a restart. Shipped files update themselves when you have not edited them, and never overwrite one you have.
[*][B]Material groups.[/B] A shape is geometry; a group is what it is built from — frame, portal, iris, chevron, light and sign block. Build [ICODE]Standard[/ICODE] in obsidian or in lapis and get a different-looking gate from one shape file. Several groups ship, you can write as many as you like, and a gate framed in a material no group declares gets one added for it automatically. Per-gate overrides beat the shape, which beats the group.
[*][B]A building assistant.[/B] [ICODE]/wormhole gate build <shape>[/ICODE] stands the shape full size in front of you, seen by you alone and made of no blocks, so you can build straight into it. Then: [ICODE]-materials[/ICODE] lists what it will cost you block by block; [ICODE]-guide[/ICODE] draws what is still to place, outlines a wrong block in red and makes a correct one disappear; [ICODE]-layer[/ICODE] steps through a deep gate a layer at a time; [ICODE]-activate[/ICODE] test-dials it; [ICODE]-iris[/ICODE], [ICODE]-chevrons[/ICODE], [ICODE]-dhd[/ICODE] and [ICODE]-material[/ICODE] redress it; [ICODE]-share[/ICODE] shows it to another player or the whole world; and [ICODE]-place[/ICODE] builds it for real.
[*][B]An iris, with remote codes.[/B] A closed iris bounces anyone dialling in, and sweeps shut a ring at a time, or as a spiral, rows or columns. Give a gate an IDC and callers can open it from the other end.
[*][B]Sign dialling and redstone.[/B] A dial sign steps through destinations on right-click, with the selection coloured and wrapped in [ICODE]» «[/ICODE] so it reads for a colourblind player. Wire redstone to the marked cell and a pulse dials whatever the sign shows; a second marked cell drives a lever while the gate is open, for doors and lamps.
[*][B]Networks, owners and per-gate settings[/B] — shutdown and activate timeouts, cooldown, redstone on or off, iris code, owner, and per-gate material overrides.
[*][B]Gates work in the Nether and the End[/B], and a wormhole runs one way: nothing comes back up an open gate.
[/LIST]


[SIZE=5][B]★ Transport rings[/B][/SIZE]

[CENTER][IMG]https://raw.githubusercontent.com/khanjal/Wormhole-X-Treme/main/docs/images/rings/ring-cycle.webp[/IMG][/CENTER]

[LIST]
[*][B]Built from slabs, in two patterns.[/B] Lay a circle — 16 slabs across 7, or 12 across 6 — stand inside it and run [ICODE]/wormhole ring create[/ICODE]. Do it again elsewhere in the same world and the two are paired. A refusal says exactly what is wrong, and [ICODE]ring cancel[/ICODE] gives your slabs back.
[*][B]The material is whatever you laid.[/B] Deepslate slabs rise as deepslate. Every end can then be re-dressed on its own: [ICODE]ring[/ICODE] (any slab, including one a data pack adds), [ICODE]light[/ICODE] for the pad while it works, [ICODE]flash[/ICODE] for the light running through the stack, and [ICODE]built[/ICODE] for what [ICODE]reset[/ICODE] puts back.
[*][B]Two deployment styles, per end.[/B] [ICODE]CONCURRENT[/ICODE] sends several rings up together; [ICODE]SEQUENTIAL[/ICODE] raises them one at a time. Set the default server-wide, or [ICODE]ring edit style fast|slow[/ICODE] one end at a time.
[*][B]Floor rings and ceiling rings.[/B] Bottom slabs on a floor make a ring that rises out of it; top slabs under a ceiling make one whose rings fall to the floor around you.
[*][B]A countdown you can walk out of.[/B] The floor opens and counts down; step clear before it commits and it stands down. A pair then rests before it fires again.
[*][B]Everything in the ring travels[/B] — players, mobs, items, vehicles. Ride in on a horse and you arrive still on it. Only players are checked for access.
[*][B]It refuses an unfit arrival[/B]: every square clear, solid ground under it, water and lava not counting. A refused trip costs nothing, and the pattern is flashed to whoever was turned away.
[*][B]Owners, access and quotas.[/B] Pairs start public or private, [ICODE]ring allow[/ICODE] and [ICODE]ring deny[/ICODE] manage a private pair's list, [ICODE]ring owner[/ICODE] hands it over, and a per-player quota caps how many anyone may own.
[*][B]Every timing is a setting[/B] — countdown, cooldown, animation speed, how long the stack settles, how long each ring stays lit, how long the pad glows afterwards — as are the maximum distance and height between two ends.
[*][B]Name an end[/B] and its partner tells travellers where they are heading.
[/LIST]


[SIZE=5][B]★ Beaming[/B][/SIZE]

[CENTER][IMG]https://raw.githubusercontent.com/khanjal/Wormhole-X-Treme/main/docs/images/beams/beam-up.webp[/IMG][/CENTER]

[LIST]
[*][B]Nothing to build.[/B] A destination is a named point somebody stood on once. A column of light takes you there from anywhere.
[*][B]Public destinations and private places.[/B] Staff curate a public list; every player keeps their own places. A name is looked up in your own first, then the public list.
[*][B]Crosses worlds freely[/B], and a destination in an unloaded world says so rather than loading it.
[*][B]Charge for it, per destination.[/B] Set a default cost, then override it on any public destination — through Vault, and only if you want to.
[*][B]Admin travel.[/B] Beam yourself or another player straight to a player, a destination, or raw coordinates in any world — from console or a command block too.
[*][B]The whole sequence is tick-by-tick configurable[/B]: how long the glow gathers, how far into it you vanish, how long the column rises, when the teleport lands, how long it descends and fades. The two "at step" values are clamped inside their phase, so a traveller can never be left frozen and invisible.
[*][B]An optional per-player cooldown.[/B]
[*][B]You arrive facing the way the destination was saved[/B], and on the nearest safe spot if the ground has since changed.
[/LIST]


[SIZE=5][B]★ Quantum mirrors[/B][/SIZE]

[CENTER][IMG]https://raw.githubusercontent.com/khanjal/Wormhole-X-Treme/main/docs/images/mirrors/mirror-effects.webp[/IMG][/CENTER]

[LIST]
[*][B]One banner on a wall[/B] and [ICODE]/wormhole mirror create <name>[/ICODE]. That is the whole job. Hang two banners side by side and the pair is one mirror, two wide and two tall.
[*][B]It opens onto the room beyond.[/B] Walk up and the banner gives way to an opening its own size showing another world's room in real blocks — so the view has depth and shifts as you move past it. Nothing in the world changes; only the players looking in are sent the view.
[*][B]Right-click to choose, punch to travel.[/B] Right-click steps through the other mirrors by name; punch it and you land in front of that mirror's banner, facing out into its room. A whole round trip takes three seconds and no commands.
[*][B]A look for every biome in the game[/B], plus [ICODE]hub[/ICODE], [ICODE]exit[/ICODE], [ICODE]market[/ICODE], [ICODE]warning[/ICODE], [ICODE]private[/ICODE], [ICODE]shrine[/ICODE], [ICODE]vault[/ICODE] and more. They are plain text files: edit one and it stays edited, delete one and it comes back, add your own and the plugin offers it.
[*][B]Stamp a look from the room itself.[/B] [ICODE]mirror set <name> -stamp[/ICODE] reads the room and paints the banner from it: the biome picks the frame — rising flame for the Nether, white crests over blue for an ocean — and the blocks around it become coarse squares in their dominant colours. Indoors, the room's own blocks decide, so a library comes back the brown of its shelves.
[*][B]The view is a capture[/B], taken once and kept on disk, so a mirror onto an archived world still shows it without loading that world.
[*][B]Tunable depth.[/B] How far the room is drawn is a setting (about as far as a server sends, by default). A deep redraw rests before the next, so a mirror can never take more than a quarter of the server's time however close you stand.
[*][B]It says what it is.[/B] Look at one from a few blocks and it tells you above the hotbar what a click will do. [ICODE]mirror debug[/ICODE] lists every fact about a mirror in green and red — a gap in the wall, another mirror too near, a missing capture.
[*][B]Paper bonus:[/B] optional fog pulled in to where the room ends, so the far edge is fog rather than this world's hills.
[/LIST]


[SIZE=5][B]For the people running the server[/B][/SIZE]

[LIST]
[*][B]Everything travels[/B] — minecarts and boats with their passengers, ridden horses, camels, pigs, donkeys, llamas and striders with their riders, arrows and tridents and ender pearls in mid-flight, and mobs, items and XP orbs that wander into an open gate. Tamed wolves, cats and parrots follow their owner through any of the four.
[*][B]Configured in game.[/B] [ICODE]/wormhole config <setting> <value>[/ICODE] changes any setting on the spot — no reload, no restart. [ICODE]/wormhole config sign[/ICODE] searches them.
[*][B]Every sound is a setting[/B], resource pack sounds included, with a volume per subsystem and [ICODE]none[/ICODE] to silence any one of them. A gate even sounds its size: deeper and louder on a big gate, lighter on a small one.
[*][B]Works with or without a permissions plugin.[/B] Vault and LuckPerms if you have them, a built-in fallback if you do not.
[*][B]Plain YAML storage[/B], one file per gate. No database.
[*][B]Events for other plugins[/B] to watch or cancel travel, and to hear a wormhole open and close.
[*][B]PlaceholderAPI[/B], if you want it: gates total, gates open, gates owned and the nearest gate, for a scoreboard or tab list.
[*][B]CoreProtect[/B], if you want it: gate and ring construction is logged so an admin can roll it back. Off until [ICODE]coreprotect-enabled[/ICODE] is set.
[*][B]Anonymous usage counts[/B] go to [URL=https://bstats.org/plugin/bukkit/Wormhole%20X-Treme/34269]bStats[/URL]: Minecraft version, server software, and how many gates, rings, beams and mirrors, in ranges. [ICODE]metrics-enabled: false[/ICODE] turns it off.
[*][B]Importer[/B] for gates from older Wormhole X-Treme forks' SQLite databases.
[/LIST]

[B]Getting started[/B]

Drop the jar in [ICODE]plugins/[/ICODE] and start the server. Nothing else is needed.

[CODE]/wormhole gate build Standard           # preview it, then lay the frame in obsidian
/wormhole gate complete Home           # click the DHD button first
/wormhole ring create                  # standing in a circle of slabs
/wormhole beam place set home          # where you stand
/wormhole mirror create home           # looking at a wall banner[/CODE]


[SIZE=5][B]Compatibility[/B][/SIZE]

[LIST]
[*][B]Minecraft 1.20 - 26.3.[/B] CI builds and runs the test suite across that range, at every boundary where the API moved.
[*][B]Spigot[/B] is the primary target — the API this is compiled against. [B]Paper[/B] is supported and built against at every version. [B]CraftBukkit[/B] works, but has no action bar, so ring countdowns and mirror names do not appear above the hotbar. [B]Purpur[/B] and [B]Pufferfish[/B] are best effort. [B]Folia is not supported.[/B]
[*][B]Java 17[/B] or later for the plugin itself. Minecraft 1.20.5+ needs the server on Java 21, and 26.1+ on Java 25 — that is the server's requirement, not this plugin's.
[/LIST]

What CI proves is that the plugin compiles and its tests pass against each version. It is not a claim that every gate has been played on every one of them. Bug reports are welcome and get answered.


[SIZE=5][B]Under the hood[/B][/SIZE]

[CENTER][IMG]https://sonarcloud.io/api/project_badges/measure?project=khanjal_Wormhole-X-Treme&metric=coverage[/IMG] [IMG]https://sonarcloud.io/api/project_badges/measure?project=khanjal_Wormhole-X-Treme&metric=sqale_rating[/IMG] [IMG]https://sonarcloud.io/api/project_badges/measure?project=khanjal_Wormhole-X-Treme&metric=reliability_rating[/IMG] [IMG]https://sonarcloud.io/api/project_badges/measure?project=khanjal_Wormhole-X-Treme&metric=security_rating[/IMG][/CENTER]

[LIST]
[*][B]Tested.[/B] A test suite covering gate detection, dial sequencing, ring geometry, beam timing, mirror captures, config parsing and the command layer. Coverage is on the badge above and is measured on every push, not quoted from memory.
[*][B]Every push builds and tests the whole matrix.[/B] Java 17 and Java 25; every supported Minecraft version on the Spigot API; Paper at every one of them; and Purpur's newest. A Minecraft version is only claimed as supported on the README if it is in that matrix.
[*][B]Compiled against the oldest supported API on purpose.[/B] A plugin built against an old API runs on newer servers; one built against a new API can call something an old server has never heard of, and nothing catches that until a player reports a crash. Building against the floor makes the compiler enforce the floor — and the newest-version legs of the matrix catch the opposite case, an API that has been removed.
[*][B]Static analysis on every pull request.[/B] SpotBugs runs on each build, and SonarCloud fails a pull request that carries [I]any[/I] open finding — not merely a coverage gate. A 2026-09 refactoring campaign cleared the open backlog and closed every "method too complex" finding on the way.
[*][B]Nothing third-party in the jar but bStats.[/B] Every other dependency is provided or test scope, and bStats is relocated so it never meets another plugin's copy. No database: gates are one YAML file each.
[*][B]GPL-3.0, and the issue tracker is open.[/B] Bug reports get answered and pull requests are welcome.
[/LIST]


[SIZE=5][B]Gallery[/B][/SIZE]

[B]The shapes that ship[/B], built side by side from one camera position — Minimal, Standard, Large, Grand, Massive and Horizontal.

[CENTER][IMG]https://raw.githubusercontent.com/khanjal/Wormhole-X-Treme/main/docs/images/gates/gate-shapes.png[/IMG][/CENTER]

[B]The same set, dialled.[/B]

[CENTER][IMG]https://raw.githubusercontent.com/khanjal/Wormhole-X-Treme/main/docs/images/gates/gate-shapes-active.png[/IMG][/CENTER]

[B]Horizontal lies flat[/B] and is dropped into rather than walked through. Idle and dialled.

[CENTER][IMG]https://raw.githubusercontent.com/khanjal/Wormhole-X-Treme/main/docs/images/gates/gate-horizontal.png[/IMG][/CENTER]

[B]One shape, several palettes.[/B] The same Standard gate built in each of the material groups that ship. The shape file is identical; only the blocks differ, and you can write as many groups as you like.

[CENTER][IMG]https://raw.githubusercontent.com/khanjal/Wormhole-X-Treme/main/docs/images/gates/standard-palettes.png[/IMG][/CENTER]

[B]The palettes dialled[/B], and with the iris closed.

[CENTER][IMG]https://raw.githubusercontent.com/khanjal/Wormhole-X-Treme/main/docs/images/gates/standard-palettes-active.png[/IMG]
[IMG]https://raw.githubusercontent.com/khanjal/Wormhole-X-Treme/main/docs/images/gates/standard-palettes-iris.png[/IMG][/CENTER]


[SIZE=5][B]Documentation[/B][/SIZE]

[LIST]
[*][URL=https://github.com/khanjal/Wormhole-X-Treme/blob/main/docs/guide/SERVER.md]Setup, configuration, permissions and commands[/URL]
[*][URL=https://github.com/khanjal/Wormhole-X-Treme/blob/main/docs/guide/GATES.md]Gates[/URL] · [URL=https://github.com/khanjal/Wormhole-X-Treme/blob/main/docs/guide/RINGS.md]Rings[/URL] · [URL=https://github.com/khanjal/Wormhole-X-Treme/blob/main/docs/guide/BEAMS.md]Beaming[/URL] · [URL=https://github.com/khanjal/Wormhole-X-Treme/blob/main/docs/guide/MIRRORS.md]Mirrors[/URL]
[*][URL=https://github.com/khanjal/Wormhole-X-Treme/blob/main/docs/API.md]Writing a plugin against this one[/URL]
[*][URL=https://github.com/khanjal/Wormhole-X-Treme/blob/main/CHANGELOG.md]Changelog[/URL] · [URL=https://github.com/khanjal/Wormhole-X-Treme/issues]Issues and bug reports[/URL]
[/LIST]


[SIZE=5][B]Credits[/B][/SIZE]

Wormhole X-Treme was written by [B]Lologarithm[/B] (Ben Echols) and [B]alron[/B] (Dean Bailey), with contributions from [B]lirelent[/B] (Ryan Metzger) and [B]Jeremy Wood[/B]. [B]lycano[/B] kept it alive after the original went quiet, through the WolfNetDevelopment fork until 2015.

This fork descends from the original rather than from that one, and brings the plugin to modern Minecraft with rings, beaming and mirrors added.


[SIZE=4][B]Licence[/B][/SIZE]

Free software under [URL=https://github.com/khanjal/Wormhole-X-Treme/blob/main/LICENSE][B]GPL-3.0[/B][/URL]. The source is on [URL=https://github.com/khanjal/Wormhole-X-Treme]GitHub[/URL] and pull requests are welcome.

The [B]name and logo[/B] are excluded from that licence and covered by [URL=https://github.com/khanjal/Wormhole-X-Treme/blob/main/TRADEMARK.md]TRADEMARK.md[/URL] instead — more than one project carries this name, and the mark is what tells them apart. Every right the GPL grants over the code is untouched: fork it, modify it, redistribute the jar.

[SIZE=4][B]Not affiliated with the Stargate franchise[/B][/SIZE]

Wormhole X-Treme is an unofficial, fan-made Minecraft plugin. It is not affiliated with, endorsed by, sponsored by or associated with MGM Studios or any rights holder in the Stargate franchise. STARGATE and all related marks are the property of their respective owners, and the word is used here only to describe the kind of gate the plugin builds.

The plugin ships no franchise material of any kind: every sound it plays is a stock Minecraft sound, and every image is either a Minecraft screenshot or hand-drawn for this project.

[SIZE=4][B]On how this is built[/B][/SIZE]

Wormhole X-Treme is a fifteen-year-old plugin brought forward, not a new one generated. The original was written in 2011 by Lologarithm and alron; this fork modernises it and adds rings, beaming and mirrors.

Development is AI-assisted — much of the modernisation work was done with Claude Code under review, and the commit history records it. What that assistance does not do is decide what ships: every change goes through the test suite, the full build matrix and the static analysis described above before it is merged, and a maintainer reads it. The placeholder logo was drawn the same way, which [URL=https://github.com/khanjal/Wormhole-X-Treme/blob/main/TRADEMARK.md]TRADEMARK.md[/URL] says in as many words.

The code is all there under GPL-3.0. Read it, fork it, or tell me where it is wrong.
```

## BBCode notes

- **`[HR][/HR]` renders as literal text on this install.** All rules were removed; the
  `[SIZE=5][B]` headings and the blank lines between sections carry the structure. The
  self-closing `[HR]` is the form XenForo actually defines, so try that if you want rules back —
  but check it in the preview first.
- **`[ICODE]` is XenForo's inline code tag** and is what Spigot uses. `[B]` reads fine as a
  substitute if an editor refuses it.
- **The banner is an SVG and probably will not render.** `raw.githubusercontent.com` serves it as
  `image/svg+xml` correctly, but XenForo generally refuses SVG in `[IMG]`. Upload a PNG render to
  the resource gallery and use that URL, or drop the banner line and let the title carry it.
- **The captures are animated WebP**, 124 KB to 792 KB, served with the right content type. If
  they fail while the static PNGs work, it is Spigot's image proxy refusing animated WebP, and the
  fix is uploading them to the resource gallery.
- **The gallery is about 1.9 MB of PNG.** If the page feels heavy, cut `gate-shapes-active.png`
  and `standard-palettes-active.png` — the idle versions make the point.
- **The description is long.** That suits a resource page, where people scroll to the section for
  the thing they came for. If it needs shortening, the operator list is the cut: the guide links
  cover it.

## Rules this listing touches

**Checked: SpigotMC has no AI rule, so the logo stays.** Modrinth's Content Rules 6.2.1 forbids
any page image "created or derived from generative AI output", which rules the AI-authored logo
off that page entirely — see
[`modrinth.md`](modrinth.md#rule-6-no-ai-generated-images-on-the-page). SpigotMC's Terms & Rules
contain no AI clause of any kind: nothing about generative AI, generated content or generated
images, and nothing requiring disclosure of how a resource was produced. So the logo is fine as
the resource icon and in the description here, and this page deliberately differs from Modrinth's.

The rules that *do* bear on this listing are the four already recorded below: the direct-download
link, the advertising rule that keeps Claude Code unlinked, "posting someone else's plugin or
resource is not allowed", and the brand-name rule.

Two caveats on that finding. It was checked against the Terms & Rules text as Justin pasted it on
2026-09-19, footed "Last Modified: Jul 19, 2025", and not re-fetched since. And that page links a
separate **Terms of Service** at `spigotmc.org/wiki/spigot-terms/` which nobody has read, so this
covers the rules page only.

Read against Spigot's rules text as of 2025-07-19. Four things matter; one is a real risk.

- **"Posting someone else's plugin or resource is not allowed."** This is the rule a report would
  cite, and it states no fork exception. What defends the listing is that the rule's wording
  targets *minor* edits — configuration changes, code lifted from tutorials — and nothing here is
  minor: three transport systems the original never had, a version range it never saw, and a test
  suite and CI matrix standing behind both. The lineage line in the first screenful does the work;
  the Credits section at the bottom is not enough on its own. If it is ever raised, the answer is the commit history and the
  three new subsystems, not an argument about the GPL. There is precedent: another fork of this
  same plugin is already live on Spigot, crediting the original authors and linking its source.
- **Brand names.** "Stargate-style travel" is structurally the "AAAA like BBBB" pattern the rule
  describes, but the rule's examples are all servers and networks trading on each other's
  reputation, the word here is descriptive, and Spigot already hosts a plugin called *Stargate*.
  The non-affiliation notice is what settles it — the second half of that rule is about feigning
  endorsement, and explicitly disclaiming it is the opposite.
- **Download links must be direct.** `releases/latest` is a redirect to a page, not a file, so it
  does not qualify. Hence uploading the jar.
- **Advertising** must relate to running a Spigot server, with a business arrangement behind it.
  That is why Claude Code is named as plain text and never linked.

**The AI disclosure stays anyway.** With no AI rule (see the top of this section), it is there on
reputation grounds, not compliance:
`TRADEMARK.md` already says the logo was drawn with Claude Code, the changelog mentions it, and
the commits carry `Co-Authored-By` trailers, so anyone clicking through finds it in a minute.
Concealment is the bigger risk.

**Do not get drawn into explaining trademark law in the discussion thread.** The rules forbid
giving or requesting legal advice. Stating your own licence and your own non-affiliation is a
statement about your own resource, which is fine.

## Version upload

Spigot calls this posting a resource update. It takes a title, a message and the jar, and the
message is the one piece of release copy this file does not otherwise carry.

| Field | Value |
|---|---|
| Update title | `Wormhole X-Treme v1.8.0 (MC 1.20-26.3)` — matches the GitHub release name |
| Update message | the short form in [`shared.md`](shared.md#release-notes), converted to BBCode |
| File | `WormholeXTreme-<version>.jar` from the release |
| Version | set it to match, so the resource header stops advertising the old one |

**The message needs converting; Modrinth's and Hangar's do not.** Those two take the release
notes as Markdown and paste in unchanged. Spigot takes BBCode, so the same block needs
`**bold**` as `[B]bold[/B]`, `` `code` `` as `[ICODE]code[/ICODE]`, the `>` blockquote dropped and
the bullet list wrapped in `[LIST]` with `[*]` per item. The [BBCode notes](#bbcode-notes) above
apply here too — in particular, do not reach for `[HR]`.

The converted block is kept below so the upload is a paste rather than a conversion. It is the
one place the release notes are duplicated, so **rewriting
[`shared.md`](shared.md#release-notes) means rewriting this too.** What follows is 1.8.0's.

```
[B]Upgrading from 1.7 - nothing you have to do.[/B]
[LIST]
[*][B]Anonymous usage counts now go to bStats[/B], on by default: Minecraft version, server software, and how many gates, rings, beams and mirrors, in ranges. [ICODE]/wormhole config metrics-enabled false[/ICODE] stops it.
[*]The default [ICODE]top[/ICODE] dial pauses on each chevron, so a dial takes about three seconds longer. [ICODE]/wormhole config gate-dial-spin chevron[/ICODE] keeps the old pace.
[*]Optional: add [ICODE]dial-spin: pegasus[/ICODE] and [ICODE]dial-spin: universe[/ICODE] to an existing [ICODE]config.yml[/ICODE]'s Atlantis and Universe groups, as new installs have.
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

[URL=https://github.com/khanjal/Wormhole-X-Treme/blob/main/CHANGELOG.md]Full changelog[/URL]
```

**The dash in the first line is a plain hyphen**, where `shared.md` has an em-dash. Not verified
as necessary — the description above uses real em-dashes and they render fine — just one less
thing to go wrong in a field that is typed rather than pasted from a file.

Tick **Notify followers** so people watching the resource hear about it. That notification is
most of what uploading to Spigot buys over linking a jar, which is the reason the resource type
is an upload in the first place — see [Fields](#fields).

## Keeping it current

Published 2026-09-19 at <https://www.spigotmc.org/resources/wormhole-x-treme.138936/>. The jar blocker is gone: `v1.7.0` and `v1.7.1` are both released,
so there is a jar to upload. What is left:

1. **Check which version the resource carries.** `v1.8.0` is the newest release. Upload the newer
   jar and update the Version field if it has not been done.
2. **`plugin.yml`'s description is still the 2011 text** — "Splash Effect, IDC, Iris, configurable
   Wormhole materials, and much much more." It is what shows in `/plugins` and in server panels,
   and it names none of rings, beaming or mirrors. Not a blocker for the form, but the first thing
   anyone who installs from Spigot reads.
3. **`pom.xml:35` still carries `<url>http://www.wormhole-xtreme.com</url>`** from the original
   project. It did not resolve when checked. Worth cleaning out.
