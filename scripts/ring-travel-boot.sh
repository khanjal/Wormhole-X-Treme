#!/usr/bin/env bash
# Pairs two transport rings from the console, fires them, and sends an item and a pig through:
#   ring-travel-boot.sh <server.jar> <plugin.jar> <minecraft-version>
# Ring travel with no player on the server. JAVA and BOOT_DIR pass through to boot-test.sh.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
dir="${BOOT_DIR:-$(mktemp -d)}"
# Emptied first: a world left from an earlier run still holds its pair and whatever arrived, which
# would answer for this one. So it has to be a previous run's folder, or not exist yet.
if [[ -e "$dir" && -n "$(ls -A "$dir" 2>/dev/null)" && ! -f "$dir/eula.txt" ]]; then
  echo "BOOT_DIR $dir is not empty and not a previous server run; refusing to empty it" >&2
  exit 2
fi
rm -rf "$dir"
mkdir -p "$dir"
export BOOT_DIR="$(cd "$dir" && pwd)"

# Item data moved from NBT's Count to components' count in 1.20.5.
item='{Item:{id:"minecraft:diamond",count:1}}'
if printf '%s\n' "$3" "1.20.4" | sort -V -C; then
  item='{Item:{id:"minecraft:diamond",Count:1b}}'
fi

# Two seven-wide circles of slabs, twenty blocks apart, on stone laid over the flat world's bedrock
# at -64. The stone lifts them a block, so the countdown lights, drawn two blocks under a floor ring,
# fall at -64 rather than below the world.
ring() {
  local z="$1" slab='minecraft:smooth_stone_slab[type=bottom]'
  printf '%s\n' \
    "fill -3 -63 $((z - 3)) 3 -63 $((z + 3)) minecraft:stone" \
    "fill -1 -62 $((z - 3)) 1 -62 $((z - 3)) $slab" \
    "fill -1 -62 $((z + 3)) 1 -62 $((z + 3)) $slab" \
    "fill -3 -62 $((z - 1)) -3 -62 $((z + 1)) $slab" \
    "fill 3 -62 $((z - 1)) 3 -62 $((z + 1)) $slab" \
    "setblock -2 -62 $((z - 2)) $slab" \
    "setblock 2 -62 $((z - 2)) $slab" \
    "setblock -2 -62 $((z + 2)) $slab" \
    "setblock 2 -62 $((z + 2)) $slab"
}

# The pair is fired by a block inside it, as a map's command block would, since its id is random.
# A cycle counts down five seconds, then flashes what is inside to the other end about eight seconds
# in; what arrives stands at the far pad's centre, 0.5 -62 20.5. Asked three times, as travel-boot.sh
# asks, for a runner that falls behind. The pig has no AI, so it cannot walk out before the flash.
arrived="execute if entity @e[type=item,x=0.5,y=-62,z=20.5,distance=..3] run say ITEM_ARRIVED
execute if entity @e[type=pig,x=0.5,y=-62,z=20.5,distance=..3] run say PIG_ARRIVED"
# Loaded first: there are no always-loaded spawn chunks from 1.21.9, and fill refuses an unloaded one.
commands="forceload add -16 -16 15 31
sleep 2
$(ring 0)
$(ring 20)
wx ring build world 0 -62 0 0 -62 20
summon item 0.5 -62 0.5 $item
summon pig 1.5 -62 0.5 {NoAI:1b}
wx ring fire world 0 -62 0
sleep 10
$arrived
sleep 4
$arrived
sleep 6
$arrived"

require='Ring pair [0-9a-f]{8} is live and public\. Arrivals at 0\.5 -62 0\.5 and 0\.5 -62 20\.5
Ring pair [0-9a-f]{8} is counting down
ITEM_ARRIVED
PIG_ARRIVED'

BOOT_COMMANDS="$commands" BOOT_REQUIRE="$require" \
  BOOT_TEST_ALLOW="${BOOT_TEST_ALLOW:-No Vault/LuckPerms provider detected|Shapes framed in OBSIDIAN disagree}" \
  bash "$here/boot-test.sh" "$1" "$2"
