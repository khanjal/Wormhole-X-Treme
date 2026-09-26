#!/usr/bin/env bash
# Starts a real server with the plugin and fails on anything short of a clean enable, run and stop:
#   boot-test.sh <server.jar> <plugin.jar>
# JAVA picks the runtime (default: java on PATH); BOOT_DIR keeps the server folder for inspection.
# Optional: EXTRA_PLUGINS, a folder of jars installed alongside; BOOT_CONFIG, lines for the plugin's
# config.yml before first start; BOOT_COMMANDS and BOOT_REQUIRE, one console command or log regex a line.
set -uo pipefail

if [[ $# -ne 2 || ! -f "$1" || ! -f "$2" ]]; then
  echo "usage: boot-test.sh <server.jar> <plugin.jar>, both existing files (got: $*)" >&2
  exit 2
fi
server_jar="$(realpath "$1")"
plugin_jar="$(realpath "$2")"
java_bin="${JAVA:-java}"
start_timeout="${START_TIMEOUT:-300}"
settle_seconds="${SETTLE_SECONDS:-15}"
dir="${BOOT_DIR:-$(mktemp -d)}"

mkdir -p "$dir/plugins"
dir="$(cd "$dir" && pwd)"
rm -f "$dir"/plugins/*.jar
cp "$plugin_jar" "$dir/plugins/"
if [[ -n "${EXTRA_PLUGINS:-}" ]]; then
  cp "$EXTRA_PLUGINS"/*.jar "$dir/plugins/"
fi
if [[ -n "${BOOT_CONFIG:-}" ]]; then
  mkdir -p "$dir/plugins/WormholeXTreme"
  printf '%s\n' "$BOOT_CONFIG" > "$dir/plugins/WormholeXTreme/config.yml"
fi
echo "eula=true" > "$dir/eula.txt"
cat > "$dir/server.properties" <<'EOF'
online-mode=false
server-port=25599
level-type=minecraft\:flat
generator-settings={"layers"\:[{"block"\:"minecraft\:bedrock","height"\:1}],"biome"\:"minecraft\:plains"}
generate-structures=false
spawn-protection=0
view-distance=3
simulation-distance=3
EOF
: > "$dir/commands.txt"
log="$dir/console.log"

send() { echo "$1" >> "$dir/commands.txt"; }

cd "$dir" || exit 2
rm -f tail.pid
{ tail -f commands.txt & echo $! > tail.pid; wait; } | "$java_bin" -Xmx1G -DIReallyKnowWhatIAmDoingISwear=true \
  -Dterminal.jline=false -Dterminal.ansi=false -jar "$server_jar" nogui > "$log" 2>&1 &
server_pid=$!
cleanup() { [[ -f tail.pid ]] && kill "$(cat tail.pid)" 2>/dev/null; kill "$server_pid" 2>/dev/null; }
trap cleanup EXIT

started=0
for ((i = 0; i < start_timeout; i++)); do
  if grep -q 'Done (' "$log"; then started=1; break; fi
  kill -0 "$server_pid" 2>/dev/null || break
  sleep 1
done

if [[ $started -eq 1 ]]; then
  # The repeating tasks first run 20 to 100 ticks after enable, so give them time to throw.
  send "wormhole"
  send "wx list"
  while IFS= read -r command; do
    [[ -n "$command" ]] && send "$command"
  done <<< "${BOOT_COMMANDS:-}"
  sleep "$settle_seconds"
  send "stop"
  for ((i = 0; i < 120; i++)); do
    kill -0 "$server_pid" 2>/dev/null || break
    sleep 1
  done
fi

stopped=0
kill -0 "$server_pid" 2>/dev/null || stopped=1

failures=()
[[ $started -eq 1 ]] || failures+=("server did not finish starting within ${start_timeout}s")
grep -q 'Enable Completed' "$log" || failures+=("the plugin never logged Enable Completed")
[[ $started -eq 0 || $stopped -eq 1 ]] || failures+=("server did not exit within 120s of stop")
[[ $started -eq 1 ]] && ! grep -q 'Disabling WormholeXTreme' "$log" && failures+=("the plugin was never disabled")
while IFS= read -r required; do
  [[ -z "$required" ]] || grep -qE "$required" "$log" || failures+=("never logged: $required")
done <<< "${BOOT_REQUIRE:-}"

# onEnable catches its own failures and logs them, so a warning is the failure signal, not an exception escaping.
pattern='(WARN|ERROR|SEVERE)\]:? .*(WormholeXTreme|wormhole_xtreme)|^\s+at com\.wormhole_xtreme|Could not load .plugins/|Error occurred while (enabling|disabling)'
if [[ -n "${BOOT_TEST_ALLOW:-}" ]]; then
  # A broken or match-everything allowlist would hide every warning and pass.
  echo | grep -Eq "$BOOT_TEST_ALLOW"
  allow_check=$?
  if [[ $allow_check -ne 1 ]]; then
    echo "BOOT_TEST_ALLOW is not a usable regex, or matches every line: $BOOT_TEST_ALLOW" >&2
    exit 2
  fi
  flagged="$(grep -E "$pattern" "$log" | grep -Ev "$BOOT_TEST_ALLOW")"
else
  flagged="$(grep -E "$pattern" "$log")"
fi
[[ -z "$flagged" ]] || failures+=("warnings or stack frames from the plugin:"$'\n'"$flagged")

if [[ ${#failures[@]} -eq 0 ]]; then
  echo "boot test passed: $(grep -o 'Done ([^)]*)' "$log" | head -1), Enable Completed, clean stop"
  exit 0
fi
printf 'boot test FAILED\n' >&2
printf -- '- %s\n' "${failures[@]}" >&2
printf '\nlast 80 lines of %s:\n' "$log" >&2
tail -n 80 "$log" >&2
exit 1
