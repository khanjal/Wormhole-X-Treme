#!/usr/bin/env bash
# Starts a real server with the plugin, joins it as a player, and takes that player through a gate,
# a beam and a ring, failing unless it comes out where it should each time:
#   player-boot.sh <server.jar> <plugin.jar> <minecraft-version>
# The player is a Mineflayer bot (player-test/journeys.js), so this needs Node 22 or newer and a
# version Mineflayer speaks: 1.20.1 to 1.21.11 and 26.1, as of 4.39. OBSERVE=1 waits for you to join
# localhost:25599 and watch, and asks you after each trip whether you saw it; see
# docs/DEVELOPMENT.md. JAVA, BOOT_DIR and BOOT_PORT pass through to boot-test.sh; the floor is grass
# unless BOOT_FLOOR says otherwise.
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "usage: player-boot.sh <server.jar> <plugin.jar> <minecraft-version>" >&2
  exit 2
fi
here="$(cd "$(dirname "$0")" && pwd)"
client="$here/player-test"

# Checks for the bot's own dependency, not the folder, so an install cut short is done again.
if [[ ! -d "$client/node_modules/mineflayer" ]]; then
  npm ci --prefix "$client" --no-audit --no-fund
fi

BOOT_CLIENT="$(printf 'node %q %q' "$client/journeys.js" "$3")" \
  BOOT_FLOOR="${BOOT_FLOOR:-minecraft:grass_block}" \
  BOOT_TEST_ALLOW="${BOOT_TEST_ALLOW:-No Vault/LuckPerms provider detected|Shapes framed in OBSIDIAN disagree}" \
  bash "$here/boot-test.sh" "$1" "$2"
