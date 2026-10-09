---
name: route-implementation
description: Decide which model implements a planned change in this repository (khanjal/Wormhole-X-Treme) and hand the plan to it — Sonnet for mechanical changes the plan fully specifies, Opus for anything needing judgment, or no hand-off at all for a small change — so the expensive planning model does only the reasoning, not the long edit-build-test loop. Covers the routing rules, escalation after a failed attempt, the hand-off prompt, who fixes review findings, which models review, and recording the choice on the PR so the rules can be tuned from evidence. Use this whenever a plan for a code change here is settled and editing is about to start, and whenever a delegated attempt comes back stopped or failing.
---

# Routing an implementation to the right model

Planning is short and decides everything; implementing is long and mostly reading, editing and
running Maven. So the session that planned — usually Fable or Opus — hands the implementing to a
sub-agent on a cheaper model when the plan allows it. This skill picks the model. It cannot pick
the planning model: that is the session's own, chosen before this runs.

The savings are real only if the cheaper model gets it right first time. A Sonnet attempt that
has to be redone can cost more than Opus doing it once, which is why every PR records the route
(step 6) and the rules below are meant to change as that record grows. They are a starting
guess, not a measured cost model.

## 1. First: is it worth handing off at all?

A sub-agent starts cold: it re-reads the files, the skills and the build before its first edit,
and the planning session has usually read those files already. Keep the work in this session
when what is left is short — roughly one or two files and under about 50 changed lines, a
doc-only change, or a single edit-and-test pass. Hand off when what is left is a long loop:
several files, several test classes, a mutation battery, or rounds of build fixes.

## 2. Pick the implementer

Go down the list; the first row that matches decides. Record the row number (step 6).

| # | The change | Route to |
|---|---|---|
| R1 | Two implementer attempts on the same model have failed to produce code that builds and passes its tests (plan hand-backs do not count) | one model up: Sonnet → Opus → this session. If this session is already the model that failed, stop and revisit the plan with the user instead of a third try |
| R2 | Calls a Bukkit/Spigot/Paper API not already used in the codebase | `implementer-opus` |
| R3 | Touches a catch block, reflection, or anything version-gated (1.20 floor, 26.x removals) | `implementer-opus` |
| R4 | Changes state shared across classes (Windows, the gate registry, the scheduler) | `implementer-opus` |
| R5 | The plan leaves any design decision open — "either X or Y", "work out how to…" | `implementer-opus` |
| R6 | The plan names every file, method and test, and each step is mechanical: a Sonar sweep whose plan already marks the structural false positives to leave alone (`sonar-check`), a rename, config or message plumbing, or tests for existing behaviour — the last is the likeliest to need rework, since telling a vacuous test from an equivalent mutant is judgment (`mutation-check`); watch its record | `implementer-sonnet` |
| R7 | Anything else | `implementer-opus` |

When unsure between Sonnet and Opus, pick Opus: the rework risk costs more than the price gap.

## 3. Hand off

Run it with the Agent tool, `subagent_type` set to the implementer, in the background. While it
runs, leave the worktree alone — no edits, commits or builds there; the implementer commits as it
goes and the mutation harness rewrites files, so two writers collide. Draft the CHANGELOG entry
in the scratchpad instead.

The prompt carries everything, since the sub-agent sees none of this conversation:

- the worktree's absolute path and branch name; the sub-agent's shell starts elsewhere
- the plan, step by step, with file paths and method names
- what the change is for, in two sentences, so it can recognise when the plan is wrong
- the tests expected, and what each should fail on if the code were broken
- anything already ruled out, so it does not rediscover it
- on a second attempt or an escalation: what is already committed (`git log origin/main..HEAD`),
  which plan steps it covers, and what went wrong — the new implementer continues from that
  state, and says so if it thinks the earlier commits should be discarded instead

## 4. When it comes back

- **Stopped on a plan problem:** fix the plan here, then hand the rest back to the same
  sub-agent with `SendMessage`, or to a fresh one a model up if the problem showed the change
  needs more judgment than the route assumed.
- **Finished:** it has committed on the branch with its own model's trailer. Read the diff
  yourself — a skim for scope and anything surprising, not a review. Then carry on with
  `ship-it` from the CHANGELOG step; the implementer has already run ship-it's test and
  compiler-warning checks and reported the results.

The first time this skill is used, check `git log --format=%b origin/main..HEAD` shows the
implementer's own model in its trailers, not the planner's. If it does not, fix the agent files
before relying on the trailer, and fill "Written by" from the Route line meanwhile.

## 5. Reviews, and who fixes what they find

The implementer counts as the model that **wrote** the code for `pr-review`'s table, so a
Sonnet-implemented change is first reviewed by Opus, not Sonnet. That table guards against the
writer's slips, not the planner's design blind spots, so one more rule on top of it:
**the final review is never by the planner.** Where the table would make the planner the final
reviewer, swap the two: the planner does the first review and the other model the final one.

| Planned by | Implemented by | First review | Final review |
|---|---|---|---|
| Opus | Sonnet | Opus | Fable |
| Opus | Opus | Sonnet | Fable |
| Fable | Sonnet | Fable | Opus |
| Fable | Opus | Fable | Sonnet |
| Opus or Fable | this session (no hand-off) | as `pr-review`'s table | as `pr-review`'s table |

Fable planning and Opus implementing leaves Sonnet as the only model that did neither, so it
takes the final review — the quicker pass, now also the one that reviews the fixes. That is the
accepted price of an independent final review; on a risky change of that kind, keep the
implementing in this session instead, and `pr-review`'s own table applies.

**Every review fix goes back to the implementer** — the model reviews' and Copilot's alike, small
ones included — so every line of the change still has one author and the final reviewer is still
independent of it. Send them to the same sub-agent with `SendMessage`, which resumes it with its
context intact, so a fix round skips the cold start that step 1 counts as delegation's main
cost; spawn a fresh one only for an escalation, or when the session that spawned it is gone (a later
or cloud session picking up the PR), telling it what is committed as in step 3. The planner fixes a finding itself only when the
finding is in the plan's design; say so in the PR description.

## 6. Record the route on the PR

Fill in the **Route** line of the PR template's Reviews section:

- **row**: the table row, R1–R7, or "none" when the work stayed in this session
- **implementer** and **planned by**: the models
- **attempts**: implementer runs until it built and passed its tests — 1 is first time; an
  escalation counts on the model it escalated to, so write e.g. "Sonnet 2, Opus 1"
- **hand-backs**: stops on a plan problem — the planner's cost, not the implementer's
- **review fixes**: rounds of fixes after the reviews, split by where each finding lay — e.g.
  "impl 1, plan 1". An implementation that passed its own tests but was wrong counts here, under
  impl, so this is where a redo shows up; plan findings are the planner's cost

To tune the rules, read the record back:

```bash
gh -R khanjal/Wormhole-X-Treme pr list --state merged --limit 50 --search "Route in:body" --json number,body --jq '.[] | "\(.number) \((.body // "") | (capture("Route: (?<r>[^\r\n]*)").r? // "no Route line"))"'
```

Tune on **attempts plus impl review fixes** per row. A row whose Sonnet changes keep needing a
second attempt or an impl fix round belongs with Opus; a row where Opus always gets it first time may be ready for Sonnet. Change the table
here, with the PR numbers that justify it.
