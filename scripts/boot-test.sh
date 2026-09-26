#!/usr/bin/env bash
# Starts a real server with the plugin and fails on anything short of a clean enable, run and stop:
#   boot-test.sh <server.jar> <plugin.jar>
# JAVA picks the runtime (default: java on PATH); BOOT_DIR keeps the server folder for inspection.
set -uo pipefail

server_jar="$(realpath "$1")"
plugin_jar="$(realpath "$2")"
java_bin="${JAVA:-java}"
start_timeout="${START_TIMEOUT:-300}"
settle_seconds="${SETTLE_SECONDS:-15}"
dir="${BOOT_DIR:-$(mktemp -d)}"

mkdir -p "$dir/plugins"
dir="$(cd "$dir" && pwd)"
cp "$plugin_jar" "$dir/plugins/"
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

cd "$dir"
tail -f commands.txt | "$java_bin" -Xmx1G -DIReallyKnowWhatIAmDoingISwear=true -Dterminal.jline=false -Dterminal.ansi=false \
  -jar "$server_jar" nogui > "$log" 2>&1 &
server_pid=$!
cleanup() { pkill -P $$ tail 2>/dev/null; kill "$server_pid" 2>/dev/null; }
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
  sleep "$settle_seconds"
  send "stop"
  for ((i = 0; i < 120; i++)); do
    kill -0 "$server_pid" 2>/dev/null || break
    sleep 1
  done
fi

# A stopped server still leaves the piped tail waiting; one more write ends it.
send ""
stopped=0
kill -0 "$server_pid" 2>/dev/null || stopped=1

failures=()
[[ $started -eq 1 ]] || failures+=("server did not finish starting within ${start_timeout}s")
grep -q 'Enable Completed' "$log" || failures+=("the plugin never logged Enable Completed")
[[ $stopped -eq 1 ]] || failures+=("server did not exit within 120s of stop")
[[ $started -eq 1 ]] && ! grep -q 'Disabling WormholeXTreme' "$log" && failures+=("the plugin was never disabled")

# onEnable catches its own failures and logs them, so a warning is the failure signal, not an exception escaping.
pattern='(WARN|ERROR|SEVERE)\]:? .*(WormholeXTreme|wormhole_xtreme)|^\s+at com\.wormhole_xtreme|Could not load .plugins/|Error occurred while (enabling|disabling)'
if [[ -n "${BOOT_TEST_ALLOW:-}" ]]; then
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
