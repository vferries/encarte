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
# Link previews (Open Graph) reuse the store feature graphics: one source per image.
cp fastlane/metadata/android/fr-FR/images/featureGraphic.png "$out/og-fr.png"
cp fastlane/metadata/android/en-US/images/featureGraphic.png "$out/og-en.png"

# The site promises no JavaScript; an inline or external <script> would break it without any URL check noticing.
scripts_found=$(grep -rliE '<script' --include='*.html' "$out" || true)
[[ -z $scripts_found ]] || fail "script found in: $scripts_found"

# Only <a> tags may point elsewhere (source code, issues). Anything left once they are removed would be loaded by the
# browser, so no URL with a scheme or a leading // may remain, whatever the quoting or line layout. A URL in visible
# text fails loudly too, which is acceptable. The site's own origin is same-origin, not external: the canonical,
# hreflang and Open Graph tags need absolute https://encarte.fr URLs. It is removed only when it is the whole host
# (followed by / or a delimiter), so https://encarte.fr.evil.com still fails.
external=0
while IFS= read -r -d '' file; do
  hits=$(tr '\n' ' ' < "$file" | sed -E 's/<a[[:space:]][^>]*>//gI' \
    | sed -E 's#https://encarte\.fr(/[^"'"'"'[:space:]>]*)?(["'"'"'[:space:]>])#\2#g' \
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

# Nav and FAQ links use #fragments: each one must land on an existing id, or the menu silently breaks.
bad_anchor=0
while IFS= read -r match; do
  file=${match%%:*}
  ref=${match#*:href=\"}
  path=${ref%%#*}
  fragment=${ref#*#}
  case $path in http://*|https://*) continue ;; esac
  target=$file
  if [[ -n $path ]]; then
    target="$(dirname "$file")/$path"
    [[ $target == */ || $target == */. ]] && target="${target%.}index.html"
  fi
  [[ -e $target ]] || continue   # a missing page is reported by the link check above
  grep -q "id=\"$fragment\"" "$target" || { echo "build-site: broken anchor in $file: $ref" >&2; bad_anchor=1; }
done < <(grep -roE 'href="[^"]*#[^"]+' --include='*.html' "$out")
(( bad_anchor == 0 )) || fail "broken anchors (above)"

echo "build-site: $out ready ($(find "$out" -type f | wc -l) files)"
