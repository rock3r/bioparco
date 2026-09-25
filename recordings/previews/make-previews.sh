#!/usr/bin/env bash
# Builds one animated WebP preview per specimen MP4.
# Usage: make-previews.sh <dir with <specimen>.mp4 files> [settings.tsv]
# Writes <specimen>.webp next to each MP4. Needs ffmpeg and img2webp (Debian/Ubuntu: `webp`).
set -euo pipefail

dir=$1
settings=${2:-"$(dirname "$0")/previews.tsv"}

shopt -s nullglob
movies=("$dir"/*.mp4)
if [ ${#movies[@]} -eq 0 ]; then
  echo "No MP4s under $dir" >&2
  exit 1
fi

for movie in "${movies[@]}"; do
  name=$(basename "$movie" .mp4)
  fps=12 width=- seconds=- crop=- quality=65
  row=$(awk -F '\t' -v n="$name" '$1 == n { print; exit }' "$settings")
  if [ -n "$row" ]; then
    IFS=$'\t' read -r _ fps width seconds crop quality <<< "$row"
  fi

  filters=()
  [ "$crop" != "-" ] && filters+=("crop=$crop")
  filters+=("fps=$fps")
  [ "$width" != "-" ] && filters+=("scale=$width:-2:flags=lanczos")
  vf=$(IFS=,; echo "${filters[*]}")

  frames=$(mktemp -d "$dir/.frames.XXXXXX")
  trap 'rm -rf "$frames"' EXIT
  trim=()
  [ "$seconds" != "-" ] && trim=(-t "$seconds")
  ffmpeg -v error ${trim[@]+"${trim[@]}"} -i "$movie" -vf "$vf" "$frames/%05d.png"
  delay=$(( (1000 + fps / 2) / fps ))
  img2webp -loop 0 -lossy -q "$quality" -m 6 -d "$delay" "$frames"/*.png -o "$dir/$name.webp"
  rm -rf "$frames"
done
