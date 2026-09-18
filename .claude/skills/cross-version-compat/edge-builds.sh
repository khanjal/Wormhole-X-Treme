#!/usr/bin/env bash
# Compiles and tests against the API versions that break most often, before CI does.
#   .claude/skills/cross-version-compat/edge-builds.sh              # every edge, full suite
#   .claude/skills/cross-version-compat/edge-builds.sh 'Mirror*Test' # every edge, some tests
# Offline (-o): the versions must already be in ~/.m2, which a past CI-matrix build leaves behind.
set -u

tests="${1:-}"
jdk21="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"
jdk25="/c/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot"

# name | JAVA_HOME | maven args
edges=(
  "spigot 1.20 (floor)|$jdk21|-Plegacy-api -Dspigot.api.version=1.20-R0.1-SNAPSHOT"
  "spigot 1.20.6 (registry Material, old dismount gone)|$jdk21|-Pmodern-api -Dspigot.api.version=1.20.6-R0.1-SNAPSHOT"
  "spigot 1.21.4 (EntityType.BOAT gone)|$jdk21|-Pmodern-api -Dspigot.api.version=1.21.4-R0.1-SNAPSHOT"
  "spigot 26.2 (newest)|$jdk21|-Pmodern-api -Dspigot.api.version=26.2-R0.1-SNAPSHOT"
  "paper 26.2|$jdk25|-Pmodern-api -Dpaper.api.version=26.2.build.124-stable"
)

failed=0
for edge in "${edges[@]}"; do
  IFS='|' read -r name home args <<< "$edge"
  # shellcheck disable=SC2086
  if [ -n "$tests" ]; then
    out=$(JAVA_HOME="$home" mvn -o -B -q clean test $args "-Dtest=$tests" -Dsurefire.failIfNoSpecifiedTests=false 2>&1)
  else
    out=$(JAVA_HOME="$home" mvn -o -B -q clean test $args 2>&1)
  fi
  if [ $? -eq 0 ]; then
    echo "PASS  $name"
  else
    failed=1
    echo "FAIL  $name"
    echo "$out" | grep -E '\.java:\[|symbol:|location:|Tests run:.*(Failures|Errors): [1-9]|<<< FAIL|BUILD FAILURE' | sort -u | head -12 | sed 's/^/      /'
  fi
done

# clean test leaves target/ built against the last edge; put the default back for anyone packaging.
mvn -o -B -q clean compile > /dev/null 2>&1
exit $failed
