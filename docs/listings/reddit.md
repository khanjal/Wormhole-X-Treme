# Reddit

Copy for a launch post on **r/admincraft**. Not published. Unlike the site files this is a one-off
announcement, not a page to keep current, so it has no fields table and no "keeping it current"
beyond the checklist at the bottom.

Facts and prose come from [`shared.md`](shared.md). Reddit takes **Markdown**, so the body pastes in as
written. Switch the editor to Markdown mode first; the default rich editor mangles tables and
code blocks.

**Read r/admincraft's rules the day you post.** The subreddit allows it **(confirmed by Justin)**, but flair
requirements, self-promotion limits and account-age rules were not read here, and they move.

## The approach

r/admincraft is critical of AI-built projects. The post does not try to dodge that:

- **The disclosure is in the first paragraph**, not the last. Being open at the start takes the
  sting out of it; being found out lower down is what turns people hostile. It also matches every
  other listing, where the disclosure ships in full ([`README.md`](README.md#conventions)).
- **Evidence follows the claim immediately.** The CI matrix, the tests and the static analysis are
  linked, because a skeptical reader can open those and cannot argue with them.
- **One first-hand claim, if and only if it is true.** See the placeholder in the body.
- **No countable claims.** No test counts, no setting counts, per the
  [convention](README.md#conventions). Link the badges and let them carry the numbers.

## Title

Pick one. Reddit titles cap at 300 characters and none of these is near it.

```
Wormhole X-Treme: the 2011 Stargate-style plugin, brought forward to 1.20–26.3 (AI-assisted, CI and tests linked)
```

```
I've been maintaining Wormhole X-Treme, a Stargate-style travel plugin: gates, transport rings, beaming and quantum mirrors (AI-assisted, details inside)
```

The first states the version range and the AI disclosure in the title itself, which is the version I'd
use here. A title that omits it and a first line that adds it reads as the same thing hidden
one layer down.

## Media

Reddit's own upload, not a link to GitHub. Attach in this order:

1. `docs/images/gates/gate-dial.webp`, the gate dialling. It is the strongest capture and the
   one that has to carry the post.
2. `docs/images/mirrors/mirror-effects.webp`
3. `docs/images/rings/ring-cycle.webp`
4. `docs/images/beams/beam-up.webp`

Reddit may not take animated WebP. If it refuses, convert to MP4 or GIF first, and if the
clips are over the limit use the GitHub-release MP4 route in [`../CAPTURES.md`](../CAPTURES.md).

## Body

````markdown
Wormhole X-Treme is a Stargate-style travel plugin that was written in 2011 and went quiet. I've
been bringing it forward to Minecraft 1.20–26.3 and adding some systems it never had.

**Up front, since this sub will want to know: much of the modernisation work was done with Claude
Code, and the commit history records it.** I read every change, and nothing merges without passing
the checks below. I'd rather you judge it on those than on my say-so, so here they are:

- Every push builds and runs the test suite across the whole supported range, Java 17 and 25,
  Spigot API, Paper and Purpur: [the CI workflow](https://github.com/khanjal/Wormhole-X-Treme/actions)
- SonarCloud fails a pull request on *any* open finding, not just coverage:
  [dashboard](https://sonarcloud.io/project/overview?id=khanjal_Wormhole-X-Treme)
- It is compiled against the oldest supported API on purpose, so the compiler enforces the floor
- Nothing third-party in the jar except bStats, relocated. No database, one YAML file per gate
- GPL-3.0, issue tracker open, and I answer bug reports

[Running on my own server since ___. Delete this line if it is not true.]

**What it does.** Four ways to get somewhere:

| | What you build | Reaches |
|---|---|---|
| **Stargates** | a frame of blocks and a button | any gate, across worlds |
| **Transport rings** | a circle of slabs, in pairs | its pair, same world |
| **Beaming** | nothing at all | any named destination, across worlds |
| **Quantum mirrors** | one banner on a wall | any other mirror, across worlds |

For admins: everything configurable in game with `/wormhole config`, no restart. Works with or
without Vault and LuckPerms. Optional PlaceholderAPI, CoreProtect, WorldGuard flags and Dynmap
layers, each off until you enable it. Nothing is downloaded; the update check only looks.

Compatibility, honestly: CI proves it compiles and its tests pass at each version where the API
moved. It does not prove every gate has been played on every version, and Folia is not supported.

- [SpigotMC](https://www.spigotmc.org/resources/wormhole-x-treme.138936/) ·
  [Modrinth](https://modrinth.com/plugin/wormhole-x-treme) ·
  [Hangar](https://hangar.papermc.io/khanjal/Wormhole-X-Treme) ·
  [source](https://github.com/khanjal/Wormhole-X-Treme)
- [Setup, config and permissions](https://github.com/khanjal/Wormhole-X-Treme/blob/main/docs/guide/SERVER.md)

Credits: written by Lologarithm and alron with contributions from lirelent and Jeremy Wood.
Unofficial and not affiliated with the Stargate franchise or MGM.

What I'd most like: bug reports from real servers, and which versions you'd want tested harder.
````

**What was left out on purpose.** The Credits paragraph is two lines here, not the full
section. The licence-and-trademark paragraphs are on every site listing and need not repeat in a
post. The lineage line about the other forks stays out entirely, per
[`README.md`](README.md#conventions).

**Check the SonarCloud and Actions links** resolve to the project before posting. The two above
were written from the project key and repo name and not opened.

## Replies

- **Answer technical questions and bug reports, fast.** That is what the thread is for and it is
  what changes minds.
- **Do not argue about whether AI-assisted code is legitimate.** It rarely goes well and it takes
  over the thread. One calm line is enough: it is disclosed, reviewed and gated, and the code is
  there to read.
- **Do not delete or edit away criticism.** Say what is wrong, if it is right, and fix it.
- **Expect "show me a bug it shipped."** Have an answer: the changelog records the fixes, and
  pointing at one is better than saying there are none.
- **Do not mention the other forks** unless asked, and then briefly.

## Before posting

1. **Read r/admincraft's current rules** and use whatever flair they require.
2. **At least one jar run on a real server** by you, so the placeholder line in the body is true.
   If you cannot say it, remove the line; do not fill it with something vague.
3. **The three live listings are current**, since the post links all three.
4. **CurseForge is not linked** unless it exists by then.
5. **Be around for the first few hours.** A post you answer in the first hour does better than one
   you answer the next day, and the first replies set the tone.
6. **Post once.** No cross-post to r/spigot the same day.
