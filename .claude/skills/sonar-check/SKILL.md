---
name: sonar-check
description: How to check a change against this repository's (khanjal/Wormhole-X-Treme) open SonarCloud issues before pushing it — run the local PMD ruleset for the eight rules it covers, then read for the rules PMD cannot see (S135, S1141, S4144 and the long tail), and know which findings are structural false positives that must not be "fixed". Use this whenever working through the Sonar backlog, before pushing a sweep or refactor, when a PR shows a red Sonar check, or when deciding whether a reported issue is worth fixing at all.
---

# Checking a change against SonarCloud without waiting for SonarCloud

The scan runs on the PR and takes minutes. Most of what it will say can be known in about ten
seconds first. This is how, and — more importantly — where the local check stops being able to
tell you anything, because that boundary is where the mistakes happen.

## 1. Run the local ruleset first

```
mvn pmd:pmd -Dformat=csv
```

Then read `target/pmd.csv`. The rules and the evidence behind them are documented in
`pmd-ruleset.xml`; the short version is that it covers eight Sonar rules, catches 46 of the 47
open issues across them, and is right about four times in five. A finding is a lead, not a
verdict.

Two things follow from that ratio, and they point in opposite directions:

**A clean run means something.** If PMD reports nothing new on a file you touched, you have not
introduced an S3776, S1168, S1488, S1128, S1066, S1068, S1186 or S3626. Recall on those is
essentially total.

**A finding does not.** Twelve of the 58 findings are cognitive-complexity hits that Sonar does
not raise, because PMD scores a method above 15 where Sonar scores it below. Confirm against
SonarCloud before treating one as work.

This is deliberately not wired into `mvn verify` and deliberately not `pmd:check`. At 79%
precision a failing build would sometimes be wrong, and a check that is sometimes wrong is one
people learn to skip.

## 2. Then read for what PMD cannot see

Roughly half the open backlog has no PMD equivalent at all. These are the three largest such
groups, and they need the file open:

**S135, reduce break/continue in this loop (16 open).** Spread across fourteen files, at most
two in any one. It usually dissolves as a side effect of an S3776 reshape rather than being
worth its own change — if you are already extracting a method from the loop body, check whether
the breaks went with it.

**S1141, extract this nested try.** Concentrated in `WormholeXTreme.java` and
`WormholeXTremeVehicleListener.java`. Before flattening one, read
[[catch-throwable-can-be-load-bearing]] — some of the nesting exists to catch `LinkageError`
separately from `Exception` across server versions, and collapsing the two changes behaviour on
exactly the servers hardest to test.

**S4144, identical implementations (6 open).** Read every one. Two turned out to be real
duplication bugs during the earlier sweeps. But see the next section first, because five of the
six currently open are not fixable at all.

The rest is a long tail across roughly thirty rules with one to three sites each. There is
nothing to bundle; fix them as you pass through the file, which is what the sweeps already
concluded.

## 3. Findings that must not be fixed

**S4144 on anything in `events/`.** Every Bukkit event class needs both a static
`getHandlerList()` and an instance `getHandlers()`, and both return the same `HandlerList`
field, so their bodies are necessarily identical. This is not style — it is enforced from two
directions. `org.bukkit.event.Event` declares `public abstract HandlerList getHandlers()`, so
the instance method must exist; and `SimplePluginManager` looks the static one up reflectively,
carrying the literal error string `getHandlerList must be static`. Delete or delegate either one
and event registration breaks at runtime, which no test here would catch because the tests do
not run a plugin manager.

That accounts for `StargateCreatedEvent`, `StargateRemovedEvent`, `RingTravelEvent`,
`StargatePlayerTravelEvent` and `StargateMinecartTeleportEvent`. Mark them won't-fix in
SonarCloud rather than leaving them to be rediscovered every sweep. The sixth, in
`BeamTravelTest`, is unrelated and may be genuine.

**Anything where `length()` is a Bukkit `Vector`.** An earlier sweep nearly rewrote three
`length() == 0` sites as `isEmpty()`. On a `Vector`, `length()` is magnitude. The compiler
caught it that time; it would not have if the receiver had been a `String` on one branch.

## 4. For S3776 specifically, keep the net first

Cognitive complexity is the group that has repeatedly turned up real bugs, and it is the one
where a reshape can silently change behaviour. The cycle the earlier work settled on:

1. Write a characterisation test against the method as it stands.
2. Mutate the original to prove the test actually bites — a test that passes against a
   deliberately broken version is guarding nothing (see [[assert-presence-not-absence]]).
3. Reshape.
4. Mutate again.

`getLeverToggleByte` is the example worth remembering: two nested ternaries reaching the same
answer by different routes, with no coverage at all. The tests that now pin it over all 256 byte
values were run against the original before the rewrite, which is what makes "no behaviour
change" a measurement rather than a claim. See [[wormhole-test-style]] for how to write them.

## 5. Reading the real numbers when the PR check is red

The Sonar quality gate on this repository fails on almost every PR and carries no signal: it
wants 80% coverage on new code, and a sweep or a rename touches hundreds of lines while adding
no tests, so it trips by construction. Sweep PRs have merged with every required check green and
zero new issues while showing a red Sonar X. Read the new-issue count and `new_coverage`
directly rather than trusting the tick.

Check what SonarCloud has actually analysed before comparing anything to it — `main`'s analysis
lags the newest merge, and a local run compared against a stale scan produces confusing
differences that are really just the gap between two commits:

```bash
curl -s "https://sonarcloud.io/api/project_analyses/search?project=khanjal_Wormhole-X-Treme&ps=1"
```

```bash
curl -s "https://sonarcloud.io/api/issues/search?componentKeys=khanjal_Wormhole-X-Treme&resolved=false&ps=500&facets=rules"
```

`docs/SONAR_BACKLOG.md` holds the campaign's shape and the counts as of its last regeneration.
It goes stale quickly — regenerate from those queries rather than trusting its numbers, and
rather than hand-editing them.

## 6. Then ship it normally

Nothing here replaces the usual finishing sequence. Once the change is made, [[ship-it]] still
applies: run the tests and report the real count, write the CHANGELOG entry in this project's
voice, and confirm CI rather than assuming it.
