# Listings

The copy that goes on the plugin sites, kept here so a release is an edit rather than a rewrite.

| File | What it is |
|---|---|
| [`shared.md`](shared.md) | The source of truth. Every fact and every block of prose the listings share, in plain Markdown with no site markup. |
| [`spigot.md`](spigot.md) | [SpigotMC](https://www.spigotmc.org/resources/) — form fields and the description in BBCode. |
| [`modrinth.md`](modrinth.md) | [Modrinth](https://modrinth.com/plugins) — project fields and the description in Markdown. |
| [`hangar.md`](hangar.md) | [Hangar](https://hangar.papermc.io/) — project fields and the page in Markdown. |

Nothing here is generated. The three site files are parallel texts, not renders of `shared.md`,
because the markup and the field sets differ enough that a generator would cost more than it
saves. What `shared.md` buys is that the *facts* live in one place: when a number or a link
changes, you change it there and then carry it into whichever site files quote it.

## Updating for a release

1. **Re-derive the counts** in [`shared.md`](shared.md#counts). Each row carries the command that
   produces it. They drift — the count of settings moved between 1.7.0 being drafted and it being
   published — and a number a reader can contradict is worse than no number.
2. **Bump the version and the supported range** in [`shared.md`](shared.md#release-facts), then in
   each site file's fields table.
3. **Check the image URLs still resolve.** They are pinned to `main`, not to a tag, so they follow
   whatever later happens to those files. That is deliberate: a tag URL 404s until the tag exists,
   which is what broke the first Spigot preview. The cost is that renaming a capture breaks three
   listings at once.
4. **Carry any feature change into all three descriptions.** This is the one place the parallel
   texts hurt. `grep` for a phrase from the block you changed.

## Conventions

- **Facts are countable or they do not go in.** "253 test classes" is checkable by anyone who
  clones the repo; "thousands of tests" is not, and a figure a badge contradicts is worse than
  silence. Where a live badge exists, prefer the badge.
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
