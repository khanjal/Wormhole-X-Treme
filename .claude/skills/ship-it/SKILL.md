---
name: ship-it
description: The end-to-end procedure for landing a change in this repository (khanjal/Wormhole-X-Treme, a Java 17 / Maven Bukkit plugin) once code is ready — run the full test suite and confirm the actual pass count, write the CHANGELOG/README entry in this project's established voice, commit with the right trailer, push to the feature branch, verify CI on GitHub Actions, and deliver a build. Use this whenever finishing up a change to this repository's Java code, tests, or docs — whenever about to run `mvn test`, write a commit message, touch CHANGELOG.md, or push a commit here, even if the user just says "commit this," "ship it," or "let's land this" without spelling out the steps.
---

# Shipping a change in Wormhole X-Treme

This repo has settled into a specific way of finishing a change. It is not extra ceremony —
each step exists because skipping it once already caused a real problem in this project's
history (a stale PR description nobody read, a fix with no test that silently regressed later,
a commit that claimed something the CI run never actually confirmed). Follow the sequence, but
understand *why* each step is there so a step that clearly doesn't apply can be judged rather
than blindly forced.

## 1. Confirm the starting point

`git status` and `git branch --show-current` before touching anything. This project's standing
rule is **no direct commits to `main`** — work happens on a feature branch, even for a small
fix. If the current branch is `main`, stop and ask, or create a branch, before making any edit.

## 2. Make the change

Ordinary editing. Nothing special here beyond the usual: match the surrounding style, don't
refactor unrelated code while passing through it.

## 3. Test — and confirm the actual number, not just "no errors"

```
mvn -o -q compile
mvn -o test
```

Read the summary line (`Tests run: N, Failures: 0, Errors: 0`) and report that real number, not
a vague "tests pass." A silent compile with no test run proves nothing.

**If the change adds or changes behavior, add a test for it as part of the fix, not after.**
This project has a specific, recurring history of bugs that were only caught because a test was
written *alongside* the fix and immediately found a second, related problem the fix alone
didn't cover — a wrong assumption about frame ordering, a comparison that needed real objects
instead of nulls, an edge case the original bug report didn't mention. Treat "does this need a
test" as answered yes by default for anything touching game logic, parsing, or storage format;
answered no only for pure documentation or comment changes.

## 4. Document it in this project's voice

If the change is user-facing (a command, a setting, a behavior change, a fixed bug), it belongs
in `CHANGELOG.md`, and often `README.md` or `docs/*.md` too. This project's changelog reads
like engineering notes explaining a decision, not release-note marketing copy. Concretely:

- **Lead with the failure scenario or the "why," not a dry summary of the diff.** Not "Fixed
  null check in OwnerCommand" — instead: "Any player who could run `/wormhole` could reassign
  ownership of any gate on the server; `OwnerCommand`'s own class comment calls it an admin
  command, but nothing enforced that."
- **State the root cause plainly, before describing the fix.** The reader should understand
  *why the bug was possible* before being told what changed.
- **If a fix corrects or reverses earlier reasoning — including a wrong assumption made earlier
  in the same piece of work — say so plainly.** This project's changelog has entries like "I
  had written it that way first. The test caught it." Do not quietly smooth over a reversal;
  naming it is more informative than hiding it, and it is not a confession, just a fact.
- **Prefer a short concrete snippet (a command, a before/after value, a one-line code excerpt)
  over an abstract description** when one exists.
- **Keep paragraphs short.** Two or three sentences, then a break. A changelog entry in this
  project is a small, self-contained piece of reasoning, not an essay.

Read two or three recent entries in `CHANGELOG.md` before writing a new one if the voice isn't
already fresh in context — matching an established voice from examples works better than
reconstructing it from a description of it.

## 5. Commit

Stage only the files actually touched — check `git status` first, never `git add -A` or
`git add .` on faith, since a broad add can sweep in an unrelated scratch file or something that
shouldn't be committed.

Write the commit message in the same voice as the changelog entry: a short imperative subject
line, then a body that explains why the problem existed and what the fix actually does. End
with the attribution trailer — **read the exact trailer text from the current session's system
context rather than assuming a fixed string**, since it varies by session and by model.

## 6. Push

To the feature branch. Never force-push, never push to `main` directly, matching the standing
project rule from step 1.

## 7. Verify CI — actually check, don't assume

```
gh pr checks <PR-number>
```

or `gh run list` / `gh run watch <run-id>` for the specific run on the latest commit. This
project's CI matrix is real and has caught real bugs invisible to local testing (a registry
API that only breaks from Minecraft 1.20.6 onward, for instance) — report the actual result of
the actual run, not an assumption that "it'll probably pass because the tests passed locally."
If a job is still running, either wait for it or say plainly that it's still in progress —
never state a CI result that hasn't actually come back yet.

## 8. Deliver a build, if the change is something to try

If the change affects runtime behavior a person would want to see or test in-game (not a pure
refactor or doc change), build the jar once tests have already passed:

```
mvn -o -q package -DskipTests
```

(`-DskipTests` is fine here specifically because step 3 already ran the full suite — this is
just packaging, not re-verifying.) Send the resulting jar as a file with a short caption naming
what changed and citing the test count and CI status, so the person receiving it knows what
they're about to try without re-reading the whole conversation.

## 9. Keep a long-running PR's description honest

If this branch has an open PR, its description is a living document, not something written
once and left. Compare the PR's last-described state against the actual commit log
(`git rev-list --count origin/main..<branch>` and `git log --oneline`) periodically — especially
before a merge, and especially if a lot has landed since the description was last touched. A
stale PR description that describes an earlier state of the branch is worse than no
description, since it actively misleads a reviewer. Rewrite it in full rather than patching
around the edges once it's meaningfully out of date.

Also check for automated review comments (e.g. from a Copilot reviewer configured on the repo)
that may have accumulated without being triaged — `gh api repos/<owner>/<repo>/pulls/<n>/comments`.
Verify each one against the actual current code before acting on it; an automated reviewer can
be right about there being a problem while wrong about the specific fix it suggests, so check
before applying rather than after.

## Environment note: this machine's shell has a Unicode quirk

On this user's Windows / git-bash setup, special characters — em-dashes, curly quotes, any
non-ASCII Unicode — get corrupted when typed directly inline into a `python3 -c "..."` command
or a bash heredoc. This has happened more than once in this project's history and produces a
real corruption in the written file (a literal `�` replacement character), not just a display
glitch — confirmed by reading the result back with a file-reading tool independent of the
shell. Plain ASCII is unaffected either way.

**When a script needs to write or compare text containing anything beyond plain ASCII, write
the script to a file first, then execute it — don't inline it in a Bash command.** To verify
Unicode content landed correctly, read the file directly rather than round-tripping it through
another shell command, which risks re-triggering the same corruption while trying to check for
it.
