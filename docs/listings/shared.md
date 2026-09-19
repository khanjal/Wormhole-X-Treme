# Shared listing copy

The source of truth for every plugin-site listing. Plain Markdown, no site markup — each site
file takes what it needs and renders it in BBCode or its own Markdown flavour.

Everything here is drawn from the repository at `main`. Nothing is quoted from memory.

---

## Release facts

The values a release changes. Change them here first, then carry them into the site files.

| | |
|---|---|
| Version | `1.7.0` |
| Supported Minecraft | 1.20 – 26.3 |
| Native / compiled against | 1.20 (`pom.xml` sets `spigot-api` to `1.20.4-R0.1-SNAPSHOT`) |
| Java, plugin | 17 |
| Java, server | 21 from MC 1.20.5, 25 from MC 26.1 — the server's requirement, not this plugin's |
| Licence | GPL-3.0 (the name and logo excluded, see [`TRADEMARK.md`](../../TRADEMARK.md)) |
| Dependencies | none. Vault and LuckPerms optional, snakeyaml comes from the server, nothing shaded |
| Jar | `WormholeXTreme-<version>.jar` |

## Numbers the copy does not print

**No count that a release can change goes in listing copy.** Settings, test classes, CI legs,
mirror looks, gate shapes, material groups, open Sonar findings: each of those was accurate the
day it was written and wrong a release later, and a figure a reader can contradict from the badge
or the repo is worse than no figure at all. Say what the thing is instead — "any setting", "a look
for every biome", "the whole matrix" — and let the live badges carry anything numeric.

The numbers that do appear are the ones that describe how the plugin behaves rather than how much
of it there is: the slab counts a ring pattern needs, how long a chevron holds, the three-second
mirror round trip. Those are part of the description and do not drift on their own.

The exceptions are in [Release facts](#release-facts) above: the version, the supported range and
the Java versions. Those are required fields, and a release has to revisit them anyway.

## Links

| | |
|---|---|
| Source | `https://github.com/khanjal/Wormhole-X-Treme` |
| Issues and support | `https://github.com/khanjal/Wormhole-X-Treme/issues` |
| Guide | `https://github.com/khanjal/Wormhole-X-Treme/tree/main/docs/guide` |
| Licence | `https://github.com/khanjal/Wormhole-X-Treme/blob/main/LICENSE` |
| Trademark | `https://github.com/khanjal/Wormhole-X-Treme/blob/main/TRADEMARK.md` |
| Changelog | `https://github.com/khanjal/Wormhole-X-Treme/blob/main/CHANGELOG.md` |
| Donations | none in the repo. Leave the field blank. |

**Not the Discord link.** `SECURITY.md` and the issue chooser point at
`discord.com/users/235747179459772416`, which is a direct message to Justin personally. A public
listing is a different order of exposure, and a DM does not answer the next person with the same
problem. Support goes to the issue tracker.

## Images

All pinned to `main`. Prefix: `https://raw.githubusercontent.com/khanjal/Wormhole-X-Treme/main/`

| Use | Path | Type |
|---|---|---|
| Banner | `docs/images/logo-banner.svg` | SVG — **renders on Modrinth and Hangar, not on Spigot** |
| Icon | rendered 256×256 PNG from `docs/images/logo.svg` | not in the repo; rendered per site |
| Gates | `docs/images/gates/gate-dial.webp` | animated WebP |
| Rings | `docs/images/rings/ring-cycle.webp` | animated WebP |
| Beaming | `docs/images/beams/beam-up.webp` | animated WebP |
| Mirrors | `docs/images/mirrors/mirror-effects.webp` | animated WebP |
| Gallery | `docs/images/gates/gate-shapes.png`, `gate-shapes-active.png`, `gate-horizontal.png`, `standard-palettes.png`, `standard-palettes-active.png`, `standard-palettes-iris.png` | PNG |

The gallery is about 1.9 MB all told. If a page feels heavy, `gate-shapes-active.png` and
`standard-palettes-active.png` are the two cuts that lose least — the idle versions make the point.

Everything else under `docs/images/` is SVG: the per-shape drawings, the ring patterns, the ring
stack, the beam timing strip. Render to PNG before using them anywhere that will not take SVG.

**Rendering the banner clips the wordmark unless you substitute the font.** `logo-banner.svg`
draws its text as live `<text>` in `Segoe UI, Helvetica Neue, Helvetica, Arial, sans-serif`
(`docs/images/logo-banner.svg:106`). Render it anywhere without Segoe UI installed and the
fallback sets wider, pushing "WORMHOLE" and the tagline off the right edge of the 1040x340
viewBox. Substitute a metric-compatible face first:

```bash
sed 's/font-family="Segoe UI, Helvetica Neue, Helvetica, Arial, sans-serif"/font-family="Liberation Sans"/' \
  docs/images/logo-banner.svg > /tmp/banner.svg
python3 -c "import cairosvg; cairosvg.svg2png(url='/tmp/banner.svg', write_to='banner.png', output_width=1240)"
```

Liberation Sans is metric-compatible with Arial, which is already in the stack, so nothing moves.

---

## Tagline

Written to Spigot's 100-character cap, which is the tightest of the three. Measured.

**Preferred** (97):

> Stargate-style travel: dialling gates, transport rings, beaming and quantum mirrors. MC 1.20-26.3

Alternates, all measured and all under 100:

> Stargates, transport rings, beaming and quantum mirrors. Minecraft 1.20-26.3, no dependencies. (94)

> Four ways to travel: Stargates, transport rings, beaming and quantum mirrors. MC 1.20-26.3 (90)

> Dialling Stargates, transport rings, beaming and quantum mirrors. Minecraft 1.20-26.3. (86)

All four keep the two things that have to survive a cut: the four transport systems, which is what
distinguishes this listing, and the version range, which is the first thing an operator checks.

## Header

> **Stargate-style travel for Bukkit, Spigot and Paper**
> Minecraft 1.20 through 26.3 · Java 17 · no dependencies

## Lineage line

Goes in the first screenful, not the last. A reader should have the whole story before the feature
list, and on Spigot it is also what answers the "posting someone else's plugin" rule.

> **A maintained fork of the original 2011 plugin** by Lologarithm and alron, GPL-3.0, with full
> credits at the bottom and the source at github.com/khanjal/Wormhole-X-Treme. It brings the plugin
> to modern Minecraft and adds transport rings, beaming and quantum mirrors.

## The four systems, in one line each

> Four ways to get somewhere, each a different trade between what you build and what you get.
>
> - **Stargates** — a frame of blocks. Press the button, then `/dial`; or fit a dial sign and dial
>   it with a button or redstone. Reaches any gate, across worlds.
> - **Transport rings** — a circle of slabs, in pairs. Walk in. Same world.
> - **Beaming** — build nothing at all. `/wormhole beam to <name>` from anywhere.
> - **Quantum mirrors** — one banner on a wall. Right-click to choose, punch to go. Across worlds.

---

## Feature blocks

### Stargates

- **Dialling the way the show does it.** Chevrons light in order — down the right side, up the
  left, the top one last — at half a second each on a Standard gate and a little slower on bigger
  ones. The last one holds a second and locks in with its own sound. Then the kawoosh.
- **Dial-spin patterns.** The dialling gate's inner ring turns before each chevron locks:
  `top` sweeps half the ring and reverses each glyph, `chevron` lands on the chevron itself, `lap`
  takes a whole turn clockwise, `fill` lights the ring behind it as it goes, `pegasus` dials as an
  Atlantis gate does, and `none` turns it off. No pattern changes how fast a gate dials.
- **An eighth chevron** locks when the destination is in another world, after the top one.
- **The gate shapes that ship** — Standard, Large, Grand, Massive, Minimal and Horizontal, the
  last lying flat to be dropped into rather than walked through. Shapes are plain text files:
  copy one, edit the grid, and `/wormhole gate shapes reload` tries it without a restart. Shipped
  files are written out on first run and never overwrite yours.
- **Material groups.** A shape is geometry; a group is what it is built from — frame, portal,
  iris, chevron, light and sign block. Build `Standard` in obsidian or in lapis and get a
  different-looking gate from one shape file. Several groups ship, you can write as many as you
  like, and a gate framed in a material no group declares gets one added for it automatically.
  Per-gate overrides beat the shape, which beats the group.
- **A building assistant.** `/wormhole gate build <shape>` stands the shape full size in front of
  you, seen by you alone and made of no blocks, so you can build straight into it. Then:
  `-materials` lists what it will cost you block by block; `-guide` draws what is still to place,
  outlines a wrong block in red and makes a correct one disappear; `-layer` steps through a deep
  gate a layer at a time; `-activate` test-dials it; `-iris`, `-chevrons`, `-dhd` and `-material`
  redress it; `-share` shows it to another player or the whole world; and `-place` builds it for
  real.
- **An iris, with remote codes.** A closed iris bounces anyone dialling in. Give a gate an IDC and
  callers can open it from the other end.
- **Sign dialling and redstone.** A dial sign steps through destinations on right-click, with the
  selection coloured and wrapped in `» «` so it reads for a colourblind player. Wire redstone to
  the marked cell and a pulse dials whatever the sign shows; a second marked cell drives a lever
  while the gate is open, for doors and lamps.
- **Networks, owners and per-gate settings** — shutdown and activate timeouts, cooldown, redstone
  on or off, iris code, owner, and per-gate material overrides.
- **Gates work in the Nether and the End**, and a wormhole runs one way: nothing comes back up an
  open gate.

### Transport rings

- **Built from slabs, in two patterns.** Lay a circle — 16 slabs across 7, or 12 across 6 — stand
  inside it and run `/wormhole ring create`. Do it again elsewhere in the same world and the two
  are paired. A refusal says exactly what is wrong, and `ring cancel` gives your slabs back.
- **The material is whatever you laid.** Deepslate slabs rise as deepslate. Every end can then be
  re-dressed on its own: `ring` (any slab, including one a data pack adds), `light` for the pad
  while it works, `flash` for the light running through the stack, and `built` for what `reset`
  puts back.
- **Two deployment styles, per end.** `CONCURRENT` sends several rings up together; `SEQUENTIAL`
  raises them one at a time. Set the default server-wide, or `ring edit style fast|slow` one end
  at a time.
- **Floor rings and ceiling rings.** Bottom slabs on a floor make a ring that rises out of it; top
  slabs under a ceiling make one whose rings fall to the floor around you.
- **A countdown you can walk out of.** The floor opens and counts down; step clear before it
  commits and it stands down. A pair then rests before it fires again.
- **Everything in the ring travels** — players, mobs, items, vehicles. Ride in on a horse and you
  arrive still on it. Only players are checked for access.
- **It refuses an unfit arrival**: every square clear, solid ground under it, water and lava not
  counting. A refused trip costs nothing, and the pattern is flashed to whoever was turned away.
- **Owners, access and quotas.** Pairs start public or private, `ring allow` and `ring deny`
  manage a private pair's list, `ring owner` hands it over, and a per-player quota caps how many
  anyone may own.
- **Every timing is a setting** — countdown, cooldown, animation speed, how long the stack
  settles, how long each ring stays lit, how long the pad glows afterwards — as are the maximum
  distance and height between two ends.
- **Name an end** and its partner tells travellers where they are heading.

### Beaming

- **Nothing to build.** A destination is a named point somebody stood on once. A column of light
  takes you there from anywhere.
- **Public destinations and private places.** Staff curate a public list; every player keeps their
  own places. A name is looked up in your own first, then the public list.
- **Crosses worlds freely**, and a destination in an unloaded world says so rather than loading it.
- **Charge for it, per destination.** Set a default cost, then override it on any public
  destination — through Vault, and only if you want to.
- **Admin travel.** Beam yourself or another player straight to a player, a destination, or raw
  coordinates in any world — from console or a command block too.
- **The whole sequence is tick-by-tick configurable**: how long the glow gathers, how far into it
  you vanish, how long the column rises, when the teleport lands, how long it descends and fades.
  The two "at step" values are clamped inside their phase, so a traveller can never be left frozen
  and invisible.
- **An optional per-player cooldown.**
- **You arrive facing the way the destination was saved**, and on the nearest safe spot if the
  ground has since changed.

### Quantum mirrors

- **One banner on a wall** and `/wormhole mirror create <name>`. That is the whole job. Hang two
  banners side by side and the pair is one mirror, two wide and two tall.
- **It opens onto the room beyond.** Walk up and the banner gives way to an opening its own size
  showing another world's room in real blocks — so the view has depth and shifts as you move past
  it. Nothing in the world changes; only the players looking in are sent the view.
- **Right-click to choose, punch to travel.** Right-click steps through the other mirrors by name;
  punch it and you land in front of that mirror's banner, facing out into its room. A whole round
  trip takes three seconds and no commands.
- **A look for every biome in the game**, plus `hub`, `exit`, `market`,
  `warning`, `private`, `shrine`, `vault` and more. They are plain text files: edit one and it
  stays edited, delete one and it comes back, add your own and the plugin offers it.
- **Stamp a look from the room itself.** `mirror set <name> -stamp` reads the room and paints the
  banner from it: the biome picks the frame — rising flame for the Nether, white crests over blue
  for an ocean — and the blocks around it become coarse squares in their dominant colours.
  Indoors, the room's own blocks decide, so a library comes back the brown of its shelves.
- **The view is a capture**, taken once and kept on disk, so a mirror onto an archived world still
  shows it without loading that world.
- **Tunable depth.** How far the room is drawn is a setting (about as far as a server
  sends, by default). A deep redraw rests before the next, so a mirror can never take more than a
  quarter of the server's time however close you stand.
- **It says what it is.** Look at one from a few blocks and it tells you above the hotbar what a
  click will do. `mirror debug` lists every fact about a mirror in green and red — a gap in the
  wall, another mirror too near, a missing capture.
- **Paper bonus:** optional fog pulled in to where the room ends, so the far edge is fog rather
  than this world's hills.

### For the people running the server

- **Everything travels** — minecarts and boats with their passengers, ridden horses, camels, pigs,
  donkeys, llamas and striders with their riders, arrows and tridents and ender pearls in
  mid-flight, and mobs, items and XP orbs that wander into an open gate. Tamed wolves, cats and
  parrots follow their owner through any of the four.
- **Configured in game.** `/wormhole config <setting> <value>` changes any setting on
  the spot — no reload, no restart. `/wormhole config sign` searches them.
- **Every sound is a setting**, resource pack sounds included, with a volume per subsystem and
  `none` to silence any one of them. A gate even sounds its size: deeper and louder on a big gate,
  lighter on a small one.
- **Works with or without a permissions plugin.** Vault and LuckPerms if you have them, a built-in
  fallback if you do not.
- **Plain YAML storage**, one file per gate. No database.
- **Events for other plugins** to watch or cancel travel.
- **Importer** for gates from older Wormhole X-Treme forks' SQLite databases.

## Getting started

> Drop the jar in `plugins/` and start the server. Nothing else is needed.

**The quickstart builds `Standard`, not `StandardSignDial`, on purpose.**
`docs/guide/GATES.md` says to expect the SignDial shapes to go, with
[#46](https://github.com/khanjal/Wormhole-X-Treme/issues/46) moving sign dialling to the DHD. A
shape slated for removal does not belong in a listing that outlives it.

```
/wormhole gate build Standard           # preview it, then lay the frame in obsidian
/wormhole gate complete Home            # click the DHD button first
/wormhole ring create                   # standing in a circle of slabs
/wormhole beam place set home           # where you stand
/wormhole mirror create home            # looking at a wall banner
```

## Compatibility

- **Minecraft 1.20 – 26.3.** CI builds and runs the test suite across that
  range, at every boundary where the API moved.
- **Spigot** is the primary target — the API this is compiled against. **Paper** is supported and
  built against at every version. **CraftBukkit** works, but has no action bar, so ring countdowns
  and mirror names do not appear above the hotbar. **Purpur** and **Pufferfish** are best effort.
  **Folia is not supported.**
- **Java 17** or later for the plugin itself. Minecraft 1.20.5+ needs the server on Java 21, and
  26.1+ on Java 25 — that is the server's requirement, not this plugin's.

> What CI proves is that the plugin compiles and its tests pass against each version. It is not a
> claim that every gate has been played on every one of them. Bug reports are welcome and get
> answered.

Keep that last paragraph. `docs/guide/SERVER.md` says plainly that none of this has been
runtime-verified on a live server, and the sentence costs nothing while protecting the listing the
first time someone's 26.3 server does something unexpected.

## Under the hood

Badges: SonarCloud `coverage`, `sqale_rating`, `reliability_rating`, `security_rating` for
`khanjal_Wormhole-X-Treme`, at
`https://sonarcloud.io/api/project_badges/measure?project=khanjal_Wormhole-X-Treme&metric=<metric>`

- **Tested.** A test suite covering gate detection, dial sequencing, ring geometry, beam
  timing, mirror captures, config parsing and the command layer. Coverage is on the badge above
  and is measured on every push, not quoted from memory.
- **Every push builds and tests the whole matrix.** Java 17 and Java 25; every supported
  Minecraft version on the Spigot API; Paper at every one of them; and Purpur's newest. A
  Minecraft version is only claimed as supported if it is in that matrix.
- **Compiled against the oldest supported API on purpose.** A plugin built against an old API runs
  on newer servers; one built against a new API can call something an old server has never heard
  of, and nothing catches that until a player reports a crash. Building against the floor makes
  the compiler enforce the floor — and the newest-version legs of the matrix catch the opposite
  case, an API that has been removed.
- **Static analysis on every pull request.** SpotBugs runs on each build, and SonarCloud fails a
  pull request that carries *any* open finding, not merely a coverage gate. A 2026-09 refactoring
  campaign cleared the open backlog and closed every "method too complex" finding on the way.
- **Nothing third-party in the jar.** Every dependency is provided or test scope; there is no
  shading, no bundled library, and no database. Gates are one YAML file each.
- **GPL-3.0, and the issue tracker is open.** Bug reports get answered and pull requests are
  welcome.

## Documentation links

- Setup, configuration, permissions and commands — `docs/guide/SERVER.md`
- Gates · Rings · Beaming · Mirrors — `docs/guide/GATES.md`, `RINGS.md`, `BEAMS.md`, `MIRRORS.md`
- Writing a plugin against this one — `docs/API.md`
- Changelog · Issues and bug reports

## Release notes

Modrinth and Hangar both take a per-version changelog, and the full 1.7.0 section of
[`CHANGELOG.md`](../../CHANGELOG.md) runs to about 150 lines — a wall on a download page. Use the
short form below there and link the full one.

**Keep both Upgrading bullets whatever else is cut**, especially the shape-files one: an upgrader
who keeps their old shape files sees none of the headline dialling changes and reads the release
as broken.

> **Upgrading from 1.6.0 — two things to do.**
>
> - Command keywords now need a dash: `gate remove <gate> -destroy` (was `-all`), `complete
>   -help`, `mirror set <name> -stamp`, `beam admin cost <name> -default`. Scripts and command
>   blocks need updating.
> - Your shape files are kept, not overwritten — so the new dialling will not appear until you
>   delete the bundled shapes you have not edited and restart. The startup log names them.
>
> **Stargates**
>
> - Chevrons light in the show's order, at a pace you can follow, with a lock-in sound.
> - An eighth chevron locks when the destination is in another world.
> - The inner ring turns as it dials, in a choice of patterns, or not at all.
> - Build previews: stand a shape full size in front of you, check what it costs, get a build
>   guide, then place it for real.
> - `gate regen` finds a gate's real shape and relights its chevrons.
>
> **Travel**
>
> - Tamed wolves, cats and parrots follow their owner through gates, rings, beams and mirrors.
>
> **Server**
>
> - Supported through Minecraft 26.3. CraftBukkit no longer errors on ring countdowns.
>
> [Full changelog](https://github.com/khanjal/Wormhole-X-Treme/blob/main/CHANGELOG.md)

## Credits

> Wormhole X-Treme was written by **Lologarithm** (Ben Echols) and **alron** (Dean Bailey), with
> contributions from **lirelent** (Ryan Metzger) and **Jeremy Wood**. **lycano** kept it alive
> after the original went quiet, through the WolfNetDevelopment fork until 2015.
>
> This fork descends from the original rather than from that one, and brings the plugin to modern
> Minecraft with rings, beaming and mirrors added.

**The Contributors metadata field is a different list:** `Lologarithm, alron, lirelent, Jeremy
Wood, Khan Jal`. That is the original plugin's own `plugin.yml` authors line with Justin appended,
and it is the authors' own statement of who wrote the code. lycano is not on it — this fork
descends from the original at 0.854 (May 2011), not from the WolfNet fork, which the package root
`com.wormhole_xtreme.wormhole`, the absence of any lycano reference in `src/`, and the changelog
jumping from 0.x (2011) straight to 1.0.0 (2026) all confirm. History goes in prose, authorship
goes in metadata.

**Do not revise that list from the GitHub contributor graph.** The upstream graph shows alron,
lirelent and dumptruckman but *not* Lologarithm, who is beyond dispute a primary author — commits
authored under an email never linked to a GitHub account do not attach to a user, so the graph
under-reports and absence from it proves nothing. In particular it is not grounds to drop Jeremy
Wood, whose credit rests on the original authors having listed him in their own `plugin.yml`,
which is better evidence than a graph known to be incomplete. lirelent is separately documented at
`CHANGELOG-ORIGINAL-2011.md:192` for distance finding and gate shape parsing.

## Licence

> Free software under **GPL-3.0**. The source is on GitHub and pull requests are welcome.
>
> The **name and logo** are excluded from that licence and covered by `TRADEMARK.md` instead — more
> than one project carries this name, and the mark is what tells them apart. Every right the GPL
> grants over the code is untouched: fork it, modify it, redistribute the jar.

## Non-affiliation notice

Ships on every listing.

> **Not affiliated with the Stargate franchise**
>
> Wormhole X-Treme is an unofficial, fan-made Minecraft plugin. It is not affiliated with, endorsed
> by, sponsored by or associated with MGM Studios or any rights holder in the Stargate franchise.
> STARGATE and all related marks are the property of their respective owners, and the word is used
> here only to describe the kind of gate the plugin builds.
>
> The plugin ships no franchise material of any kind: every sound it plays is a stock Minecraft
> sound, and every image is either a Minecraft screenshot or hand-drawn for this project.

## AI disclosure

Goes last, after the non-affiliation notice, and only with **Under the hood** above it — the
disclosure reads completely differently sitting under a full CI matrix than it does
standing alone. Claude Code is named as **plain text, never a link**.

> **On how this is built**
>
> Wormhole X-Treme is a fifteen-year-old plugin brought forward, not a new one generated. The
> original was written in 2011 by Lologarithm and alron; this fork modernises it and adds rings,
> beaming and mirrors.
>
> Development is AI-assisted — much of the modernisation work was done with Claude Code under
> review, and the commit history records it. What that assistance does not do is decide what
> ships: every change goes through the test suite, the full build matrix and the static
> analysis described above before it is merged, and a maintainer reads it. The placeholder logo
> was drawn the same way, which `TRADEMARK.md` says in as many words.
>
> The code is all there under GPL-3.0. Read it, fork it, or tell me where it is wrong.

Short version, if a site's page is tight:

> **On how this is built**
>
> Development is AI-assisted, with Claude Code doing much of the modernisation work under review;
> the commit history records it. Nothing merges without the test suite, the full build
> matrix and the static analysis above. The code is all there under GPL-3.0.
