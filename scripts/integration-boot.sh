#!/usr/bin/env bash
# Boots the plugin beside LuckPerms, Vault, PlaceholderAPI, CoreProtect and EssentialsX, with each
# integration switched on, and requires every hook to say it attached:
#   integration-boot.sh <server.jar> <plugin.jar> <minecraft-version>
# PLUGINS_AS_OF, JAVA and BOOT_DIR pass through to fetch-plugins.sh and boot-test.sh.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
deps="$(mktemp -d)"
bash "$here/fetch-plugins.sh" "$3" "$deps"
has() { ! grep -qx "$1" "$deps/skipped.txt"; }

config=""
require=""
commands=""
add() { printf -v "$1" '%s%s\n' "${!1}" "$2"; }

if has luckperms; then
  add require 'Vault provider detected'
fi
if has placeholderapi; then
  add config 'placeholders-enabled: true'
  add require 'Registered PlaceholderAPI expansion'
  # An expansion that did not answer would echo the placeholder back unreplaced.
  add commands 'papi parse --null gates=%wormhole_gates_total%'
  add require 'gates=[0-9]+'
fi
if has coreprotect; then
  add config 'coreprotect-enabled: true'
  add require 'Enabling CoreProtect'
fi
if has essentialsx; then
  add config 'economy-enabled: true'
  add require 'Attached to Vault economy provider'
fi

if [[ -s "$deps/skipped.txt" ]]; then
  echo "not tested on $3: $(tr '\n' ' ' < "$deps/skipped.txt")"
fi
# The permission fallback warning is not allowed here: with LuckPerms and Vault it must not appear.
EXTRA_PLUGINS="$deps" BOOT_CONFIG="$config" BOOT_REQUIRE="$require" BOOT_COMMANDS="$commands" \
  BOOT_TEST_ALLOW="${BOOT_TEST_ALLOW:-Shapes framed in OBSIDIAN disagree}" \
  bash "$here/boot-test.sh" "$1" "$2"
