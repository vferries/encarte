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

# The site promises no JavaScript; an inline or external <script> would break it without any URL check noticing.
scripts_found=$(grep -rliE '<script' --include='*.html' "$out" || true)
[[ -z $scripts_found ]] || fail "script found in: $scripts_found"

# Only <a> tags may point elsewhere (source code, issues). Anything left once they are removed would be loaded by the
# browser, so no URL with a scheme or a leading // may remain, whatever the quoting or line layout. A URL in visible
# text fails loudly too, which is acceptable.
external=0
while IFS= read -r -d '' file; do
  hits=$(tr '\n' ' ' < "$file" | sed -E 's/<a[[:space:]][^>]*>//gI' \
    | grep -oiE "([a-z][a-z0-9+.-]*:)?//[^[:space:])>\"']+" || true)
  [[ -z $hits ]] || { echo "build-site: external resource in $file:" >&2; echo "$hits" >&2; external=1; }
done < <(find "$out" -type f \( -name '*.html' -o -name '*.css' \) -print0)
(( external == 0 )) || fail "external resource found (above)"

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
