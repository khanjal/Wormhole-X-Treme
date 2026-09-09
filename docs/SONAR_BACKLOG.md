# SonarCloud backlog - Wormhole-X-Treme

Snapshot of `main` at `ecb9f0f` (PR #206), analysed 2026-09-08.
Regenerate with the queries at the bottom rather than hand-editing the counts.

**There is no backlog left to work.** Three issues are open and all three are design questions
waiting on a decision, not defects. This file is now a record of what the campaign did and what
is worth carrying into the next one.

## Where it ended

| | 2026-09-05 | after the S3776 work | now |
|---|---|---|---|
| Open issues | 893 | 427 | **3** |
| Cognitive complexity | 4978 | 4074 | **3560** |
| Line coverage | 42.9% | 53.3% | **73.0%** |
| Tests | ~490 | 903 | **1321** |

Reliability **A**, security **A**, maintainability **A**. Duplication 0.5% across 22,566 lines.
Every `S3776` (method too complex) is closed; there were 18 at the halfway mark.

## The three that are left

All in the beam subsystem, all deliberate, none of them a bug:

| Rule | Where | The question |
|---|---|---|
| `S6206` | `BeamDestination` | Make it a `record`? It is already immutable; the change is real but it is a public type. |
| `S107` | `BeamDestination` | 8-parameter constructor. |
| `S107` | `BeamFrame` | 14-parameter constructor. |

The two `S107`s are the same call: a builder or a parameter object would satisfy the rule, and
whether that reads better than 14 named arguments at the two call sites is a matter of taste
rather than a defect. Left open on purpose so the decision is visible.

## What the campaign was worth

The sweeps (PRs #115-#122) closed 251 issues by pattern. The later work (#150-#206) was
different in kind: coverage-led, one class at a time, with a mutation run to prove each test
bites before the PR opened.

That second half found **three production bugs** the rule that pointed at the file had not
noticed:

- indexed gate wiring could never be removed without deleting the whole gate, so the redstone
  an admin lays to wire a gate was trapped there
- a debug line was built on every player interaction whether or not `FINE` was enabled (#199)
- gate protection did nothing when another plugin cancelled the event first, and Bukkit lets a
  later listener un-cancel (#53, PR #207)

**And it fixed two bugs that could not have reached anybody**, which is worth recording as
plainly as the three that could. A lever's powered bit eating its facing (#153) and
`getLeverToggleByte` reaching the same answer by two routes (#118) were both real defects in
real code -- in methods with no production callers. Both are deleted now, `LegacyCompat` in
#208 and `getLeverToggleByte` in the utils audit that followed it.

The lesson is not "check for callers before fixing" so much as **a test suite makes dead code
look maintained**. Both of those methods had careful tests, which is exactly why three separate
PRs went into them without anybody asking whether they ran.

**The yield collapses once the easy classes are covered.** After #199 the remaining PRs found
no production bugs at all -- every mutation that survived turned out to be a fault in the test I
had just written, not in the code. That is the signal to stop, and it is why this file closes
rather than listing more targets.

What is uncovered now is uncovered for a reason: thin I/O wrappers (`StargateBlockSetup`
drawing, `BukkitRingWorld`), accessor padding (`ConfigManager`), and lifecycle that needs a live
server. `BeamAnimation.arriveAndSettle` is the one real gap -- the class cannot be loaded under
test at all, because its `TRAVELLER_EFFECTS` static field builds `PotionEffectType` values from
a registry that wants a running server.

## Two things to know before working this list

**The Sonar check fails on almost every PR here, and it carries no signal.** The gate wants
80% coverage on new code; a sweep or a rename touches hundreds of lines and adds no tests, so it
trips by construction. Every sweep PR merged with 12/12 required checks green and zero new
issues while showing a red Sonar X. Read `new_coverage` and the new-issue count directly rather
than trusting the tick.

**Retarget a stacked PR before merging its parent.** `main` requires 11 checks with
`strict: true`, so stacked branches must rebase anyway - but merging the parent with
`--delete-branch` auto-closes the child, and GitHub will not reopen a PR whose base branch is
gone. PR #117 was lost that way and had to be recreated as #118.

## Splitting the work across sessions

Splitting *by rule* was right for the sweeps, because each rule had one shape. It is wrong for
coverage work: split by directory instead, so each session owns a subtree. Past three or four
sessions the CI queue is the limit rather than the authoring, since every merge invalidates
every other open PR, which then has to rebase and re-run the full matrix.

## Regenerating this file

Check the analysed revision first - `main`'s analysis lags the newest merge:

```bash
curl -s "https://sonarcloud.io/api/project_analyses/search?project=khanjal_Wormhole-X-Treme&ps=1"
```

```bash
curl -s "https://sonarcloud.io/api/issues/search?componentKeys=khanjal_Wormhole-X-Treme&resolved=false&ps=500&facets=rules"
```

```bash
curl -s "https://sonarcloud.io/api/measures/component?component=khanjal_Wormhole-X-Treme&metricKeys=coverage,line_coverage,ncloc,tests,reliability_rating,security_rating,sqale_rating,duplicated_lines_density,cognitive_complexity"
```
