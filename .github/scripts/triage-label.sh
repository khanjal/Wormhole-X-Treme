#!/usr/bin/env bash
# Moves one issue out of the triage queue. This exists as a script, rather than granting the
# sweep `gh issue edit` outright, so that text hidden in an issue body cannot reach anything
# but these two labels.
set -euo pipefail

issue="${1:-}"
case "${issue}" in
    '' | *[!0-9]*)
        echo "usage: triage-label.sh <issue-number>" >&2
        exit 64
        ;;
esac

gh issue edit "${issue}" \
    --repo "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is not set}" \
    --add-label triaged \
    --remove-label needs-investigation
