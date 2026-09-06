# SonarCloud backlog - Wormhole-X-Treme

Snapshot of `main` at `4a06d49`, after the six bundled sweeps (PRs #115-#122).
Regenerate with the queries at the bottom rather than hand-editing the counts.

## Where the campaign stands

| | 2026-09-05 | after S3776 work | now |
|---|---|---|---|
| Open issues | 893 | 427 | **178** |
| Cognitive complexity | 4978 | 4074 | **4013** |
| Line coverage | 42.9% | 53.3% | **53.5%** |
| Tests | ~490 | 903 | **905** |

The sweeps closed **251 issues in 8 PRs**. What is left is almost entirely work that
needs a method read rather than a pattern matched.

## What the sweeps were, and what they cost

| PR | Sweep | Issues |
|---|---|---|
| #115 | dead wood and test idioms | 82 |
| #116 | naming and layout | 47 |
| #118 | local simplifications | 23 |
| #119 | anonymous classes to lambdas | 45 (23 edits) |
| #120 | switch labels, nested ifs, ternaries | 25 |
| #121 | List and ConcurrentMap on getters | 19 |
| #122 | the tail of the above | 7 |

Three things were worth more than the rule that found them:

- **Six Javadocs were reattached, not deleted.** The dangling-comment rule was finding
  documentation that had been separated from its method in an earlier refactor and left
  above a different one. Only two of the eight were genuinely dead.
- **Five `assertThrows` calls were passing for the wrong reason.** `assertThrows(X, () ->
  pair().getAllowed().add(y))` passes if `pair()` throws X. The setup call is hoisted out
  now, so only the call under test is inside the lambda.
- **`getLeverToggleByte` had no coverage at all.** It was two nested ternaries reaching the
  same answer by different routes. Two tests now pin it over all 256 byte values; they were
  run against the original before the rewrite, which is what makes "no behaviour change" a
  measurement rather than a claim.

And two were traps the compiler caught: three `length() == 0` sites were Bukkit `Vector`s,
where `length()` is magnitude; and `playerRecentArrival` is both a `MessageStrings` constant
and an unrelated private field, so the rename had to be scoped rather than global.

## What is left

### Per-site work - 119 issues

These cannot be swept. Each needs the method read, and for S3776 the net-first cycle:
characterisation test, mutate to prove it bites, reshape, mutate again.

| Rule | Count | Note |
|---|---|---|
| `S3776` Cognitive complexity | 47 | ~1 per PR. The rule that has been finding real bugs. |
| `S1192` Duplicated literal | 33 | Needs judgement about what deserves a name. `SubCommands.java` has 7. |
| `S135` Multiple break/continue | 19 | Usually dissolves as a side effect of an S3776 reshape. |
| `S1141` Nested try | 14 | Same - an extraction fixes it. |
| `S4144` Identical implementations | 6 | Read each one. Two of these turned out to be real duplication bugs. |

### The long tail - 59 issues across 33 rules

No rule here has more than three sites, so there is nothing left to bundle. Fix them as
you pass through the file.

| Rule | Count | Where |
|---|---|---|
| `S1168` Return an empty collection, not null | 3 | `StargateShapeRegistry.java` x2, `StargateAnimator.java` |
| `S1488` Return the expression directly | 3 | `GateSerializer.java`, `StargateYamlManager.java`, `StargateManager.java` |
| `S1612` Use a method reference | 3 | `RingPatternTest.java` x2, `PlayerTravelEventTest.java` |
| `S2629` Build the log message lazily | 3 | `WormholeXTreme.java` x3 |
| `S2925` Remove Thread.sleep from a test | 3 | `GateMaxOpenTimeTest.java` x3 |
| `S4042` Use Files.delete to see why it failed | 3 | `RingYamlManager.java` x2, `StargateYamlManager.java` |
| `S6126` Use a text block | 3 | `RingYamlManagerTest.java` x3 |
| `S9142` Use a switch expression | 3 | `ConfigurationYAML.java`, `Stargate3DShape.java`, `CommandUtilities.java` |
| `S1066`  | 2 | `StargateBlockSetup.java`, `WormholeXTremePlayerListener.java` |
| `S107`  | 2 | `BeamDestination.java`, `BeamFrame.java` |
| `S1128`  | 2 | `ShapeMatchPreferenceTest.java`, `WormholeXTreme.java` |
| `S2786`  | 2 | `ConfigManager.java`, `WXPermissions.java` |
| `S3626` Redundant jump statement | 2 | `WormholeXTreme.java`, `LegacyCompat.java` |
| `S3923`  | 2 | `CommandUtilities.java`, `Wormhole.java` |
| `S5785` assertTrue on a constant | 2 | `LegacyImportTest.java` x2 |
| `S6204`  | 2 | `MaterialCommand.java`, `SubCommandsTest.java` |
| `S6206`  | 2 | `BeamDestination.java`, `RingManager.java` |
| `S8491` Dangling Javadoc comment | 2 | `StargateRestrictions.java`, `WorldUtils.java` |
| `S1068`  | 1 | `BukkitRingWorld.java` |
| `S1117`  | 1 | `WormholeXTreme.java` |
| `S1155`  | 1 | `StargateAnimator.java` |
| `S1172`  | 1 | `GateOneWayTest.java` |
| `S1186`  | 1 | `StargateDBManager.java` |
| `S1871`  | 1 | `WXList.java` |
| `S2093`  | 1 | `ConfigurationFlatFile.java` |
| `S2130`  | 1 | `Stargate3DShape.java` |
| `S3252`  | 1 | `ShapeFileValidator.java` |
| `S3398`  | 1 | `BeamAnimation.java` |
| `S3457`  | 1 | `WormholeXTreme.java` |
| `S3824`  | 1 | `StargateBlockSetup.java` |
| `S5857`  | 1 | `Stargate3DShape.java` |
| `S6201`  | 1 | `RingCommand.java` |
| `S6905`  | 1 | `LegacyDatabaseImporter.java` |

## Two things to know before working this list

**The Sonar check fails on almost every PR here, and it carries no signal.** The gate wants
80% coverage on new code; a sweep or a rename touches hundreds of lines and adds no tests,
so it trips by construction. Every sweep PR merged with 12/12 required checks green and
zero new issues while showing a red Sonar X. Read `new_coverage` and the new-issue count
directly rather than trusting the tick.

**Retarget a stacked PR before merging its parent.** `main` requires 11 checks with
`strict: true`, so stacked branches must rebase anyway - but merging the parent with
`--delete-branch` auto-closes the child, and GitHub will not reopen a PR whose base branch
is gone. PR #117 was lost that way and had to be recreated as #118.

## Running this across several sessions

Splitting *by rule* was right for the sweeps, because each rule had one shape. It is wrong
for what is left: S3776 and S1192 both cut across every package, and two sessions working
different rules in the same file will conflict.

Split by directory instead - each session owns a subtree and fixes every issue inside it.
Past three or four sessions the CI queue is the limit, not the authoring: every merge
invalidates every other open PR, which then has to rebase and re-run the full matrix.

## Regenerating this file

Check the analysed revision first - `main`'s analysis lags the newest merge:

```bash
curl -s "https://sonarcloud.io/api/project_analyses/search?project=khanjal_Wormhole-X-Treme&ps=1"
```

```bash
curl -s "https://sonarcloud.io/api/issues/search?componentKeys=khanjal_Wormhole-X-Treme&resolved=false&ps=500&facets=rules"
```
