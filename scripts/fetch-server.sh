#!/usr/bin/env bash
# Downloads a server jar for the boot test: fetch-server.sh <paper|purpur|spigot|craftbukkit> <version> <out.jar>
# Spigot and CraftBukkit have no download; BuildTools compiles them, which takes several minutes.
set -euo pipefail

kind="$1"
version="$2"
out="$3"
ua="WormholeXTreme-boot-test (https://github.com/khanjal/Wormhole-X-Treme)"
mkdir -p "$(dirname "$out")"

case "$kind" in
  paper)
    meta="$(curl -fsS -A "$ua" "https://fill.papermc.io/v3/projects/paper/versions/$version/builds/latest")"
    url="$(printf '%s' "$meta" | python3 -c 'import json,sys; print(json.load(sys.stdin)["downloads"]["server:default"]["url"])')"
    sum="$(printf '%s' "$meta" | python3 -c 'import json,sys; print(json.load(sys.stdin)["downloads"]["server:default"]["checksums"]["sha256"])')"
    curl -fsS -A "$ua" -o "$out" "$url"
    echo "$sum  $out" | sha256sum -c --quiet
    ;;
  purpur)
    meta="$(curl -fsS -A "$ua" "https://api.purpurmc.org/v2/purpur/$version/latest")"
    sum="$(printf '%s' "$meta" | python3 -c 'import json,sys; print(json.load(sys.stdin)["md5"])')"
    curl -fsS -A "$ua" -o "$out" "https://api.purpurmc.org/v2/purpur/$version/latest/download"
    echo "$sum  $out" | md5sum -c --quiet
    ;;
  spigot|craftbukkit)
    work="$(mktemp -d)"
    curl -fsS -A "$ua" -o "$work/BuildTools.jar" \
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
