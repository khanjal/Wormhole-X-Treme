#!/usr/bin/env bash
# Runs a Wormhole Research Facility lab in this terminal: a Paper server with the campus built and
# held for you to join (run-facility.js). The shell twin of lab.ps1, which opens its own window;
# open a terminal for this one. Works from a fresh clone: the facility's Node modules are
# installed the first time, run-facility.js builds the plugin (or takes -p), fetches Paper and
# picks the JDK. The world is kept between labs unless -f.
#
#   scripts/facility/lab.sh [-v version] [-P port] [-p plugin.jar] [-o ops] [-w with] [-c cache] [-f]
#
#   -v  Minecraft version (default 26.1.2)
#   -P  server port (default 25590; Dynmap's web map is at 8123 + port - 25590)
#   -p  a plugin jar to test instead of building this checkout
#   -o  players to op, comma-separated
#   -w  companion plugins, comma-separated (see companions.json)
#   -c  a folder of companion jars to read first
#   -f  a fresh world
#
# Say "stop" in the lab's chat, or press Ctrl+C, to end it.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
version=26.1.2
port=25590
args=()
fresh=0
while getopts 'v:P:p:o:w:c:f' opt; do
  case "$opt" in
    v) version="$OPTARG" ;;
    P) port="$OPTARG" ;;
    p) args+=(--plugin "$(cd "$(dirname "$OPTARG")" && pwd)/$(basename "$OPTARG")") ;;
    o) args+=(--op "$OPTARG") ;;
    w) args+=(--with "$OPTARG") ;;
    c) args+=(--plugin-cache "$(cd "$OPTARG" && pwd)") ;;
    f) fresh=1 ;;
    *) sed -n '2,20p' "$0"; exit 2 ;;
  esac
done
[ "$fresh" = 1 ] || args+=(--keep-world)

if [ ! -d "$here/node_modules" ]; then
  echo "Installing the facility's Node modules (once)..."
  npm install --prefix "$here"
fi

cd "$repo"
# ${args[@]+...}: an empty array under set -u is an error before bash 4.4 (-f alone leaves it empty).
exec node "$here/run-facility.js" "$version" --port "$port" ${args[@]+"${args[@]}"}
