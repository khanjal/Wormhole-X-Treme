#!/usr/bin/env bash
# A local load test: what Wormhole costs a real Paper server's tick, with Mineflayer bots (#523).
#   perf-boot.sh                     every scenario, step and jar the settings below name
# Each run boots a fresh server through boot-test.sh, sets one scenario up from the console, joins the
# bots (player-test/load.js), warms up, and records every tick for DURATION seconds with a small
# measuring plugin (perf-tick/, compiled here against the server's own API, installed in every run).
# A run without Wormhole goes beside each run with it, so the difference is the plugin's.
#
# Scenarios (SCENARIO, space-separated, default "viewers wanderers gateview"):
#   idle       N gate pairs dialled open and N mirrors hung, no players: the tickers alone
#   viewers    N bots in front of MIRRORS (4) mirrors, turning their heads and stepping aside
#   wanderers  N bots walking at random by spawn, GATES (4) open gates and MIRRORS mirrors far off
#   gateview   N bots in front of GATES (4) open gates, for each GATE_VIEW (behind open) and
#              FUNNEL_MIN (0 3), gate-view-funnel-min-length
# BOTS steps N ("1 10"); DURATION is the measured seconds (120; a gate's view is walked again once a
# minute, so give gateview 180 or more), WARMUP the seconds before it (45), after which a viewer run
# also waits up to VIEW_WAIT (300) for every bot to be drawn a window. A viewer run whose bots were
# not all drawn, or an idle or wanderers run whose gates were not open and loaded throughout (they
# are kept force-loaded, since nobody is near them; KEEP_LOADED=0 lets them go), is marked INVALID.
# JARS, space-separated, are what runs on the server: "none" for the control, a path (relative to
# the current folder), or label=path to compare a PR's jar with main's (default "none
# <repo>/target/WormholeXTreme.jar"). A label is letters, digits, dot, dash or underscore, once each.
# MC (1.21.11) picks the Paper version, fetched by fetch-server.sh the first time unless SERVER_JAR
# names one; JAVA, a path or a command on PATH, must be Java 21 or newer, else a JDK 21 found in the
# usual folders is used. The first run also installs the bots with npm ci.
# MOVES=still keeps viewers motionless; SPARK=1 also
# saves a spark profile of the ticks over 50 ms (a .sparkprofile, read at spark.lucko.me);
# WX_CONFIG, "key value" pairs split by commas, sets more of Wormhole's settings.
#
# The server is bigger than the boot tests': view-distance 8, simulation-distance 6, a fixed 4 GB heap.
# Everything goes in PERF_DIR (<repo>/.perf, ignored by git): runs/<time>/ holds a server folder and
# a JSON result per run, and the table, printed at the end, is results.md. One server at a time, the
# bots on the same machine: on a desktop, read differences between runs, not absolute numbers.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/.." && pwd)"
mc="${MC:-1.21.11}"
perf="${PERF_DIR:-$repo/.perf}"
scenarios="${SCENARIO:-viewers wanderers gateview}"
steps="${BOTS:-1 10}"
jars="${JARS:-none $repo/target/WormholeXTreme.jar}"
views="${GATE_VIEW:-behind open}"
funnels="${FUNNEL_MIN:-0 3}"
port="${BOOT_PORT:-25611}"
mkdir -p "$perf"
perf="$(cd "$perf" && pwd)"

java_bin="${JAVA:-}"
if [[ -z "$java_bin" ]]; then
  for candidate in "/c/Program Files/Eclipse Adoptium"/jdk-21*/bin/java.exe /usr/lib/jvm/*21*/bin/java \
    /usr/lib/jvm/*21*/bin/java.exe; do
    [[ -x "$candidate" ]] && { java_bin="$candidate"; break; }
  done
fi
[[ -n "$java_bin" ]] || { echo "no JDK 21 found; set JAVA to a Java 21 java" >&2; exit 2; }
# A bare command is looked up on PATH, and followed through links to the JDK it belongs to.
resolved="$(command -v "$java_bin" || true)"
[[ -n "$resolved" ]] || { echo "JAVA=$java_bin is neither a file nor a command on PATH" >&2; exit 2; }
java_bin="$(realpath "$resolved")"
# "1.8.0_503" is Java 8; "21.0.12" is Java 21.
java_major="$("$java_bin" -version 2>&1 | sed -n 's/.*version "\(1\.\)\{0,1\}\([0-9]*\).*/\2/p' | head -1)"
if [[ -z "$java_major" || "$java_major" -lt 21 ]]; then
  echo "JAVA=$java_bin is Java ${java_major:-of no version it would say}; Paper $mc needs 21 or newer" >&2
  exit 2
fi
javac_bin="$(dirname "$java_bin")/javac"
[[ -x "$javac_bin" || -x "$javac_bin.exe" ]] || { echo "no javac beside $java_bin: JAVA must be a JDK's, not a JRE's" >&2; exit 2; }

# Each jar's label names its runs, the tick log and the result files, so it must be plain and unique.
seen_labels=" "
for entry in $jars; do
  path=""
  if [[ "$entry" == none ]]; then label=none
  elif [[ "$entry" == *=* ]]; then label="${entry%%=*}"; path="${entry#*=}"
  else label="$(basename "$entry" .jar)"; path="$entry"
  fi
  [[ "$label" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "JARS: the label '$label' may hold only letters, digits, . _ -" >&2; exit 2; }
  [[ "$seen_labels" != *" $label "* ]] || { echo "JARS: the label '$label' is given twice; name them label=path" >&2; exit 2; }
  [[ -z "$path" || -f "$path" ]] || { echo "JARS: no file $path" >&2; exit 2; }
  seen_labels="$seen_labels$label "
done

server_jar="${SERVER_JAR:-$perf/paper-$mc.jar}"
[[ -f "$server_jar" ]] || bash "$here/fetch-server.sh" paper "$mc" "$server_jar"

# The measuring plugin, built once per version against the API paperclip unpacks, without starting.
tick_dir="$perf/perf-tick-$mc"
if [[ ! -f "$tick_dir/plugin/WxPerfTick.jar" || "$here/perf-tick/WxPerfTick.java" -nt "$tick_dir/plugin/WxPerfTick.jar" ]]; then
  rm -rf "$tick_dir"
  mkdir -p "$tick_dir/classes" "$tick_dir/plugin"
  (cd "$tick_dir" && "$java_bin" -Dpaperclip.patchonly=true -jar "$server_jar" > patch.log 2>&1)
  sep=":"
  [[ "$(uname -s)" == MINGW* || "$(uname -s)" == MSYS* ]] && sep=";"
  # In an argument file: a hundred jars is a long command line for Windows.
  (cd "$tick_dir" && printf -- '-cp\n%s\n' "$(find libraries -name '*.jar' | paste -sd "$sep")" > javac.args)
  # It uses only Bukkit's plugin, command and event classes and Paper's ServerTickEndEvent, all in
  # paper-api; the rest of the classpath is what those refer to. -proc:none quiets a note about
  # annotation processors found among the server's libraries.
  (cd "$tick_dir" && "$javac_bin" --release 21 -proc:none @javac.args -d classes "$here/perf-tick/WxPerfTick.java")
  cp "$here/perf-tick/plugin.yml" "$tick_dir/classes/"
  (cd "$tick_dir/classes" && "$(dirname "$java_bin")/jar" cf ../plugin/WxPerfTick.jar .)
fi

client="$here/player-test"
if [[ ! -d "$client/node_modules/mineflayer" ]]; then
  npm ci --prefix "$client" --no-audit --no-fund
fi

# Stopped by hand, take the run's server and bots down with it rather than leave them running.
child=""
kill_tree() {
  local pid
  for pid in $(ps -ef | awk -v p="$1" '$3 == p { print $2 }'); do kill_tree "$pid"; done
  kill "$1" 2>/dev/null || true
}
trap '[[ -n "$child" ]] && kill_tree "$child"; exit 130' INT TERM

run_dir="$perf/runs/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$run_dir"
echo "perf runs in $run_dir"

# One run: scenario, N, gate-view, funnel, jar entry.
run_one() {
  local scenario="$1" n="$2" view="$3" funnel="$4" entry="$5"
  local jar_label jar_path plugin=yes
  if [[ "$entry" == "none" ]]; then
    jar_label=none; jar_path=-; plugin=no
  elif [[ "$entry" == *=* ]]; then
    jar_label="${entry%%=*}"; jar_path="${entry#*=}"
  else
    jar_label="$(basename "$entry" .jar)"; jar_path="$entry"
  fi
  local tag="$scenario-n$n"
  [[ "$scenario" == gateview ]] && tag="$tag-$view-f$funnel"
  tag="$tag-$jar_label"
  local dir="$run_dir/$tag"
  mkdir -p "$dir"
  # Bukkit refuses a second join from one address within 4 s by default.
  printf 'settings:\n  connection-throttle: -1\n' > "$dir/bukkit.yml"
  echo "== $tag"
  local started=$SECONDS status=0
  JAVA="$java_bin" BOOT_DIR="$dir" BOOT_PORT="$port" BOOT_FLOOR=minecraft:grass_block \
    BOOT_HEAP=4G BOOT_JVM_ARGS="-Xms4G" SETTLE_SECONDS=2 START_TIMEOUT=300 \
    BOOT_PROPERTIES=$'view-distance=8\nsimulation-distance=6\nmax-players=200' \
    EXTRA_PLUGINS="$tick_dir/plugin" \
    BOOT_TEST_ALLOW="${BOOT_TEST_ALLOW:-No Vault/LuckPerms provider detected|Shapes framed in OBSIDIAN disagree}" \
    SCENARIO="$scenario" BOTS="$n" PLUGIN="$plugin" JAR_LABEL="$jar_label" LABEL="$tag" \
    GATE_VIEW="$view" FUNNEL_MIN="$funnel" PERF_RESULT="$run_dir/$tag.json" \
    BOOT_CLIENT="$(printf 'node %q %q' "$client/load.js" "$mc")" \
    bash "$here/boot-test.sh" "$server_jar" "$jar_path" > "$dir/run.log" 2>&1 &
  child=$!
  wait "$child" || status=$?
  child=""
  echo "   $((SECONDS - started))s, boot-test status $status$([[ -f "$run_dir/$tag.json" ]] || echo ', NO RESULT')"
  grep -h "INVALID" "$dir/run.log" | sed 's/^/   /' || true
  [[ "$status" -eq 0 ]] || tail -n 30 "$dir/run.log" | sed 's/^/   | /'
  if [[ "${SPARK:-0}" == 1 ]]; then cp "$dir"/plugins/spark/*.sparkprofile "$run_dir/" 2>/dev/null || true; fi
  # The world is several hundred MB; the log and the tick file are what a run leaves.
  [[ "${KEEP_WORLDS:-0}" == 1 ]] || rm -rf "$dir"/world "$dir"/world_nether "$dir"/world_the_end "$dir"/libraries "$dir"/versions "$dir"/cache
}

for scenario in $scenarios; do
  for n in $steps; do
    if [[ "$scenario" == gateview ]]; then
      for view in $views; do
        for funnel in $funnels; do
          for entry in $jars; do
            # The control has no gate view to vary: once is enough.
            if [[ "$entry" == none && ( "$view" != "${views%% *}" || "$funnel" != "${funnels%% *}" ) ]]; then continue; fi
            run_one "$scenario" "$n" "$view" "$funnel" "$entry"
          done
        done
      done
    else
      for entry in $jars; do run_one "$scenario" "$n" horizon 0 "$entry"; done
    fi
  done
done

node "$client/load.js" --table "$run_dir" | tee "$run_dir/results.md"
