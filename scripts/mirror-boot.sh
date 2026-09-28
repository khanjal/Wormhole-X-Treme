#!/usr/bin/env bash
# Makes a quantum mirror from the console, then restarts the server and checks it is still there:
#   mirror-boot.sh <server.jar> <plugin.jar>
# Only a player's click sends anyone through a mirror, so this checks making and keeping one, not
# travel. JAVA and BOOT_DIR pass through to boot-test.sh.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
dir="${BOOT_DIR:-$(mktemp -d)}"
# The folder is emptied first, so it has to be one a previous run made, or not exist yet.
if [[ -e "$dir" && -n "$(ls -A "$dir" 2>/dev/null)" && ! -f "$dir/eula.txt" ]]; then
  echo "BOOT_DIR $dir is not empty and not a previous server run; refusing to empty it" >&2
  exit 2
fi
rm -rf "$dir"
mkdir -p "$dir"
export BOOT_DIR="$(cd "$dir" && pwd)"
export BOOT_TEST_ALLOW="${BOOT_TEST_ALLOW:-No Vault/LuckPerms provider detected|Shapes framed in OBSIDIAN disagree}"

# A white wall banner facing south on a stone wall five wide, from the bedrock at -64 up to -59: a
# block of wall out on every side of the opening, and two, so it is not warned about as thin. The wall
# goes up first, since a wall banner with nothing behind it drops. Its room is the block in front.
# The chat colours around names may reach the log as codes or not at all, hence the .{0,12}.
# Loaded first: there are no always-loaded spawn chunks from 1.21.9, and fill refuses an unloaded one.
commands='forceload add 0 16 15 31
sleep 2
fill 0 -63 29 4 -59 29 minecraft:stone
setblock 2 -61 30 minecraft:white_wall_banner[facing=south]
wx mirror create museum world 2 -61 30
wx mirror list'
require='Mirror .{0,12}museum.{0,12} is this banner
museum.{0,12} -- .{0,12}world.{0,12} -> its own reflection'
echo "== first boot: make the mirror"
BOOT_COMMANDS="$commands" BOOT_REQUIRE="$require" bash "$here/boot-test.sh" "$1" "$2"
cp "$BOOT_DIR/console.log" "$BOOT_DIR/console-create.log"

require='Loaded 1 quantum mirror
museum.{0,12} -- .{0,12}world.{0,12} -> its own reflection'
echo "== second boot: the mirror is still there"
BOOT_COMMANDS='wx mirror list' BOOT_REQUIRE="$require" bash "$here/boot-test.sh" "$1" "$2"
cp "$BOOT_DIR/console.log" "$BOOT_DIR/console-reload.log"
echo "mirror passed: made from the console, and kept across a restart"
