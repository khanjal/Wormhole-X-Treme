#!/usr/bin/env bash
# Builds two gates from the console, dials one to the other, and sends an item and a pig through:
#   travel-boot.sh <server.jar> <plugin.jar> <minecraft-version>
# Gate travel with no player on the server. JAVA and BOOT_DIR pass through to boot-test.sh.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"

# Item data moved from NBT's Count to components' count in 1.20.5.
item='{Item:{id:"minecraft:diamond",count:1}}'
if printf '%s\n' "$3" "1.20.4" | sort -V -C; then
  item='{Item:{id:"minecraft:diamond",Count:1b}}'
fi

# Standard gates with their DHD buttons hung on these blocks, facing south. Where their openings and
# arrival points fall is what `gate build` reports: Abydos opens around -1.5 -57.5 -2.5, and Chulak
# takes arrivals at 18.5 -60 -1.5. The flat test world's ground is at -64. Within six blocks of that
# point, because what comes through keeps moving and lands further off on some versions (1.20.4
# drops an item about 3.7 blocks away); Abydos is twenty blocks off, so nothing left there counts.
# Sent in 13 seconds after the dial, once the wormhole has formed: until the kawoosh, about ten
# seconds in, the opening is an empty frame, and a pig dropped into it could wander out first.
# Asked three times, at 4, 9 and 15 seconds after that: a busy runner can fall seconds behind, and
# the wormhole stays open 38. Any one answer is enough.
# Last, where the item is, for a run that fails: landed too far off, or never crossed.
commands="wx gate build Standard Abydos world 0 -60 0 south
wx gate build Standard Chulak world 20 -60 0 south
wx gate dial Abydos Chulak
sleep 13
summon item -1.5 -57.5 -2.5 $item
summon pig -1.5 -58.5 -2.5
sleep 4
execute if entity @e[type=item,x=18.5,y=-60,z=-1.5,distance=..6] run say ITEM_ARRIVED
execute if entity @e[type=pig,x=18.5,y=-60,z=-1.5,distance=..12] run say PIG_ARRIVED
sleep 5
execute if entity @e[type=item,x=18.5,y=-60,z=-1.5,distance=..6] run say ITEM_ARRIVED
execute if entity @e[type=pig,x=18.5,y=-60,z=-1.5,distance=..12] run say PIG_ARRIVED
sleep 6
execute if entity @e[type=item,x=18.5,y=-60,z=-1.5,distance=..6] run say ITEM_ARRIVED
execute if entity @e[type=pig,x=18.5,y=-60,z=-1.5,distance=..12] run say PIG_ARRIVED
data get entity @e[type=item,limit=1] Pos"

require='Built Abydos at 0 -60 0 in world\. Opening centred on -1\.5 -57\.5 -2\.5; arrivals at -1\.5 -60\.0 -1\.5
Built Chulak at 20 -60 0 in world\. Opening centred on 18\.5 -57\.5 -2\.5; arrivals at 18\.5 -60\.0 -1\.5
Stargates connected
ITEM_ARRIVED
PIG_ARRIVED'

BOOT_COMMANDS="$commands" BOOT_REQUIRE="$require" \
  BOOT_TEST_ALLOW="${BOOT_TEST_ALLOW:-No Vault/LuckPerms provider detected|Shapes framed in OBSIDIAN disagree}" \
  bash "$here/boot-test.sh" "$1" "$2"
