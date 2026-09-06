# Automated issue triage

`.github/workflows/claude-triage-sweep.yml` runs Claude over issues labelled
`needs-investigation` once a day, posts one preliminary comment on each, and swaps the label to
`triaged`. The prompt is `.claude/skills/triage-sweep/SKILL.md`.

It is advisory. It classifies the issue, names the files most likely involved, asks for the
information the report is missing, and links probable duplicates. It does not close issues,
propose patches, or modify files — the job has `contents: read`.

## Setup

1. **Add the secret.** Run `claude setup-token` locally and store the result as the repository
   secret `CLAUDE_CODE_OAUTH_TOKEN` (Settings > Secrets and variables > Actions). It bills
   against the subscription of whoever generated it and **lasts one year**.
2. **Create the labels.**
   ```
   gh label create needs-investigation -R khanjal/Wormhole-X-Treme \
       -c "#fbca04" -d "Queued for Claude's preliminary triage"
   gh label create triaged -R khanjal/Wormhole-X-Treme \
       -c "#c5def5" -d "Claude has posted a preliminary investigation"
   ```
3. **Try it before trusting it.** Label one issue, then run the workflow from the Actions tab
   (`Run workflow`, `max_issues: 1`) and read what it posted.

The Claude GitHub App is *not* required: the workflow passes `GITHUB_TOKEN`, so comments arrive
from `github-actions[bot]`.

## Queueing issues

Applying `needs-investigation` by hand is deliberate for now — it keeps the first weeks of runs
under a maintainer's eye. To queue every new report automatically, add the label to the issue
templates, which already set `bug` and `enhancement`:

```yaml
labels: ["bug", "needs-investigation"]
```

Blank issues, which this repository allows, stay unqueued either way.

## Tuning

| Knob | Where | Now |
| --- | --- | --- |
| How often | `cron` in the workflow | daily, 09:17 UTC |
| Issues per run | `max_issues` input | 3 |
| Turn limit | `--max-turns` in `claude_args` | 40 |
| Wall-clock limit | `timeout-minutes` | 20 |

A run over an empty queue costs one `gh issue list` and stops, so the daily schedule is cheap
when nothing is waiting. GitHub disables cron on a public repository after 60 days without
activity; this one is active, but the schedule is worth re-checking if it ever goes quiet.

## Constraints worth knowing before changing it

- **The label swap is the re-processing guard.** Nothing else stops the sweep commenting on the
  same issue every day, which is why the skill treats it as the last step for each issue and
  why it never happens before the comment posts.
- **`gh issue edit` is deliberately not granted.** The swap goes through
  `.github/scripts/triage-label.sh`, which can only add `triaged` and remove
  `needs-investigation`. The sweep reads text written by strangers; the narrow tool list is the
  containment, not the prompt's good intentions.
- **A schedule trigger, not `issues: [opened]`.** Two reasons. Anything triggered by
  `GITHUB_TOKEN` raises no further workflow events, so an issue-opened trigger silently cannot
  see issues filed by automation — no failed run, nothing in the Actions tab. And on issue
  events the action refuses to run for a user without write access, so triggering on
  `issues: [opened]` would additionally need `allowed_non_write_users: "*"` to work for the
  outside reporters who file most bugs. Schedule triggers skip that check.
- **Scheduled runs are still attributed to a person** — whoever last edited the `cron` line —
  and the action rejects bot actors. If that line is ever changed by an app rather than a
  human, the run starts failing until the actor is listed in `allowed_bots`.
- **The tool list is the only grant.** In automation mode Claude has no shell until
  `--allowedTools` says so. Adding a step that needs a new command means adding it there; the
  skill's own `allowed-tools` frontmatter does not widen what the workflow permits.

## Taking this to another repository

The workflow, the script, and the skill are self-contained: copy the three files, add the
secret and the two labels, and edit the repository-specific parts of the skill (step 4 names
this plugin's docs and its version matrix).

Past two or three repositories, copying stops paying. The action accepts
`plugin_marketplaces` and `plugins`, so the skill can live once in a marketplace repository and
be installed at run time with a namespaced prompt — `/triage:triage-sweep` — with the job
itself defined once as a reusable workflow that each repository calls. Repository-specific
context then belongs in each repository's own `CLAUDE.md` rather than in the shared skill.
