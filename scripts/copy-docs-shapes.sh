#!/usr/bin/env bash
# Copies the shipped gate shapes into docs/assets/shapes/ with a manifest of their names, so the
# gate viewer reads the real files. Run it before `mkdocs build` or `mkdocs serve`; the copy is
# git-ignored and the workflow does the same.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
src="$root/src/main/resources/shapes/gate"
dest="$root/docs/assets/shapes"

rm -rf "$dest"
mkdir -p "$dest"

names=()
for file in "$src"/*.shape; do
  cp "$file" "$dest/"
  base="$(basename "$file" .shape)"
  names+=("$base")
done

{
  printf '{\n  "shapes": [\n'
  last=$(( ${#names[@]} - 1 ))
  for i in "${!names[@]}"; do
    sep=","
    [ "$i" -eq "$last" ] && sep=""
    printf '    { "name": "%s", "file": "%s.shape" }%s\n' "${names[$i]}" "${names[$i]}" "$sep"
  done
  printf '  ]\n}\n'
} > "$dest/manifest.json"

echo "Copied ${#names[@]} shapes to docs/assets/shapes/"
