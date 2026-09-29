---
name: route-implementation
description: Decide which model implements a planned change in this repository (khanjal/Wormhole-X-Treme) and hand the plan to it — Sonnet for mechanical changes the plan fully specifies, Opus for anything needing judgment, or no hand-off at all for a small change — so the expensive planning model does only the reasoning, not the long edit-build-test loop. Covers the routing rules, escalation after a failed attempt, the hand-off prompt, and recording the choice on the PR so the rules can be tuned from evidence. Use this whenever a plan for a code change here is settled and editing is about to start, and whenever a delegated attempt comes back stopped or failing.
---

# Routing an implementation to the right model

Planning is short and decides everything; implementing is long and mostly reading, editing and
running Maven. So the session that planned — usually Fable or Opus — hands the implementing to a
sub-agent on a cheaper model when the plan allows it. This skill picks the model. It cannot pick
the planning model: that is the session's own, chosen before this runs.

The savings are real only if the cheaper model gets it right first time. A Sonnet attempt that
needs a second round of review fixes can cost more than Opus doing it once, which is why every
PR records the route (step 5) and the rules below are meant to change as that record grows.

## 1. First: is it worth handing off at all?

Do it in this session, with no sub-agent, when the change is small — roughly one or two files
and under about 50 changed lines, or a doc-only change. A sub-agent starts cold: it re-reads the
files, the skills and the build before its first edit, and on a small change that costs more than
it saves.

## 2. Pick the implementer

Go down the list; the first row that matches decides.

| The change | Route to |
|---|---|
| A delegated attempt already took two rounds (step 5) without passing review and CI | one model up: Sonnet → Opus → this session. If this session is already the model that failed, stop and revisit the plan with the user instead of a third try |
| Calls a Bukkit/Spigot/Paper API not already used in the codebase | `implementer-opus` |
| Touches a catch block, reflection, or anything version-gated (1.20 floor, 26.x removals) | `implementer-opus` |
| Changes state shared across classes (MirrorWindows, the gate registry, the scheduler) | `implementer-opus` |
| The plan leaves any design decision open — "either X or Y", "work out how to…" | `implementer-opus` |
| The plan names every file, method and test, and each step is mechanical: a Sonar sweep whose plan already marks the structural false positives to leave alone (`sonar-check`), tests for existing behaviour, a rename, config or message plumbing | `implementer-sonnet` |
| Anything else | `implementer-opus` |

When unsure between Sonnet and Opus, pick Opus: the rework risk costs more than the price gap.

## 3. Hand off

Run it with the Agent tool, `subagent_type` set to the implementer, in the background so this
session can draft the CHANGELOG entry meanwhile. The prompt carries everything, since the
sub-agent sees none of this conversation:

- the worktree's absolute path and branch name; the sub-agent's shell starts elsewhere
- the plan, step by step, with file paths and method names
- what the change is for, in two sentences, so it can recognise when the plan is wrong
- the tests expected, and what each should fail on if the code were broken
- anything already ruled out, so it does not rediscover it

## 4. When it comes back

- **Stopped on a plan problem:** fix the plan here, then hand the rest back to the same model,
  or up one if the problem showed the change needs more judgment than the route assumed.
- **Finished:** it has committed on the branch with its own model's trailer. Read the diff
  yourself before anything else — a skim for scope and anything surprising, not a review; the
  reviews come from the `pr-review` skill. Then carry on with `ship-it` from the CHANGELOG step.

The implementer counts as the model that **wrote** the code for `pr-review`'s table, so a
Sonnet-implemented change is first reviewed by Opus, not Sonnet. That guards against the
implementer's slips, not the planner's design blind spots; check that at least one of the two
reviews is by a model that neither planned nor implemented the change. The table gives that for
every usual pairing (Opus plans and Sonnet implements: Fable's final review); if it does not,
swap that review for one that does.

## 5. Record the route on the PR

Fill in the **Route** line of the PR template's Reviews section: which implementer, the table
planning model, the table row that sent it there, and the number of implementation rounds — 1 if it came back finished,
plus one for each hand-back or escalation, plus one for each round of review fixes it needed.

To tune the rules, read the record back:

```bash
gh -R khanjal/Wormhole-X-Treme pr list --state merged --limit 50 --search "Route in:body" --json number,body --jq '.[] | "\(.number) \((.body // "") | (capture("Route: (?<r>[^\r\n]*)").r? // "no Route line"))"'
```

A row that keeps sending Sonnet changes needing two or more rounds belongs with Opus; say so in
this file, with the PR numbers.
