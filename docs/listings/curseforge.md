# CurseForge

Copy for the CurseForge project at <https://www.curseforge.com/minecraft/bukkit-plugins/wormhole-x-treme>,
submitted 2026-10-12 and **awaiting CurseForge's approval** at the time of writing. Facts and prose come from
[`shared.md`](shared.md); what is here is CurseForge's own field set.

Rows marked **(confirmed)** are the form's own wording, read off it while the project was being
filled in on 2026-10-12. The values are what was recommended for each field then; anything
marked **(check)** is a value that depends on a list the form offered and that was not read back,
so correct it here from the live page.

**BukkitDev is the same project.** CurseForge runs dev.bukkit.org, and a project in the Bukkit
Plugins class shows there too, at <https://dev.bukkit.org/projects/wormhole-x-treme>. There is no
separate BukkitDev form; whatever is set here is what that page shows. Link both, since some
server owners still look on bukkit.org first.

**The description is Modrinth's.** CurseForge takes Markdown, and Modrinth's description already
carries no logo, so the page is the Markdown block in [`modrinth.md`](modrinth.md#description) as
it stands. Do not copy it into this file: a fourth parallel text is one more to forget. When the
Modrinth description changes, paste the new one here too.

---

## Fields

| Field | Value |
|---|---|
| Project name **(confirmed)** | `Wormhole X-Treme`. The form asks for no game, category or class words ("Minecraft", "Plugin") in the name. |
| Logo **(confirmed)** | a crop of the dialled Massive gate from `docs/images/gates/gate-shapes-active.png`, 512×512 — **not** the logo, see [Images](#images) below |
| Summary | see [Summary](#summary) below |
| Class | Bukkit Plugins |
| Main category | Teleportation |
| Additional categories **(check)** | Mechanics, Fun |
| Allow comments | off. Support goes to the issue tracker. |
| Unlisted project | off |
| Social links | GitHub: `https://github.com/khanjal/Wormhole-X-Treme`. Discord blank, for the reason in [`shared.md`](shared.md#links). |
| Description | the Markdown block in [`modrinth.md`](modrinth.md#description) |
| Project licence **(check)** | GNU General Public License version 3. Pick an "or later" variant if the list has one: `NOTICE.txt` carries the "any later version" grant. |
| Project distribution | allow distribution outside CurseForge. The GPL allows it anyway, and the jar is on three other sites. |

**On the licence.** The form says "use the original license for forked projects". This is one, and
the original's licence is the same GPL-3.0-or-later: `NOTICE.txt` carries the 2011 grant from Ben
Echols and Dean Bailey alongside the 2026 one.

**On distribution.** CurseForge warns that downloads outside its own ecosystem do not count
towards its rewards programme. That is the whole cost of allowing it.

## Summary

The form asks for one English sentence that says what the project does. Its length cap was not
recorded.

```
Stargate-style travel for your server: dialling gates, transport rings, beaming and quantum mirrors, in one jar for Minecraft 1.20 through 26.3.
```

(144 characters.) If the cap turns out tighter, fall back to the 97-character preferred tagline in
[`shared.md`](shared.md#tagline).

## Images

**CurseForge's stance on AI-generated images was not checked.** Modrinth bans them from the page
outright (see [`modrinth.md`](modrinth.md#rule-6-no-ai-generated-images-on-the-page)), and the
logo was written with Claude Code. So CurseForge got the safe choice from the start: the icon is
cropped from a real capture, and the description, being Modrinth's, carries no banner. If
CurseForge's rules turn out to allow it, the logo could go back. Until then, leave it off.

The form's own logo rule **(confirmed)**: not a solid or gradient colour, not a game logo, not a
trademarked asset, nothing NSFW. The gate crop passes all four.

## Version upload

| Field | Value |
|---|---|
| File | `WormholeXTreme-<version>.jar` from the release. The form takes `.jar` only. |
| Display name | `Wormhole X-Treme <version>`. The default is the jar's filename. |
| Release options | publish automatically once approved |
| Bukkit (game versions) | tick every 1.20.x, 1.21.x and 26.x the list offers, within that version's range in [`versions.md`](versions.md). Snapshots off. Do not trust what comes pre-ticked: Modrinth reads `api-version: "1.20"` and ticks 1.20.x alone, and CurseForge may do the same. |
| Release type | Release |
| Changelog | that version's Markdown block in [`versions.md`](versions.md). If the editor shows the Markdown literally, copy the rendered notes from the Modrinth version page instead. |

The first upload, 1.10.0, went in with the project on 2026-10-12.

## Keeping it current

1. **Wait for approval, then check the page.** Read the licence and categories back and correct
   the **(check)** rows above, and that the project's slug really is `wormhole-x-treme` (the
   links in the README and `shared.md` assume it).
2. **Add the badges once the page is live.** shields.io's CurseForge badges take the numeric
   project ID, not the slug, so read it off the page (the About box shows it) and add
   `img.shields.io/curseforge/dt/<id>` and `curseforge/v/<id>` to the README beside the others.
3. **Check CurseForge's content rules for AI-generated images** before ever putting the logo on
   the page.
4. **Upload each release here too.** Same jar, same changelog as Modrinth and Hangar. A change to the description is re-pasted
   from [`modrinth.md`](modrinth.md#description).
