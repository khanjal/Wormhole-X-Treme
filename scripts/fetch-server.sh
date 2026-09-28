#!/usr/bin/env bash
# Downloads a server jar for the boot test: fetch-server.sh <paper|purpur|spigot|craftbukkit> <version> <out.jar>
# Spigot and CraftBukkit have no download; BuildTools compiles them, which takes several minutes.
set -euo pipefail

kind="$1"
version="$2"
out="$3"
ua="WormholeXTreme-boot-test (https://github.com/khanjal/Wormhole-X-Treme)"
# Git Bash on Windows may have only python.
py="$(command -v python3 || command -v python)"
get() { curl -fsS --retry 3 --retry-all-errors -A "$ua" "$@"; }
mkdir -p "$(dirname "$out")"

case "$kind" in
  paper)
    meta="$(get "https://fill.papermc.io/v3/projects/paper/versions/$version/builds/latest")"
    url="$(printf '%s' "$meta" | "$py" -c 'import json,sys; print(json.load(sys.stdin)["downloads"]["server:default"]["url"])')"
    sum="$(printf '%s' "$meta" | "$py" -c 'import json,sys; print(json.load(sys.stdin)["downloads"]["server:default"]["checksums"]["sha256"])')"
    get -o "$out" "$url"
    echo "$sum  $out" | sha256sum -c --quiet
    ;;
  purpur)
    meta="$(get "https://api.purpurmc.org/v2/purpur/$version/latest")"
    sum="$(printf '%s' "$meta" | "$py" -c 'import json,sys; print(json.load(sys.stdin)["md5"])')"
    get -o "$out" "https://api.purpurmc.org/v2/purpur/$version/latest/download"
    echo "$sum  $out" | md5sum -c --quiet
    ;;
  spigot|craftbukkit)
    work="$(mktemp -d)"
    get -o "$work/BuildTools.jar" \
      "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar"
    (cd "$work" && java -jar BuildTools.jar --rev "$version" --compile "$kind" --output-dir out > buildtools.log 2>&1) \
      || { tail -n 50 "$work/buildtools.log"; exit 1; }
    mv "$work"/out/"$kind"-*.jar "$out"
    rm -rf "$work"
    ;;
  *)
    echo "unknown server kind: $kind" >&2
    exit 2
    ;;
esac
echo "fetched $kind $version to $out"
