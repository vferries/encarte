#!/usr/bin/env bash
# Checks that a release is consistent before it is tagged or built: scripts/check-release.sh [vX.Y.Z]
# Release spec §5: tag format, tag = v + versionName, versionCode = MAJOR*10000 + MINOR*100 + PATCH,
# and both changelogs for that versionCode exist, are not empty and fit Play's 500-character limit.
set -euo pipefail

root=${ENCARTE_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}
gradle_file="$root/app/build.gradle.kts"
max_chars=500

fail() { echo "check-release: $*" >&2; exit 1; }

# F-Droid's checkupdates reads these two literals too, which is why they are not computed.
version_name=$(sed -n 's/^ *versionName = "\([^"]*\)".*/\1/p' "$gradle_file")
version_code=$(sed -n 's/^ *versionCode = \([0-9]*\).*/\1/p' "$gradle_file")
[[ -n $version_name && -n $version_code ]] || fail "no versionName/versionCode literal in $gradle_file"

if (( $# > 0 )); then
  [[ $1 =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "tag '$1' is not vMAJOR.MINOR.PATCH"
  [[ $1 == "v$version_name" ]] || fail "tag '$1' does not match versionName '$version_name'"
fi

[[ $version_name =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]] || fail "versionName '$version_name' is not MAJOR.MINOR.PATCH"
major=$((10#${BASH_REMATCH[1]})) minor=$((10#${BASH_REMATCH[2]})) patch=$((10#${BASH_REMATCH[3]}))
(( minor < 100 && patch < 100 )) || fail "MINOR and PATCH must stay below 100 ($version_name)"
expected=$((major * 10000 + minor * 100 + patch))
[[ $version_code == "$expected" ]] || fail "versionCode $version_code should be $expected for $version_name"

for locale in fr-FR en-US; do
  changelog="$root/fastlane/metadata/android/$locale/changelogs/$version_code.txt"
  [[ -s $changelog ]] || fail "missing or empty changelog $changelog"
  # Play counts characters, not bytes: French accents must not count twice.
  chars=$(LC_ALL=C.UTF-8 wc -m < "$changelog")
  (( chars <= max_chars )) || fail "changelog $changelog has $chars characters (max $max_chars)"
done

echo "check-release: $version_name ($version_code) is consistent${1:+ with tag $1}"
