#!/usr/bin/env bash
# Regenerates every store PNG in fastlane/metadata/android/*/images from the sources in branding/,
# then checks sizes and the absence of alpha. Dev-only: needs Google Chrome and ImageMagick 7 (see README.md).
set -euo pipefail

cd "$(dirname "$0")/.."
branding="$PWD/branding"
locales=(fr-FR en-US)

profile=$(mktemp -d)
trap 'rm -rf "$profile"' EXIT

chrome_bin=${CHROME:-google-chrome}

# Chrome's stderr goes to a log so a failure can show its cause instead of vanishing.
fail() {
  echo "render.sh: $*" >&2
  if [[ -s $profile/chrome.log ]]; then
    echo "render.sh: last lines of Chrome's log:" >&2
    tail -n 20 "$profile/chrome.log" >&2
  fi
  exit 1
}

for tool in "$chrome_bin" magick; do
  command -v "$tool" >/dev/null || fail "'$tool' not found in PATH (see branding/README.md)"
done

# A throwaway profile keeps the user's own Chrome session out of it; file access lets templates load the font.
chrome() {
  "$chrome_bin" --headless=new --user-data-dir="$profile" --disable-gpu --hide-scrollbars \
    --allow-file-access-from-files --force-device-scale-factor=1 --virtual-time-budget=10000 "$@" 2>>"$profile/chrome.log"
}

# Templates set data-ready="ok" only once Nunito and their images loaded; Chrome would otherwise fall back silently.
check_ready() {
  local url=$1 state
  state=$(chrome --dump-dom "$url" | grep -o 'data-ready="[^"]*"' || true)
  [[ $state == 'data-ready="ok"' ]] || fail "$url is not ready (${state:-no data-ready attribute})"
}

# Play and F-Droid want opaque PNGs at exact sizes.
flatten_and_verify() {
  local file=$1 width=$2 height=$3 actual
  # Without the date chunks every run changes the bytes of every PNG, which hides real changes from the diff.
  magick "$file" -background white -alpha remove -alpha off -define png:exclude-chunk=date,time "PNG24:$file"
  actual=$(magick identify -format '%w %h %[channels]' "$file")
  [[ $actual == "$width $height srgb "* ]] || fail "$file is '$actual', expected ${width}x${height} sRGB without alpha"
}

render() {
  local url=$1 width=$2 height=$3 out=$4
  mkdir -p "$(dirname "$out")"
  chrome --window-size="$width,$height" --screenshot="$out" "$url"
  [[ -s $out ]] || fail "Chrome wrote no image for $url"
  flatten_and_verify "$out" "$width" "$height"
  echo "render.sh: $out"
}

for locale in "${locales[@]}"; do
  images="fastlane/metadata/android/$locale/images"
  render "file://$branding/icon.svg" 512 512 "$images/icon.png"

  url="file://$branding/feature-graphic.html?locale=$locale"
  check_ready "$url"
  render "$url" 1024 500 "$images/featureGraphic.png"

  for n in 1 2 3 4 5; do
    [[ -f $branding/screenshots/raw/$locale/$n.png ]] || fail "missing raw capture screenshots/raw/$locale/$n.png (see branding/README.md)"
    url="file://$branding/screenshot.html?locale=$locale&n=$n"
    check_ready "$url"
    render "$url" 1080 1920 "$images/phoneScreenshots/$n.png"
  done
done
# Web-sized screenshots for the landing page's phone fan (landing page spec §4.2): committed under site/img so the
# Pages deploy needs no ImageMagick. Same byte-stable output rule as the store images.
for locale in "${locales[@]}"; do
  short=${locale%%-*}
  mkdir -p "site/img/$short"
  for pair in 2:display 1:list 5:empty; do
    magick "$branding/screenshots/raw/$locale/${pair%%:*}.png" -resize 600x -strip \
      -define png:exclude-chunk=date,time "site/img/$short/${pair#*:}.png"
    echo "render.sh: site/img/$short/${pair#*:}.png"
  done
done
echo "render.sh: all store images regenerated and checked"
