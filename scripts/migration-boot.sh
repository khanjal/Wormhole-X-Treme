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
# The folder is emptied first, so it has to be one a previous run made, or not exist yet.
if [[ -e "$dir" && -n "$(ls -A "$dir" 2>/dev/null)" && ! -f "$dir/eula.txt" ]]; then
  echo "BOOT_DIR $dir is not empty and not a previous server run; refusing to empty it" >&2
  exit 2
fi
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
  # The pair saved mid-trip must really be saved open, or "comes across shut" proves nothing.
  python3 - "$db" <<'EOF' || { echo "the enriched database has no gate saved open" >&2; exit 1; }
import sqlite3, sys
blob = sqlite3.connect(sys.argv[1]).execute("SELECT GateData FROM Stargates WHERE Name = 'Gallium'").fetchone()[0]
sys.exit(0 if blob[1 + 3 * 12 + 2 * 32 + 1 + 12 + 4 + 8] == 1 else 1)
EOF
  imported=81 # and the truncated one
  add require 'skipped Oxygen: '
fi

add commands 'wx gate import'
add commands 'wx gate import'
add require 'Found WormholeXTreme\.sqlite from an older Wormhole X-Treme'
add require "Imported $imported gates"
add require 'skipped Travel: world "world_tutorial" is not loaded'
add require 'skipped Protection: world "world_tutorial" is not loaded'
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
# Anchored, so a count that merely ends in these digits does not pass.
add require "\\] $imported Wormholes loaded"
if [[ "$variant" == "enriched" ]]; then
  add commands 'wx idc Silver'
  add require 'IDC for gate: Silver is:ag47'
fi
echo "== second boot: reload"
BOOT_COMMANDS="$commands" BOOT_REQUIRE="$require" bash "$here/boot-test.sh" "$1" "$2"
cp "$dir/console.log" "$dir/console-reload.log"

failures=()
files=0
# Guarded: with no gates at all there is no folder, and set -e would abort before saying why.
if [[ -d "$data/data/gates" ]]; then
  files=$(find "$data/data/gates" -name '*.yml' | wc -l)
fi
[[ "$files" -eq "$imported" ]] || failures+=("$files gate files written, not $imported")
if [[ "$variant" == "enriched" ]]; then
  grep -q '^Network: Traders' "$data/data/gates/Zinc.yml" || failures+=("Zinc lost its Traders network")
  grep -q '^WorldName: world_nether' "$data/data/gates/Cobalt.yml" || failures+=("Cobalt is not in world_nether")
  grep -q '^WorldName: world_the_end' "$data/data/gates/Xenon.yml" || failures+=("Xenon is not in world_the_end")
  # Vanadium's iris was saved shut and Potassium's open; each must come across that way.
  python3 - "$data/data/gates" <<'EOF' || failures+=("an iris state did not come across")
import base64, os, re, struct, sys
def iris(name):
    text = open(os.path.join(sys.argv[1], name + ".yml"), encoding="utf-8").read()
    blob = base64.b64decode(re.search(r"GateData:\s*(\S+)", text).group(1))
    at = 1 + 3 * 12 + 2 * 32 + 1 + 12 + 4 + 8 + 1 + 8
    at += 4 + struct.unpack(">i", blob[at:at + 4])[0]
    at += 4 + struct.unpack(">i", blob[at:at + 4])[0]
    return blob[at]
sys.exit(0 if (iris("Vanadium"), iris("Potassium")) == (1, 0) else 1)
EOF
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
  if ! PLUGINS=coreprotect bash "$here/fetch-plugins.sh" "$4" "$deps"; then
    echo "::notice::could not fetch CoreProtect for $4; its hook is not tested here"
  elif compgen -G "$deps/CoreProtect*.jar" > /dev/null; then
    echo "== third boot: take a gate down beside CoreProtect"
    EXTRA_PLUGINS="$deps" BOOT_CONFIG='coreprotect-enabled: true' BOOT_COMMANDS=$'wx gate regen Vanadium -fill\nwx gate remove Vanadium -destroy' \
      BOOT_REQUIRE=$'Logging gate and ring construction to CoreProtect\nWormhole Removed: Vanadium' \
      bash "$here/boot-test.sh" "$1" "$2" || failures+=("the CoreProtect boot failed")
    # The hook line appears on the first change, which regen makes; the removals are what -destroy logs.
    python3 - "$dir/plugins/CoreProtect/database.db" <<'EOF' || failures+=("CoreProtect holds no removals from the plugin")
import sqlite3, sys
db = sqlite3.connect(sys.argv[1])
removed = db.execute("SELECT COUNT(*) FROM co_block b JOIN co_user u ON u.id = b.user"
                     " WHERE u.user = '#wormhole' AND b.action = 0").fetchone()[0]
sys.exit(0 if removed > 0 else 1)
EOF
  else
    echo "::notice::no CoreProtect release for $4; its hook is not tested here"
  fi
fi
if [[ ${#failures[@]} -gt 0 ]]; then
  printf 'migration FAILED\n' >&2
  printf -- '- %s\n' "${failures[@]}" >&2
  exit 1
fi
echo "migration passed ($variant): $imported gates imported, reloaded, and nothing imported twice"
