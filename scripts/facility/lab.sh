#!/usr/bin/env bash
# Runs a Wormhole Research Facility lab in this terminal: a Paper server with the campus built and
# held for you to join (run-facility.js). The shell twin of lab.ps1, which opens its own window;
# open a terminal for this one. Works from a fresh clone: the facility's Node modules are
# installed the first time, run-facility.js builds the plugin (or takes -p), fetches Paper and
# picks the JDK. The world is kept between labs unless -f.
#
#   scripts/facility/lab.sh [-v version] [-P port] [-p plugin.jar] [-o ops] [-w with] [-c cache] [-f]
#   scripts/facility/lab.sh -d [-e [-f] | -k] [-O] [-P port] [-p plugin.jar] [-o ops] [-w with] [-c cache]
#   scripts/facility/lab.sh -W [-n name] [-q] [-C cells] [-v version] [-P port] [-p plugin.jar] [-w with] [-c cache]
#
#   -v  Minecraft version (default 26.1.2)
#   -P  server port (default 25590; Dynmap's web map is at 8123 + port - 25590)
#   -p  a plugin jar to test instead of building this checkout
#   -o  players to op, comma-separated
#   -w  companion plugins, comma-separated (see companions.json)
#   -c  a folder of companion jars to read first
#   -f  a fresh world
#   -d  design mode (design/facility/BRIEF.md): 1.21.11 with WorldEdit, its own world kept between
#       sessions, ops in creative, no tests; say check, export or stop in chat. -v and -f do not apply
#   -e  with -d and the lab stopped: write the export zip to .local-server/exports/, then stop;
#       with -f the zip holds the worlds too
#   -k  with -d and the lab stopped: run the keep-clear check, then stop
#   -O  with -d: let other machines join (online-mode is off: anyone who reaches the port can join
#       under any name, an op's too)
#   -W  watch the self-test from inside the lab: a fresh world; it waits for you to join, then runs
#       with you as a spectator moved to each chamber and told each cell and its result (--watch)
#   -n  with -W: the name you will join as (default: whoever joins first)
#   -q  with -W: the short matrix (about 15 minutes)
#   -C  with -W: only the matrix cells whose names match (a|b, ^start, end$)
#
# Say "stop" in the lab's chat, or press Ctrl+C, to end it. Unlike lab.ps1 it does not start the
# Lab Dashboard; run `node scripts/facility/dashboard.js` for it (http://127.0.0.1:8200).
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
version=26.1.2
port=25590
args=()
fresh=0
design=
job=
open=
watch=
name=
quick=
cells=
while getopts 'v:P:p:o:w:c:fdekOWn:qC:' opt; do
  case "$opt" in
    v) version="$OPTARG" ;;
    P) port="$OPTARG" ;;
    p) args+=(--plugin "$(cd "$(dirname "$OPTARG")" && pwd)/$(basename "$OPTARG")") ;;
    o) args+=(--op "$OPTARG") ;;
    w) args+=(--with "$OPTARG") ;;
    c) args+=(--plugin-cache "$(cd "$OPTARG" && pwd)") ;;
    f) fresh=1 ;;
    d) design=--design ;;
    e) [ -z "$job" ] || { echo '-e or -k, one at a time' >&2; exit 2; }; job=--design-export ;;
    k) [ -z "$job" ] || { echo '-e or -k, one at a time' >&2; exit 2; }; job=--design-check ;;
    O) open=--design-open ;;
    W) watch=--watch ;;
    n) name="$OPTARG" ;;
    q) quick=--quick ;;
    C) cells="$OPTARG" ;;
    *) sed -n '2,31p' "$0"; exit 2 ;;
  esac
done
if { [ -n "$job" ] || [ -n "$open" ]; } && [ -z "$design" ]; then echo '-e, -k and -O go with -d' >&2; exit 2; fi
if { [ -n "$name" ] || [ -n "$quick" ] || [ -n "$cells" ]; } && [ -z "$watch" ]; then echo '-n, -q and -C go with -W' >&2; exit 2; fi
if [ -n "$watch" ] && [ -n "$design" ]; then echo 'design mode runs no tests: -W or -d' >&2; exit 2; fi
if [ -n "$design" ]; then
  # With -e, -f is a full export (the worlds too); otherwise design mode keeps its world.
  if [ "$fresh" = 1 ]; then
    [ "$job" = --design-export ] || { echo 'Design mode keeps your world: to start over, delete .local-server/design-1.21.11 yourself' >&2; exit 2; }
    args+=(--full)
  fi
  version=1.21.11
  args+=("${job:-$design}")
  [ -z "$open" ] || args+=("$open")
elif [ -n "$watch" ]; then
  # A self-test starts from a fresh world: its first checks are that every cell is as built.
  args+=(--selftest --watch)
  [ -z "$name" ] || args+=("$name")
  [ -z "$quick" ] || args+=("$quick")
  [ -z "$cells" ] || args+=(--cells "$cells")
else
  [ "$fresh" = 1 ] || args+=(--keep-world)
fi

# The lock file's copy goes in only after npm succeeds (lab.ps1 checks the same one), so a missing
# or different one means an install that never finished, or a newer lock: npm ci starts over.
if ! cmp -s "$here/package-lock.json" "$here/node_modules/.facility-lock.json"; then
  # npm ci deletes node_modules first, from under a running lab. No pgrep in git-bash: no check there.
  if command -v pgrep >/dev/null && pgrep -f 'run-facility\.js' >/dev/null; then
    echo "The facility's Node modules need reinstalling, but a lab may be running from them: stop it first." >&2
    exit 1
  fi
  echo "Installing the facility's Node modules..."
  npm ci --prefix "$here"
  cp "$here/package-lock.json" "$here/node_modules/.facility-lock.json"
fi

cd "$repo"
# ${args[@]+...}: an empty array under set -u is an error before bash 4.4 (-f alone leaves it empty).
[ -z "$design" ] || exec node "$here/run-facility.js" --port "$port" ${args[@]+"${args[@]}"}
exec node "$here/run-facility.js" "$version" --port "$port" ${args[@]+"${args[@]}"}
