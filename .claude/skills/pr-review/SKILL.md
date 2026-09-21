---
name: pr-review
description: How a pull request in this repository (khanjal/Wormhole-X-Treme) gets reviewed before it merges — a Sonnet review on every PR before it opens, and a Fable review of the finished PR before every merge, never by the model that wrote the code; then Copilot once at open, recognising the quota-exhausted "review" that looks like a clean one; all three comment surfaces and the PR's own Sonar issues; and saying on the PR which reviews ran. Use this whenever opening a PR here, whenever about to merge one, and whenever asked to check, triage or review the open PRs — including from a cloud session, which has no local memory of any of this.
---

# Reviewing a pull request before it merges

Every PR here is reviewed three times before it merges: by Sonnet before it opens, by Copilot
once it has, and by Fable on the finished PR just before it merges. Green CI replaces neither: the matrix proves the code builds and the
tests pass, not that the tests test the right thing.

## The checklist in every PR description

Every PR description carries the **Reviews** checklist from `.github/pull_request_template.md`:
the first review, Copilot, the final review, findings handled, Sonar at zero. Tick each box
as it is done, with the model and the commit it reviewed, so anyone reading the PR can see what
is still owed. A PR is not ready to merge with a box unticked.

`gh pr create --body` does **not** apply the template -- GitHub only uses it for PRs opened in
the web UI -- so paste the checklist into the body yourself, and add it to any open PR that
lacks it. Update the ticks by editing the body:

```bash
gh api repos/khanjal/Wormhole-X-Treme/pulls/<n> --jq .body > body.md   # edit, then:
gh api repos/khanjal/Wormhole-X-Treme/pulls/<n> -X PATCH -F body=@body.md
```

## 1. Before the PR opens: a review by a different model

Always, whatever Copilot's quota. The model that wrote the code shares its own wrong
assumptions, so a review by the same model misses exactly the bugs that matter most. Run the
review as a sub-agent with a model override -- the Agent tool's `model` parameter -- not as
`/code-review` in the session that wrote the code.

- **`sonnet` (Sonnet 5), before the PR opens.** The quick first pass. On 2026-09-21 it reviewed
  #412 blind and found the bug the user had just hit in-game, a second serious one (arrows
  through the far end's iris), and a third nobody else saw (a per-gate entity scan every
  second), with no false positives. Neither the writing session nor its own `/code-review` had
  found any of them.
- **`fable` (Fable 5.1), before merging -- step 5.** On the same blind test it found both
  serious bugs too, plus three edge cases Sonnet missed (ridden carts, fast movement, ender
  pearls). It goes last so it reviews everything, fixes included: a fix for one review's
  finding is new code nobody has reviewed yet.

Each took about ten minutes and 200k tokens on a 1,700-line PR; Sonnet's tokens are cheaper.

**Neither review is ever by the model that wrote the code.** The pairing above assumes Opus
wrote it, the usual case. If it was written with a different model, swap in Opus for whichever
reviewer matches it:

| Written by | First review (step 1) | Final review (step 5) |
|---|---|---|
| Opus | Sonnet | Fable |
| Sonnet | Opus | Fable |
| Fable | Sonnet | Opus |

Give it: the repo path, the diff range, that it is read-only (no edits, commits or GitHub
posts), what the change is meant to do, and to hunt for bugs with a concrete failure scenario
each, including in unchanged code whose assumptions the change broke. Ask for at most 15
findings with file:line and a confidence, and for it to say plainly if it finds nothing.

Run it in the background and do the CHANGELOG, PMD and test build meanwhile. When it returns:

- check each finding against the code before acting on it -- a reviewer can be right about a
  problem and wrong about its cause
- fix the real ones on the branch with a test, and prove each test with the `mutation-check`
  skill before a commit message says it guards anything
- write down the ones not fixed, with why, in the PR description as possibilities to check
- say in the PR description which model reviewed it and what became of each finding

## 2. When the PR opens: request Copilot, once

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

Copilot still matters after step 1: every model in step 1 comes from the same vendor, and
Copilot does not. It is the one reviewer whose blind spots are not correlated with the author's.

## 3. Before merging: read what came back

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

## 4. When Copilot could not review: steps 1 and 5 are the review of record

If the quota is gone, or the PR never had a review requested, the Sonnet review from step 1 and
the Fable review from step 5 are what the PR merges on. Do not wait for the allowance to reset, and do not ask whether
to substitute; the user decided this on 2026-09-21. Say so on the PR, rather than calling it
reviewed as if Copilot had done it. A PR whose code changed a lot after step 1 gets step 1 again
on the new commits.

## 5. Last, on every PR: Fable on the finished PR

When everything else is done -- findings fixed, CI green, Sonar at zero, any in-game check
passed -- run a `fable` sub-agent on the whole PR as it now stands (`main...` the branch head),
with the same brief as step 1. Tell it what the earlier reviews found and what became of each,
so it spends its time on what they missed rather than confirming what is already fixed.

Handle its findings the way step 1 says. A fix it prompts is small by then, and does not need a
further Fable pass unless it changes behaviour well beyond the finding. Say on the PR that it ran.

## 6. Sonar: the PR's issues, not the tick

A green Sonar check can hide an open issue. Read the count directly:

```bash
curl -s "https://sonarcloud.io/api/issues/search?componentKeys=khanjal_Wormhole-X-Treme&pullRequest=<n>&resolved=false"
```

Zero is the bar before merging (see the `sonar-check` skill for the false positives that
should be marked won't-fix instead). The PR checks only score new code, so `main` can build
up a backlog nobody sees on a PR. Query it without `pullRequest` when triaging.

## 7. Merge

- The Fable review in step 5 has run on the latest commit, or on one the later commits only
  fixed its findings in.
- All checks green on the latest commit, not an earlier one. A failure that is a registry
  refusing a download (HTTP 429 from Maven Central) is infrastructure: re-run the failed job.
- Squash merge, which is what the history uses.
- The repo deletes a merged branch automatically. Before merging a PR that another PR is
  stacked on, retarget the child to `main`. Otherwise it closes, and cannot be reopened.
- Never enable auto-merge unless the user asked.
