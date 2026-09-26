#!/usr/bin/env bash
# Downloads the plugins Wormhole integrates with, for one Minecraft version:
#   fetch-plugins.sh <minecraft-version> <out-dir>
# Each Modrinth plugin is its newest build for that version published on or before PLUGINS_AS_OF
# (YYYY-MM-DD); PLUGINS_AS_OF=latest drops the cutoff. A plugin with no build for the version is
# skipped and named in <out-dir>/skipped.txt.
set -euo pipefail

mc="$1"
out="$2"
as_of="${PLUGINS_AS_OF:-$(tr -d '\r\n ' < "$(dirname "$0")/plugins-as-of.txt")}"
ua="WormholeXTreme-boot-test (https://github.com/khanjal/Wormhole-X-Treme)"
mkdir -p "$out"
: > "$out/skipped.txt"

for slug in luckperms placeholderapi coreprotect essentialsx; do
  # Within a build's declared range rather than an exact tag: EssentialsX tags only a few versions.
  pick="$(curl -fsS -A "$ua" -G "https://api.modrinth.com/v2/project/$slug/version" \
      --data-urlencode 'loaders=["paper","spigot","bukkit"]' \
    | AS_OF="$as_of" MC="$mc" python3 -c '
import json, os, re, sys
as_of, mc = os.environ["AS_OF"], os.environ["MC"]
def key(v):
    # Padded so that 26.3 and 26.3.0 compare equal.
    return (tuple(int(n) for n in v.split(".")) + (0, 0))[:3]
for v in json.load(sys.stdin):
    if v["version_type"] != "release" or (as_of != "latest" and v["date_published"][:10] > as_of):
        continue
    tagged = [key(g) for g in v["game_versions"] if re.fullmatch(r"\d+(\.\d+)*", g)]
    if not tagged or not (min(tagged) <= key(mc) <= max(tagged)):
        continue
    jars = [f for f in v["files"] if f["filename"].endswith(".jar")]
    if not jars:
        continue
    f = next((f for f in jars if f["primary"]), jars[0])
    print(v["version_number"], f["url"], f["hashes"]["sha512"], f["filename"])
    break
')"
  if [[ -z "$pick" ]]; then
    echo "$slug" >> "$out/skipped.txt"
    echo "no $slug release for $mc (as of $as_of); skipped"
    continue
  fi
  read -r version url sha512 filename <<< "$pick"
  curl -fsS -A "$ua" -o "$out/$filename" "$url"
  echo "$sha512  $out/$filename" | sha512sum -c --quiet
  echo "fetched $slug $version"
done

# Vault is not on Modrinth; its last release is from 2020 and still what servers run.
curl -fsSL -A "$ua" -o "$out/Vault.jar" "https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar"
echo "a6b5ed97f43a5cf5bbaf00a7c8cd23c5afc9bd003f849875af8b36e6cf77d01d  $out/Vault.jar" | sha256sum -c --quiet
echo "fetched vault 1.7.3"
