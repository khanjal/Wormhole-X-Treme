#!/usr/bin/env bash
# Open SonarCloud issues, for a pull request and for main, so a PR can be checked for what it adds.
#
#   scripts/sonar-issues.sh <pr-number>   the PR's issues, whether Sonar analysed its head, and main's total
#   scripts/sonar-issues.sh               main's issues only
#
# Exits 1 when the PR has open issues or Sonar has not yet analysed the PR's current head (a stale
# count reads as current), and 2 when a call fails or the argument is not a PR number. Run it after
# pushing, once Sonar has analysed the head. See .claude/skills/sonar-check and pr-review.
set -u
PROJECT=khanjal_Wormhole-X-Treme
REPO=khanjal/Wormhole-X-Treme
API=https://sonarcloud.io/api
PR="${1:-}"
status=0
PY=python3
command -v python3 >/dev/null 2>&1 || PY=python

if [ -n "$PR" ] && ! [[ "$PR" =~ ^[0-9]+$ ]]; then
  echo "usage: $0 [pr-number]" >&2
  exit 2
fi

fail() { echo "error: $1" >&2; exit 2; }

issues() { # prints the total, then "by rule", then one line per issue; $1 = extra query string
  curl -sf "$API/issues/search?componentKeys=$PROJECT&resolved=false&ps=500$1" | $PY -c '
import sys, json, collections
d = json.load(sys.stdin)
print(d["total"])
by = collections.Counter(i["rule"] for i in d["issues"])
print("  by rule:", ", ".join(f"{r} x{n}" for r, n in by.most_common()) or "none")
for i in d["issues"]:
    print("  %s %s %s:%s | %s" % (i["rule"], i["severity"], i["component"].split(":")[-1].split("/")[-1], i.get("line"), i["message"][:100]))
' 2>/dev/null
}

if [ -n "$PR" ]; then
  head=$(gh api "repos/$REPO/pulls/$PR" --jq .head.sha 2>/dev/null)
  [ -n "$head" ] || fail "could not read PR #$PR's head from GitHub"
  list=$(curl -sf "$API/project_pull_requests/list?project=$PROJECT") || fail "could not read Sonar's pull request list"
  seen=$(echo "$list" | $PY -c "import sys,json;print(next((p['commit']['sha'] for p in json.load(sys.stdin)['pullRequests'] if p['key']==sys.argv[1]),'none'))" "$PR")
  if [ "$head" = "$seen" ]; then
    echo "PR #$PR: Sonar analysed the head (${head:0:8})"
  else
    echo "PR #$PR: Sonar has NOT analysed the head yet (head ${head:0:8}, analysed ${seen:0:8}); wait and rerun"
    status=1
  fi
  out=$(issues "&pullRequest=$PR")
  total=$(echo "$out" | head -1)
  [[ "$total" =~ ^[0-9]+$ ]] || fail "could not read PR #$PR's issues from Sonar"
  echo "PR #$PR open issues: $total"
  echo "$out" | tail -n +2
  [ "$total" != "0" ] && status=1
fi

out=$(issues "")
total=$(echo "$out" | head -1)
[[ "$total" =~ ^[0-9]+$ ]] || fail "could not read main's issues from Sonar"
rev=$(curl -sf "$API/project_analyses/search?project=$PROJECT&ps=1" \
  | $PY -c "import sys,json;a=json.load(sys.stdin)['analyses'];print((a[0].get('revision') or '')[:8] if a else '')" 2>/dev/null)
echo "main open issues: $total, analysed at ${rev:-unknown} (a rise since the last look is for the rule and file list, not the bare count)"
echo "$out" | sed -n 2p
exit $status
