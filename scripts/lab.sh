#!/usr/bin/env bash
# Starts a server with the lab built on a fresh world, for trying the plugin by hand:
#   lab.sh <server.jar> <plugin.jar> [minecraft-version]
# Join localhost:25599 with that version of Minecraft, under any name. Each bay's panel picks what
# to test; Run has the bot (player-test/lab.js) build it and make the trip while you watch. Say
# "stop" in chat to shut it down. See "The lab" in docs/DEVELOPMENT.md.
#
# The version defaults to 26.1.2, the newest Mineflayer 4.39 speaks; move it up when Mineflayer
# does. Node 22 or newer runs the bot. LAB_SELFTEST=1 has the bot work every panel itself and
# exit, failing if any run did (LAB_SELFTEST=gate,ring for only those bays). JAVA, BOOT_DIR and
# BOOT_PORT pass through to boot-test.sh. BOOT_DIR keeps the server folder, but use a new one each
# time: the lab is built on a fresh world.
set -euo pipefail

if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo "usage: lab.sh <server.jar> <plugin.jar> [minecraft-version]" >&2
  exit 2
fi
version="${3:-26.1.2}"
here="$(cd "$(dirname "$0")" && pwd)"
client="$here/player-test"

# Checks for the bot's own dependency, not the folder, so an install cut short is done again.
if [[ ! -d "$client/node_modules/mineflayer" ]]; then
  npm ci --prefix "$client" --no-audit --no-fund
fi

# Command blocks carry the panels' buttons, and the lab is wide, so the view reaches across a bay.
# The same warning allowlist as player-boot.sh.
BOOT_CLIENT="$(printf 'node %q %q' "$client/lab.js" "$version")" \
  BOOT_FLOOR="${BOOT_FLOOR:-minecraft:grass_block}" \
  BOOT_MEMORY="${BOOT_MEMORY:-2G}" \
  BOOT_PROPERTIES="$(printf '%s\n' 'enable-command-block=true' 'view-distance=8' 'simulation-distance=6' 'motd=Wormhole X-Treme lab')" \
  BOOT_TEST_ALLOW="${BOOT_TEST_ALLOW:-No Vault/LuckPerms provider detected|Shapes framed in OBSIDIAN disagree}" \
  SETTLE_SECONDS="${SETTLE_SECONDS:-5}" \
  bash "$here/boot-test.sh" "$1" "$2"
