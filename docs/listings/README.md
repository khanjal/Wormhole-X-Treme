# Listings

The copy that goes on the plugin sites, kept here so a release is an edit rather than a rewrite.

| File | What it is |
|---|---|
| [`shared.md`](shared.md) | The source of truth. Every fact and every block of prose the listings share, in plain Markdown with no site markup. |
| [`spigot.md`](spigot.md) | [SpigotMC](https://www.spigotmc.org/resources/wormhole-x-treme.138936/) — form fields and the description in BBCode. |
| [`modrinth.md`](modrinth.md) | [Modrinth](https://modrinth.com/plugin/wormhole-x-treme) — project fields and the description in Markdown. |
| [`hangar.md`](hangar.md) | [Hangar](https://hangar.papermc.io/khanjal/Wormhole-X-Treme) — project fields and the page in Markdown. |
| [`curseforge.md`](curseforge.md) | CurseForge — **not yet published**; URL to come. Project fields and the description in Markdown. |
| [`versions.md`](versions.md) | Every version's upload fields and short changelog, Markdown and BBCode, newest first. |

Three are published:
[SpigotMC](https://www.spigotmc.org/resources/wormhole-x-treme.138936/) ·
[Modrinth](https://modrinth.com/plugin/wormhole-x-treme) ·
[Hangar](https://hangar.papermc.io/khanjal/Wormhole-X-Treme). CurseForge is being created as of
2026-10-06; put its URL in the table above and here once moderation approves it. Editing a file
here does not change a live page; it records what the page should say, and somebody still has to
paste it.

Nothing here is generated. The site files are parallel texts, not renders of `shared.md`,
because the markup and the field sets differ enough that a generator would cost more than it
saves. What `shared.md` buys is that the *facts* live in one place: when a number or a link
changes, you change it there and then carry it into whichever site files quote it.

## Updating for a release

1. **Bump the version and the supported range** in [`shared.md`](shared.md#release-facts), then in
   each site file's fields table. These are the only numbers a release should have to touch — see
   the convention below.
2. **Upload the new jar to every site**, and set each one's version field to match. Spigot
   takes an uploaded file rather than a link, for the reasons in [`spigot.md`](spigot.md#fields);
   Modrinth and Hangar take a version upload each, and CurseForge a file upload, with the
   changelog type set to Markdown. Each site file has an upload section saying what goes in the
   fields. This is the step that actually ships the release to
   the people using it, and it is easy to stop after tagging.
3. **Re-check Modrinth's game versions.** Its auto-detection reads `api-version` from `plugin.yml`
   and ticks 1.20.x alone, so a version upload can quietly narrow what the page claims. See
   [`modrinth.md`](modrinth.md#fields).
4. **Add the release to [`versions.md`](versions.md)**: title, Minecraft range, and a short
   changelog in Markdown with its BBCode copy. The full `CHANGELOG.md` section is too
   long for a download page.
5. **Check the image URLs still resolve.** They are pinned to `main`, not to a tag, so they follow
   whatever later happens to those files. That is deliberate: a tag URL 404s until the tag exists,
   which is what broke the first Spigot preview. The cost is that renaming a capture breaks every
   listing at once.
6. **Carry any feature change into every description.** This is the one place the parallel
   texts hurt. `grep` for a phrase from the block you changed.

## Conventions

- **No count that a release can change goes in the copy.** Settings, test classes, CI legs, mirror
  looks, gate shapes, material groups, open Sonar findings: every one of those was accurate the
  day it was written and wrong a release later, and a figure a reader can contradict from the
  badge or the repo is worse than no figure. Say what the thing is — "any setting", "a look for
  every biome", "the whole matrix" — and let the live badges carry anything numeric. See
  [`shared.md`](shared.md#numbers-the-copy-does-not-print) for what this does and does not cover.
- **Images are pinned to `main`.** See above.
- **The logo may not go on the Modrinth page.** Its Content Rules 6.2.1 ban any page image
  "created or derived from generative AI output", and the logo SVG was written with Claude Code.
  Modrinth's icon is cropped from a gameplay capture and its description carries no banner; the
  live page predates the rule, so check it (step 2 in `modrinth.md`'s Keeping it current).
  **SpigotMC has no such rule** — its Terms & Rules were read and carry no AI clause — so the logo
  stays there. **Hangar is unchecked** and still uses the logo; check before the next upload.
  **CurseForge has no ban either**: its Moderation Policies (modified 2026-09-08, read 2026-10-06)
  ask only for a disclaimer on an AI-modified showcase image that may misrepresent the mod, and its
  Mod Author Terms carry no AI clause. So the logo is its avatar; the banner stays out, because
  SVG rendering there is unverified. Sources in
  [`curseforge.md`](curseforge.md#ai-generated-images). Reasoning in
  [`modrinth.md`](modrinth.md#rule-6-no-ai-generated-images-on-the-page). Clips and screenshots
  are real captures and are not affected.
- **The README carries a badge per site that has one.** SpigotMC's come from Spiget, by resource
  id: `img.shields.io/spiget/version/138936` and `spiget/downloads/138936`. Hangar's downloads
  come from `img.shields.io/hangar/dt/Wormhole-X-Treme` — there is no Hangar version badge, and
  no Paper or Hangar logo in shields' icon set, so that one carries a colour instead.
  **Modrinth has no badge yet**: `modrinth/dt` and `modrinth/v` answer "not found" while
  `api.modrinth.com/v2/project/wormhole-x-treme` 404s, which it does today. Add them once it
  resolves. Point them at that slug and nothing else — Modrinth also carries `wormholextreme`,
  which is the other fork, and a badge on its slug would advertise its downloads as ours.
  **CurseForge has no badge yet** either; there is no project id until the project exists.
- **The name and logo are not under the GPL.** Every listing says so and links
  [`TRADEMARK.md`](../../TRADEMARK.md); more than one project carries this name.
- **The non-affiliation notice ships on every listing.** It is trademark hygiene and it is the
  answer to the brand-name rule on Spigot at the same time. Do not drop it from one site because
  that site has no rule requiring it.
- **Claude Code is named as plain text, never linked.** Spigot's advertising rule wants a business
  arrangement behind a link to a commercial product. The disclosure loses nothing by being
  unlinked, so every listing reads the same way.
- **Do not position this fork against the other one.** Both forked the same 2011 original
  independently. The lineage line says that; nothing else needs to.
