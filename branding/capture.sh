#!/usr/bin/env bash
# Saves the connected device's screen as a raw store-screenshot capture: branding/capture.sh <locale> <n>
# The whole procedure (demo mode, demo data, shot order) is in branding/README.md.
set -euo pipefail

usage="usage: $0 <fr-FR|en-US> <1-5>"
[[ $# -eq 2 && ($1 == fr-FR || $1 == en-US) && $2 =~ ^[1-5]$ ]] || { echo "$usage" >&2; exit 2; }

out="$(dirname "$0")/screenshots/raw/$1/$2.png"
mkdir -p "$(dirname "$out")"
adb exec-out screencap -p > "$out.tmp"
# FLAG_SECURE (app lock on) turns the capture black: refuse it rather than ship a black screenshot.
# Alpha is dropped first: screencap PNGs carry an opaque alpha channel, whose maximum is always 1.
if [[ $(magick "$out.tmp" -alpha off -colorspace Gray -format '%[fx:maxima]' info:) == 0 ]]; then
  rm -f "$out.tmp"
  echo "capture.sh: the capture is black; turn the app lock off and retry" >&2
  exit 1
fi
mv "$out.tmp" "$out"
magick identify -format "capture.sh: wrote $out (%wx%h)\n" "$out"
