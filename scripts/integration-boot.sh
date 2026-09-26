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

# Vault registers its own SuperPerms fallback, and an economy as soon as Essentials is on the
# classpath, so our hook's lines appear even when LuckPerms or EssentialsX failed; vault-info names
# the provider Vault actually settled on.
add commands 'vault-info'
if has luckperms; then
  add require 'Vault provider detected'
  add require 'Permission: LuckPerms \['
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
  # Coexistence only: our hook connects on the first block change, which nothing makes here.
  # migration-boot.sh takes a gate down beside CoreProtect to exercise it.
  # Not "Enabling CoreProtect": the server prints that before CoreProtect's own startup runs.
  add require 'CoreProtect.* has been successfully enabled'
fi
if has essentialsx; then
  add config 'economy-enabled: true'
  add require 'Attached to Vault economy provider'
  add require 'Economy: EssentialsX Economy \['
fi

if [[ $(wc -l < "$deps/skipped.txt") -ge 4 ]]; then
  echo "no integration plugin has a release for $3, so there is nothing to test" >&2
  exit 1
fi
if [[ -s "$deps/skipped.txt" ]]; then
  # A GitHub annotation, so a partly untested leg shows on the run's summary and not only in its log.
  echo "::warning::not tested on $3: $(tr '\n' ' ' < "$deps/skipped.txt")"
fi
# The permission fallback warning is not allowed here: with LuckPerms and Vault it must not appear.
EXTRA_PLUGINS="$deps" BOOT_CONFIG="$config" BOOT_REQUIRE="$require" BOOT_COMMANDS="$commands" \
  BOOT_TEST_ALLOW="${BOOT_TEST_ALLOW:-Shapes framed in OBSIDIAN disagree}" \
  bash "$here/boot-test.sh" "$1" "$2"
