# SonarCloud backlog - Wormhole-X-Treme

Snapshot of `main` at `2044a18` (PR #113 merged, #114 still open).
Regenerate with the query at the bottom rather than hand-editing the counts.

## Where the campaign actually stands

| | 2026-09-05 | now | change |
|---|---|---|---|
| Open issues | 893 | 427 | **-466** |
| Cognitive complexity | 4978 | 4074 | **-904** |
| Line coverage | 42.9% | 53.3% | **+10.4pp** |
| Tests | ~490 | 903 | **+413** |

The headline number is moving. What is not moving fast is the recent stretch: since the
S3776 campaign started the count has gone 565 -> 427, about 5.7 issues per PR across ~24
PRs, and S3776 itself went 67 -> 48. Roughly **one issue closed per PR on the rule being
worked**, with the rest falling out incidentally.

So: real progress, but the most expensive PRs in the backlog have been going to one of the
least numerous rules.

## The bundling opportunity

**S1604 and S9357 are the same 23 locations, double-reported.** 45 of the 427 open issues
are 23 edits. Confirmed by comparing file+line sets: 22 of 23 overlap exactly.

### Sweep 1: anonymous class to lambda - 45 issues

One PR. Mechanical, and the compiler proves each one. `RingTransit.java` alone is 6.
Watch for anonymous classes that reference `this` or hold state - those are not lambdas.

| Rule | Count | Main/Test | Concentrated in |
|---|---|---|---|
| `S9357` Anonymous inner class to lambda | 23 | 22/1 | `RingTransit.java` x6, `WormholeXTremeVehicleListener.java` x4, `WormholeXTremePlayerListener.java` x2, +11 more |
| `S1604` Anonymous inner class to lambda (older rule, same sites) | 22 | 22/0 | `RingTransit.java` x6, `WormholeXTremeVehicleListener.java` x4, `WormholeXTremePlayerListener.java` x2, +10 more |

### Sweep 2: naming and layout - 47 issues

One PR, scripted rename. **S115 is 24 hits in `ConfigManager.MessageStrings`**, a
camelCase enum. Checked: nothing calls `MessageStrings.valueOf`, `.name()` or
`.values()`, and `toString()` is overridden to return the message text, so the constant
names are never persisted or parsed. The rename is internal-only across 268 call sites
in 36 files, and the compiler catches any miss.

| Rule | Count | Main/Test | Concentrated in |
|---|---|---|---|
| `S115` Constant name not SCREAMING_CASE | 25 | 25/0 | `ConfigManager.java` x24, `GateSerializer.java` |
| `S1124` Modifier order | 8 | 8/0 | `StargateManager.java` x6, `WormholeXTremeVehicleListener.java`, `DefaultSettings.java` |
| `S1659` One declaration per line | 8 | 8/0 | `Stargate.java` x3, `WormholeXTremePlayerListener.java` x3, `BukkitRingWorld.java` x2 |
| `S117` Local variable naming | 6 | 6/0 | `StargateYamlManager.java` x2, `ConfigurationFlatFile.java`, `WormholeXTremeVehicleListener.java`, +2 more |

### Sweep 3: dead wood - 56 issues

One PR. Unused imports, commented-out code, dangling Javadoc, private constructors on
utility classes, and `throws IOException` on test methods that cannot throw it.
`RingYamlManagerTest.java` holds 12 of the 16 S1130.

| Rule | Count | Main/Test | Concentrated in |
|---|---|---|---|
| `S1130` Declared exception cannot be thrown | 16 | 0/16 | `RingYamlManagerTest.java` x12, `RingAccessTest.java`, `WormholeXTremePlayerListenerMountTest.java`, +2 more |
| `S1118` Utility class needs a private constructor | 14 | 14/0 | `CommandUtilities.java`, `StargateShapeFactory.java`, `ConfigurationYAML.java`, +11 more |
| `S8491` Dangling Javadoc comment | 10 | 10/0 | `ConfigManager.java` x3, `WorldUtils.java` x2, `StargateRestrictions.java` x2, +3 more |
| `S1128` Unused import | 9 | 6/3 | `SoundsTest.java`, `RingSoundsTest.java`, `GateInteractionHandler.java`, +6 more |
| `S125` Commented-out code | 7 | 7/0 | `StargateManager.java` x3, `StargateRestrictions.java`, `Complete.java`, +2 more |

### Sweep 4: local simplifications - 58 issues

One or two PRs. Each is a small local rewrite already covered by the compiler and the
existing tests. S1066 (merge nested ifs) is the one to read carefully - a merged
condition can change which branch an `else` belongs to.

| Rule | Count | Main/Test | Concentrated in |
|---|---|---|---|
| `S7158` Use isEmpty() instead of length() == 0 | 17 | 17/0 | `ConfigurationYAML.java` x6, `BeamCommand.java` x4, `ConfigCommand.java`, +6 more |
| `S1066` Mergeable nested if | 11 | 11/0 | `WormholeXTremeBlockListener.java` x3, `WormholeXTremeEntityListener.java` x2, `LegacyCompat.java`, +5 more |
| `S6208` Merge switch cases with comma labels | 11 | 11/0 | `WXPermissions.java` x3, `MaterialUtils.java` x2, `LegacyCompat.java` x2, +3 more |
| `S1125` Unnecessary boolean literal | 6 | 6/0 | `ConfigManager.java` x2, `ComplexPermission.java`, `Force.java`, +2 more |
| `S1126` if-then-else to a single return | 5 | 5/0 | `WormholeXTremePlayerListener.java` x2, `RedstoneCommand.java`, `CommandUtilities.java`, +1 more |
| `S1905` Unnecessary cast | 4 | 4/0 | `RingCommand.java`, `CommandUtilities.java`, `Wormhole.java`, +1 more |
| `S3358` Nested ternary | 4 | 4/0 | `SubCommands.java` x2, `WorldUtils.java` x2 |

### Sweep 5: test-code idioms - 26 issues

One PR, test sources only, so nothing shipped changes.
`WormholeXTremeRedstoneListenerTest` holds 10 of the 11 S6068.

| Rule | Count | Main/Test | Concentrated in |
|---|---|---|---|
| `S6068` Redundant eq(...) in a Mockito verify | 11 | 0/11 | `WormholeXTremeRedstoneListenerTest.java` x10, `GateActivationSwitchTest.java` |
| `S8924` Use a static import for mock | 6 | 0/6 | `StargateManagerTest.java` x6 |
| `S5778` Only one method call in an assertThrows | 5 | 0/5 | `RingPatternTest.java` x2, `WormholeXTremeVehicleRiderFacingTest.java`, `RingAccessTest.java`, +1 more |
| `S9016` Extract mock creation to a local variable | 4 | 0/4 | `DialSignTargetRestoreTest.java` x2, `GateActivationSwitchTest.java`, `WormholeXTremeRedstoneListenerTest.java` |

### Sweep 6: return the interface - 19 issues

One PR, and the only sweep that changes signatures - `ArrayList` to `List` on public
getters. Worth doing alone so a compile break is unambiguous. `StargateShapeLayer` is 9
of the 19.

| Rule | Count | Main/Test | Concentrated in |
|---|---|---|---|
| `S1319` Declare the interface type, not the implementation | 19 | 19/0 | `StargateShapeLayer.java` x9, `Stargate.java` x5, `StargateNetwork.java` x2, +3 more |

### Per-site work - 123 issues

These cannot be swept. Each needs the method read, and for S3776 the net-first cycle:
characterisation test, mutate to prove it bites, reshape, mutate again.

| Rule | Count | Note |
|---|---|---|
| `S3776` Cognitive complexity | 48 | ~1 per PR. The rule that has been finding real bugs. |
| `S1192` Duplicated literal | 34 | Needs judgement about what deserves a name. `SubCommands.java` has 7. |
| `S135` Multiple break/continue | 19 | Usually dissolves as a side effect of an S3776 reshape. |
| `S1141` Nested try | 16 | Same - an extraction fixes it. `WormholeXTreme` and `LegacyCompat` have 4 each. |
| `S4144` Identical implementations | 6 | Read each one. Two of these turned out to be real duplication bugs. |

### Not yet classified - 53 issues

- `S2629` x3  - WormholeXTreme.java
- `S1168` x3  - StargateShapeRegistry.java, StargateAnimator.java
- `S4042` x3  - RingYamlManager.java, StargateYamlManager.java
- `S6126` x3 Use a text block - RingYamlManagerTest.java
- `S2925` x3  - GateMaxOpenTimeTest.java
- `S1488` x3  - GateSerializer.java, StargateYamlManager.java, StargateManager.java
- `S9142` x3  - ConfigurationYAML.java, Stargate3DShape.java, CommandUtilities.java
- `S6204` x2  - MaterialCommand.java, SubCommandsTest.java
- `S6206` x2  - BeamDestination.java, RingManager.java
- `S107` x2  - BeamDestination.java, BeamFrame.java
- `S5785` x2  - LegacyImportTest.java
- `S3626` x2  - WormholeXTreme.java, LegacyCompat.java
- `S2786` x2  - ConfigManager.java, WXPermissions.java
- `S1197` x2  - ConfigurationFlatFile.java
- `S127` x2  - ConfigurationFlatFile.java
- `S3457` x1  - WormholeXTreme.java
- `S2093` x1  - ConfigurationFlatFile.java
- `S3398` x1  - BeamAnimation.java
- `S3252` x1  - ShapeFileValidator.java
- `S6201` x1  - RingCommand.java
- `S6905` x1  - LegacyDatabaseImporter.java
- `S3824` x1  - StargateBlockSetup.java
- `S1068` x1  - BukkitRingWorld.java
- `S1612` x1  - PlayerTravelEventTest.java
- `S1172` x1  - GateOneWayTest.java
- `S1117` x1  - WormholeXTreme.java
- `S1871` x1  - WXList.java
- `S1155` x1  - StargateAnimator.java
- `S1186` x1  - StargateDBManager.java
- `S2130` x1  - Stargate3DShape.java
- `S5857` x1  - Stargate3DShape.java

## Suggested order

Sweeps 1-6 cover **251 of the 427 open issues** in roughly 6-8 PRs. The remaining S3776
backlog is **~40 PRs for 48 issues**. Doing the sweeps first takes the count from 427 to
about **176** in a run of small, low-risk, quickly-reviewed PRs, and leaves a backlog that
is almost entirely work which genuinely needs thinking about.

Totals: 251 sweepable, 123 per-site, 53 not yet classified.

This is not an argument to stop S3776. It is the most valuable rule per issue closed - the
one that surfaced the legacy shape files discarding button offsets, the silent failure in
interactive `/wormhole complete`, and the malformed shape file that took down every other
shape. It is an argument against it being the only thing in flight while 251 issues sit in
scriptable piles.

## Running this across several sessions

Two constraints shape this. `main` requires 11 status checks with `strict: true` and
linear history, so every merge invalidates every other open PR: they must rebase and
re-run the full matrix. Merges serialise however many sessions are authoring. And
splitting the work *by rule* guarantees conflicts, because the big rules cut across
every package.

So split by directory instead. Each session owns a subtree and fixes every sweepable
rule inside it, which is conflict-free by construction.

| Session | Scope | Sweepable issues |
|---|---|---|
| 1 | `src/test/**` | 46 |
| 2 | `main/model/`, `main/logic/` | 69 |
| 3 | `main/config/` | 45 |
| 4 | top level, `command/`, `utils/`, `permissions/`, `plugin/` | 91 |

Two rules cannot be parallelised and have to land first, each on its own:

- **S115** - the `ConfigManager.MessageStrings` rename reaches into every subtree.
- **S1319** - `ArrayList` to `List` on public getters changes signatures, so it ripples
  out to callers in other packages.

Past three or four sessions the CI queue is the limit, not the authoring.

## Regenerating this file

Check the analysed revision first - `main`'s analysis lags the newest merge:

```bash
curl -s "https://sonarcloud.io/api/project_analyses/search?project=khanjal_Wormhole-X-Treme&ps=1"
```

```bash
curl -s "https://sonarcloud.io/api/issues/search?componentKeys=khanjal_Wormhole-X-Treme&resolved=false&ps=500&facets=rules"
```
