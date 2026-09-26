#!/usr/bin/env bash
# Upgrades a server from a real 2016 plugin folder and checks every gate comes across and stays:
#   migration-boot.sh <server.jar> <plugin.jar> [real|enriched] [minecraft-version]
# "enriched" first gives the fixture's database the states it lacks (see enrich-legacy-db.py). The
# first boot imports, the second reloads what the first wrote. Given the Minecraft version, a real
# run then takes a gate down beside CoreProtect. JAVA and BOOT_DIR pass through.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
variant="${3:-real}"
dir="${BOOT_DIR:-$(mktemp -d)}"
rm -rf "$dir"
mkdir -p "$dir/plugins"
dir="$(cd "$dir" && pwd)"
data="$dir/plugins/WormholeXTreme"
cp -r "$here/fixtures/legacy-2016" "$data"

# Sign-dial gates look for their signs in the world they were built in, which this flat one is not.
export BOOT_TEST_ALLOW='Shapes framed in OBSIDIAN disagree|No Vault/LuckPerms provider detected|Unable to get sign for stargate'
export BOOT_DIR="$dir"
# Two of the 84 gates are in a world_tutorial the test server does not have.
imported=82
commands=""
require=""
add() { printf -v "$1" '%s%s\n' "${!1}" "$2"; }

if [[ "$variant" == "enriched" ]]; then
  db="$data/WormholeXTremeDB/WormholeXTreme.sqlite"
  python3 "$here/enrich-legacy-db.py" "$here/fixtures/legacy-2016/WormholeXTremeDB/WormholeXTreme.sqlite" "$db"
  imported=81 # and the truncated one
  add require 'skipped Oxygen: '
fi

add commands 'wx gate import'
add commands 'wx gate import'
add require 'Found WormholeXTreme\.sqlite from an older Wormhole X-Treme'
add require "Imported $imported gates"
add require 'skipped Travel: world "world_tutorial" is not loaded'
# The second import must add nothing: the importer promises not to double up.
add require 'Imported 0 gates'
if [[ "$variant" == "enriched" ]]; then
  add commands 'wx idc Vanadium'
  add commands 'wx idc Potassium'
  add require 'IDC for gate: Vanadium is:vanadium23'
  add require 'IDC for gate: Potassium is:k19'
fi
echo "== first boot: import ($variant)"
BOOT_COMMANDS="$commands" BOOT_REQUIRE="$require" bash "$here/boot-test.sh" "$1" "$2"
cp "$dir/console.log" "$dir/console-import.log"

commands=""
require=""
add require "$imported Wormholes loaded"
if [[ "$variant" == "enriched" ]]; then
  add commands 'wx idc Silver'
  add require 'IDC for gate: Silver is:ag47'
fi
echo "== second boot: reload"
BOOT_COMMANDS="$commands" BOOT_REQUIRE="$require" bash "$here/boot-test.sh" "$1" "$2"

failures=()
files=$(find "$data/data/gates" -name '*.yml' | wc -l)
[[ "$files" -eq "$imported" ]] || failures+=("$files gate files written, not $imported")
if [[ "$variant" == "enriched" ]]; then
  grep -q '^Network: Traders' "$data/data/gates/Zinc.yml" || failures+=("Zinc lost its Traders network")
  grep -q '^WorldName: world_nether' "$data/data/gates/Cobalt.yml" || failures+=("Cobalt is not in world_nether")
  grep -q '^WorldName: world_the_end' "$data/data/gates/Xenon.yml" || failures+=("Xenon is not in world_the_end")
  # Saved open mid-trip in 2016; it must come across shut, not stuck open or pointing at nothing.
  for gate in Gallium Manganese; do
    python3 - "$data/data/gates/$gate.yml" <<'EOF' || failures+=("$gate came across open")
import base64, re, sys
blob = base64.b64decode(re.search(r"GateData:\s*(\S+)", open(sys.argv[1], encoding="utf-8").read()).group(1))
sys.exit(blob[1 + 3 * 12 + 2 * 32 + 1 + 12 + 4 + 8])
EOF
  done
fi

# CoreProtect's hook connects on the first block change it is given. Without a player, regen and
# then remove make one; remove alone, on a gate with nothing standing in this world, does not.
# Last, because it removes a gate the checks above count.
if [[ ${#failures[@]} -eq 0 && "$variant" == "real" && -n "${4:-}" ]]; then
  deps="$(mktemp -d)"
  bash "$here/fetch-plugins.sh" "$4" "$deps" > /dev/null
  if compgen -G "$deps/CoreProtect*.jar" > /dev/null; then
    mkdir -p "$deps/only" && cp "$deps"/CoreProtect*.jar "$deps/only/"
    echo "== third boot: take a gate down beside CoreProtect"
    EXTRA_PLUGINS="$deps/only" BOOT_CONFIG='coreprotect-enabled: true' BOOT_COMMANDS=$'wx gate regen Vanadium -fill\nwx gate remove Vanadium -destroy' \
      BOOT_REQUIRE=$'Logging gate and ring construction to CoreProtect\nWormhole Removed: Vanadium' \
      bash "$here/boot-test.sh" "$1" "$2" || failures+=("the CoreProtect boot failed")
  else
    echo "no CoreProtect release for $4; its hook is not tested here"
  fi
fi
if [[ ${#failures[@]} -gt 0 ]]; then
  printf 'migration FAILED\n' >&2
  printf -- '- %s\n' "${failures[@]}" >&2
  exit 1
fi
echo "migration passed ($variant): $imported gates imported, reloaded, and nothing imported twice"
