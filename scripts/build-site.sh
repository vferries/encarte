#!/usr/bin/env bash
# Assembles the encarte.fr site into build/site/ (release spec §8) and checks it: every relative link resolves,
# and nothing loads from another origin. The Pages workflow deploys its output; open it locally to preview.
set -euo pipefail

cd "$(dirname "$0")/.."
out=build/site
fail() { echo "build-site: $*" >&2; exit 1; }

rm -rf "$out"
mkdir -p "$out/fonts"
cp -R site/. "$out/"
# One source per asset: the app's own font and the store icon.
cp app/src/main/res/font/nunito.ttf "$out/fonts/nunito.ttf"
cp branding/icon.svg "$out/icon.svg"
echo "encarte.fr" > "$out/CNAME"

# Pages may link out (source code, issues) but must never load a resource from another origin.
if grep -rnE 'src="(https?:)?//|<link[^>]*href="(https?:)?//|url\(.?(https?:)?//|@import' \
    --include='*.html' --include='*.css' "$out"; then
  fail "external resource found (above)"
fi

broken=0
while IFS= read -r match; do
  file=${match%%:*}
  ref=${match#*:}
  ref=${ref#*=\"}
  case $ref in http://*|https://*|mailto:*) continue ;; esac
  target="$(dirname "$file")/$ref"
  [[ $target == */ || $target == */. ]] && target="${target%.}index.html"
  [[ -e $target ]] || { echo "build-site: broken link in $file: $ref" >&2; broken=1; }
done < <(grep -roE '(href|src)="[^"#]+' --include='*.html' "$out")
(( broken == 0 )) || fail "broken relative links (above)"

echo "build-site: $out ready ($(find "$out" -type f | wc -l) files)"
