# Hangar

Copy for a Hangar project at <https://hangar.papermc.io/>. Facts and prose come from
[`shared.md`](shared.md); what is here is Hangar's own field set and markup.

Rows marked **(confirmed)** were read off the real form while the project was being filled in on
2026-09-19. Rows still marked **(assumed)** come from how Hangar normally works rather than from
looking; check those against the page and correct them here.

Hangar takes **Markdown**, like Modrinth, so the page below is close to `shared.md` as written.

**The one real difference from the other two sites:** Hangar is PaperMC's own platform and its
platform tags are **Paper, Velocity and Waterfall** — there is no Spigot or Bukkit tag
**(assumed)**. So this plugin lists there as a Paper plugin, which is honest (CI builds and tests
against Paper at every supported version) but inverts how Spigot and Modrinth describe it. The page
below leads with Paper and mentions Spigot second, which is the only place the copy deliberately
diverges from the other two.

---

## Fields

| Field | Value |
|---|---|
| Project name | `Wormhole X-Treme` |
| URL / namespace | `khanjal/Wormhole-X-Treme` **(assumed: it takes the owner's name)** |
| Tagline | see [Tagline](#tagline) below |
| Category **(assumed)** | Gameplay |
| Keywords **(confirmed)** | `stargate`, `teleport`, `portal`, `warp`, `rings` |
| Tags **(confirmed)** | Addon **off**, Library **off**, Supports Folia **off** |
| Licence | `GPL-3.0-or-later` — see the note in [`modrinth.md`](modrinth.md#fields) |
| Platforms **(assumed)** | Paper only. Not Velocity, not Waterfall — this is a server plugin, not a proxy plugin. |
| Platform versions **(confirmed)** | tick every 1.20.x, 1.21.x and 26.x individually. Snapshots off. |
| Issues **(confirmed)** | `https://github.com/khanjal/Wormhole-X-Treme/issues` |
| Source **(confirmed)** | `https://github.com/khanjal/Wormhole-X-Treme` |
| Support **(confirmed)** | `https://github.com/khanjal/Wormhole-X-Treme/issues` |
| Wiki **(confirmed)** | `https://github.com/khanjal/Wormhole-X-Treme/tree/main/docs/guide` |
| Discord **(confirmed)** | blank |
| Donations **(confirmed)** | blank |
| Avatar **(assumed)** | PNG rendered from `docs/images/logo.svg` |

**On the category.** Hangar's list is shorter than Modrinth's and has no Transportation
**(assumed)** — Gameplay is the closest honest fit, with Misc the fallback if Gameplay is taken to
mean something narrower. Check the list on the form and correct this row.

**On Velocity and Waterfall.** Leave both off. Ticking a proxy platform for a server plugin puts
it in front of people who cannot use it, and Hangar's platform filter is how most people browse.

**On the keywords.** These deliberately differ from the Spigot tags, which are `stargate`,
`teleport`, `portal`, `transportation`, `wormhole`. `transportation` is a Hangar *category* rather
than a useful keyword there, and `wormhole` is already in the project name, so the two slots go to
`warp` and `rings` instead — `rings` being the one subsystem nobody would find under the other
four.

**On the platform versions.** Hangar wants each one ticked individually rather than a range, so
tick every 1.20.x, 1.21.x and 26.x. Leave snapshots off.

## Tagline

Hangar's tagline cap is **120 characters, confirmed on the form**, between Spigot's 100 and
Modrinth's 256. The preferred tagline from [`shared.md`](shared.md#tagline) fits as-is at 97, and
there is room for a slightly fuller one:

```
Stargate-style travel: dialling gates, transport rings, beaming and quantum mirrors. MC 1.20-26.3, no dependencies.
```

(115 characters, so it fits the 120 cap with five to spare.)

## Home page

Hangar projects have a main page and can have **subpages** **(assumed)**. That suits this plugin
better than one long scroll, so the split below puts the four transport systems on their own pages
and keeps the main page to what someone deciding whether to download needs.

If subpages turn out not to be available, paste the four system sections from
[`modrinth.md`](modrinth.md#description) inline after the overview and the result is the Modrinth
page.

| Page | Contents |
|---|---|
| Main | Everything below. |
| Stargates | The Stargates block from [`shared.md`](shared.md#stargates), with `gate-dial.webp`. |
| Transport rings | The rings block, with `ring-cycle.webp`. |
| Beaming | The beaming block, with `beam-up.webp`. |
| Quantum mirrors | The mirrors block, with `mirror-effects.webp`. |

Paste the main page from here down.

````markdown
![Wormhole X-Treme](https://raw.githubusercontent.com/khanjal/Wormhole-X-Treme/main/docs/images/logo-banner.svg)

## Stargate-style travel for Paper, Spigot and Bukkit

**Minecraft 1.20 through 26.3 · Java 17 · no dependencies**

**A maintained fork of the original 2011 plugin** by Lologarithm and alron,
[GPL-3.0](https://github.com/khanjal/Wormhole-X-Treme/blob/main/LICENSE), with full credits at the
bottom and the source at
[github.com/khanjal/Wormhole-X-Treme](https://github.com/khanjal/Wormhole-X-Treme). It brings the
plugin to modern Minecraft and adds transport rings, beaming and quantum mirrors.

Four ways to get somewhere, each a different trade between what you build and what you get.

| | What you build | Reaches |
|---|---|---|
| **Stargates** | a frame of blocks and a button | any gate, across worlds |
| **Transport rings** | a circle of slabs, in pairs | its pair, same world |
| **Beaming** | nothing at all | any named destination, across worlds |
| **Quantum mirrors** | one banner on a wall | any other mirror, across worlds |

Each has its own page: **[Stargates](#)** · **[Transport rings](#)** · **[Beaming](#)** ·
**[Quantum mirrors](#)** — point those at the subpages once they exist.

![Dialling a gate](https://raw.githubusercontent.com/khanjal/Wormhole-X-Treme/main/docs/images/gates/gate-dial.webp)

### Stargates

A frame of blocks, a button, and `/dial`. Chevrons light in the show's order, the last one locks
in with its own sound, and then the kawoosh. Shapes ship from Minimal to Massive, plus a
Horizontal one that lies flat; a choice of dial-spin patterns; an eighth chevron for a cross-world
destination; an iris with remote codes; sign and redstone dialling. Shapes and material palettes
are plain text files you can edit, and `/wormhole gate build` stands the shape full size in front
of you as a guide before you place a block.

### Transport rings

Lay a circle of slabs, run one command, do it again elsewhere, and the two are paired. The rings
rise in whatever material you laid. A countdown you can step out of, floor and ceiling variants,
two deployment styles, and everything in the circle travels — players, mobs, items, a horse with
you still on it.

### Beaming

Nothing to build. A destination is a named point somebody stood on once, and a column of light
takes you there from anywhere, across worlds. Staff curate a public list; every player keeps their
own places. Charge per destination through Vault if you want to.

### Quantum mirrors

Hang a banner on a wall and run one command. Walk up to it and the banner gives way to an opening
showing another world's room in real blocks, with depth that shifts as you move past it.
Right-click to choose where it leads, punch it to go. A look ships for every biome, and
`-stamp` paints a banner from the room it stands in.

**On Paper:** optional fog pulled in to where the room ends, so the far edge is fog rather than
this world's hills.

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

- **Minecraft 1.20 – 26.3**, one jar. CI builds and runs the test suite across that range, at every boundary where the API moved.
- **Paper** is built and tested against at every one of those versions. **Spigot** is the API the jar is compiled against. **CraftBukkit** works, but has no action bar, so ring countdowns and mirror names do not appear above the hotbar. **Purpur** and **Pufferfish** are best effort. **Folia is not supported.**
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

The six PNGs from [`shared.md`](shared.md#images), same set and same captions as
[`modrinth.md`](modrinth.md#gallery). If Hangar has no gallery of its own **(assumed)**, append
them to the main page under a `## Gallery` heading, in that order, with the caption as the alt
text.

## Version upload

| Field | Value |
|---|---|
| Version | `1.7.0` |
| Release channel | Release |
| Platform | Paper |
| Platform versions | every 1.20.x, 1.21.x and 26.x, ticked individually. Snapshots off. |
| File | `WormholeXTreme-1.7.0.jar` |
| Changelog | the 1.7.0 section of [`CHANGELOG.md`](../../CHANGELOG.md), pasted as Markdown |

Lead the changelog with the **Upgrading** paragraph, as on Modrinth.

## Before you submit

1. **There is no 1.7.0 release yet.** The newest tag is `v1.6.0`. Tag first.
2. **Render the avatar** from `docs/images/logo.svg`. Size unverified; the 256×256 rendered for
   Spigot is a reasonable starting point.
3. **Confirm the platform question first.** If Hangar genuinely has no Spigot or Bukkit tag, the
   Paper-first framing above is right. If it does have one, revert the page's opening lines to the
   Spigot-first wording used in [`modrinth.md`](modrinth.md#description) so all three read alike.
4. **Point the four subpage links** in the main page at the subpages once they exist. They are
   `#` placeholders as written.
