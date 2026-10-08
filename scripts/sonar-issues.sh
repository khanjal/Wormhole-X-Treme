#!/usr/bin/env bash
# Open SonarCloud issues, for a pull request and for main, so a PR can be checked for what it adds.
#
#   scripts/sonar-issues.sh <pr-number>   the PR's issues, whether Sonar analysed its head, and main's total
#   scripts/sonar-issues.sh               main's issues only
#
# Exits 1 when the PR has open issues, or when Sonar has not yet analysed the PR's current head
# (a stale count reads as current). See .claude/skills/sonar-check and .claude/skills/pr-review.
set -u
PROJECT=khanjal_Wormhole-X-Treme
REPO=khanjal/Wormhole-X-Treme
API=https://sonarcloud.io/api
PR="${1:-}"
status=0

issues() { # prints "total" then one line per issue; $1 = extra query string
  curl -s "$API/issues/search?componentKeys=$PROJECT&resolved=false&ps=500$1" | python -c '
import sys, json, collections
d = json.load(sys.stdin)
print(d["total"])
by = collections.Counter(i["rule"] for i in d["issues"])
print("  by rule:", ", ".join(f"{r} x{n}" for r, n in by.most_common()) or "none")
for i in d["issues"]:
    print("  %s %s %s:%s | %s" % (i["rule"], i["severity"], i["component"].split(":")[-1].split("/")[-1], i.get("line"), i["message"][:100]))
'
}

if [ -n "$PR" ]; then
  head=$(gh api "repos/$REPO/pulls/$PR" --jq .head.sha)
  seen=$(curl -s "$API/project_pull_requests/list?project=$PROJECT" \
    | python -c "import sys,json;print(next((p['commit']['sha'] for p in json.load(sys.stdin)['pullRequests'] if p['key']=='$PR'),'none'))")
  if [ "$head" = "$seen" ]; then
    echo "PR #$PR: Sonar analysed the head (${head:0:8})"
  else
    echo "PR #$PR: Sonar has NOT analysed the head yet (head ${head:0:8}, analysed ${seen:0:8}); wait and rerun"
    status=1
  fi
  out=$(issues "&pullRequest=$PR")
  echo "PR #$PR open issues: $(echo "$out" | head -1)"
  echo "$out" | tail -n +2
  [ "$(echo "$out" | head -1)" != "0" ] && status=1
fi

out=$(issues "")
echo "main open issues: $(echo "$out" | head -1)  (a PR must not raise this; note it before merging, compare after)"
echo "$out" | sed -n 2p
exit $status
