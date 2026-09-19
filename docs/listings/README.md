# Listings

The copy that goes on the plugin sites, kept here so a release is an edit rather than a rewrite.

| File | What it is |
|---|---|
| [`shared.md`](shared.md) | The source of truth. Every fact and every block of prose the listings share, in plain Markdown with no site markup. |
| [`spigot.md`](spigot.md) | [SpigotMC](https://www.spigotmc.org/resources/) — form fields and the description in BBCode. |
| [`modrinth.md`](modrinth.md) | [Modrinth](https://modrinth.com/plugins) — project fields and the description in Markdown. |
| [`hangar.md`](hangar.md) | [Hangar](https://hangar.papermc.io/) — project fields and the page in Markdown. |

All three are published:
[SpigotMC](https://www.spigotmc.org/resources/wormhole-x-treme.138936/) ·
[Modrinth](https://modrinth.com/plugin/wormhole-x-treme) ·
[Hangar](https://hangar.papermc.io/khanjal/Wormhole-X-Treme). Editing a file here does not change
a live page; it records what the page should say, and somebody still has to paste it.

Nothing here is generated. The three site files are parallel texts, not renders of `shared.md`,
because the markup and the field sets differ enough that a generator would cost more than it
saves. What `shared.md` buys is that the *facts* live in one place: when a number or a link
changes, you change it there and then carry it into whichever site files quote it.

## Updating for a release

1. **Bump the version and the supported range** in [`shared.md`](shared.md#release-facts), then in
   each site file's fields table. These are the only numbers a release should have to touch — see
   the convention below.
3. **Check the image URLs still resolve.** They are pinned to `main`, not to a tag, so they follow
   whatever later happens to those files. That is deliberate: a tag URL 404s until the tag exists,
   which is what broke the first Spigot preview. The cost is that renaming a capture breaks three
   listings at once.
4. **Carry any feature change into all three descriptions.** This is the one place the parallel
   texts hurt. `grep` for a phrase from the block you changed.

## Conventions

- **No count that a release can change goes in the copy.** Settings, test classes, CI legs, mirror
  looks, gate shapes, material groups, open Sonar findings: every one of those was accurate the
  day it was written and wrong a release later, and a figure a reader can contradict from the
  badge or the repo is worse than no figure. Say what the thing is — "any setting", "a look for
  every biome", "the whole matrix" — and let the live badges carry anything numeric. See
  [`shared.md`](shared.md#numbers-the-copy-does-not-print) for what this does and does not cover.
- **Images are pinned to `main`.** See above.
- **The name and logo are not under the GPL.** Every listing says so and links
  [`TRADEMARK.md`](../../TRADEMARK.md); more than one project carries this name.
- **The non-affiliation notice ships on every listing.** It is trademark hygiene and it is the
  answer to the brand-name rule on Spigot at the same time. Do not drop it from one site because
  that site has no rule requiring it.
- **Claude Code is named as plain text, never linked.** Spigot's advertising rule wants a business
  arrangement behind a link to a commercial product. The disclosure loses nothing by being
  unlinked, so all three read the same way.
- **Do not position this fork against the other one.** Both forked the same 2011 original
  independently. The lineage line says that; nothing else needs to.
