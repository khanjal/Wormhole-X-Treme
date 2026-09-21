---
name: pr-review
description: How a pull request in this repository (khanjal/Wormhole-X-Treme) gets reviewed before it merges — request Copilot once when the PR opens, recognise the quota-exhausted "review" that looks like a clean one, fall back to /code-review as the review of record, read all three comment surfaces and the PR's own Sonar issues, and say on the PR which review stood in. Use this whenever opening a PR here, whenever about to merge one, and whenever asked to check, triage or review the open PRs — including from a cloud session, which has no local memory of any of this.
---

# Reviewing a pull request before it merges

Every PR here gets a second pair of eyes before it merges. Copilot is the first choice, and
`/code-review` stands in when Copilot cannot run. Green CI does not replace either: the matrix
proves the code builds and the tests pass, not that the tests test the right thing.

## 1. When the PR opens: request Copilot, once

Request it once, at open. Never again after follow-up commits: each review spends from a
monthly allowance, and once that is gone a request gets nothing at all.

`gh` needs `-R khanjal/Wormhole-X-Treme` in this repo (there are three remotes). Request the
review with the GraphQL mutation, naming the reviewer bot by its node id:

```bash
gh api repos/khanjal/Wormhole-X-Treme/pulls/<n> --jq .node_id
gh api graphql -f query='mutation{requestReviews(input:{pullRequestId:"<node id>",botIds:["BOT_kgDOCnlnWA"],union:true}){pullRequest{number}}}'
```

`BOT_kgDOCnlnWA` is `copilot-pull-request-reviewer`. `suggestedActors` lists only
`copilot-swe-agent`, which is the coding agent: requesting it returns success and nothing
ever arrives. The mutation's return value proves nothing either way. Check the timeline:

```bash
gh api repos/khanjal/Wormhole-X-Treme/issues/<n>/timeline --jq '.[] | select(.event=="review_requested") | .requested_reviewer.login'
```

Empty means no request landed. If it will not register, ask the user to add Copilot from the
GitHub UI.

## 2. Before merging: read what came back

The review lags CI by 6-15 minutes, so read it at the moment of merging, not when CI goes
green. There are three surfaces, and reading two of them is the usual miss:

```bash
gh api repos/khanjal/Wormhole-X-Treme/pulls/<n>/reviews    # review bodies
gh api repos/khanjal/Wormhole-X-Treme/pulls/<n>/comments   # inline comments
gh api repos/khanjal/Wormhole-X-Treme/issues/<n>/comments  # issue-level: SonarCloud, humans
```

**A quota-exhausted review looks like a clean one.** Its state is `COMMENTED`, it has no
inline comments, and its whole body reads "Copilot was unable to review this pull request
because the user who requested the review has reached their quota limit." Read the body
before calling a PR reviewed.

Treat Copilot's findings as informed, not authoritative. Check each against the current code;
it has argued for restoring a `catch (Throwable)` this project removed on purpose.

## 3. When Copilot could not review: `/code-review` is the review of record

If the quota is gone, or the PR never had a review requested, run `/code-review <n>` and use
that. Do not wait for the allowance to reset, and do not ask whether to substitute. The user
decided this on 2026-09-21. It is weaker than Copilot, so:

- fix each finding on the PR's branch with a test, or answer it on the PR if it is wrong
- before a commit message says a test guards the fix, prove it with the `mutation-check` skill
- say on the PR that `/code-review` stood in, what it found, and what became of each finding

## 4. Sonar: the PR's issues, not the tick

A green Sonar check can hide an open issue. Read the count directly:

```bash
curl -s "https://sonarcloud.io/api/issues/search?componentKeys=khanjal_Wormhole-X-Treme&pullRequest=<n>&resolved=false"
```

Zero is the bar before merging (see the `sonar-check` skill for the false positives that
should be marked won't-fix instead). The PR checks only score new code, so `main` can build
up a backlog nobody sees on a PR. Query it without `pullRequest` when triaging.

## 5. Merge

- All checks green on the latest commit, not an earlier one.
- Squash merge, which is what the history uses.
- The repo deletes a merged branch automatically. Before merging a PR that another PR is
  stacked on, retarget the child to `main`. Otherwise it closes, and cannot be reopened.
- Never enable auto-merge unless the user asked.
