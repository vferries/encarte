#!/usr/bin/env bash
# Tests for check-release.sh, run against throwaway copies of the files it reads.
set -euo pipefail

script="$(cd "$(dirname "$0")" && pwd)/check-release.sh"
failures=0

# make_root <versionName> <versionCode> <fr changelog> <en changelog>; "-" means "no file".
make_root() {
  local root
  root=$(mktemp -d)
  mkdir -p "$root/app"
  printf '    defaultConfig {\n        versionCode = %s\n        versionName = "%s"\n    }\n' "$2" "$1" > "$root/app/build.gradle.kts"
  local locale text
  for locale in fr-FR en-US; do
    if [[ $locale == fr-FR ]]; then text=$3; else text=$4; fi
    mkdir -p "$root/fastlane/metadata/android/$locale/changelogs"
    [[ $text == - ]] || printf '%s' "$text" > "$root/fastlane/metadata/android/$locale/changelogs/$2.txt"
  done
  echo "$root"
}

expect_ok() { # <name> <root> [tag]
  local name=$1 root=$2; shift 2
  if ENCARTE_ROOT=$root "$script" "$@" > /dev/null 2>&1; then
    echo "ok   $name"
  else
    echo "FAIL $name: expected success"; failures=$((failures + 1))
  fi
}

expect_fail() { # <name> <expected message part> <root> [tag]
  local name=$1 needle=$2 root=$3; shift 3
  local output
  if output=$(ENCARTE_ROOT=$root "$script" "$@" 2>&1); then
    echo "FAIL $name: expected failure"; failures=$((failures + 1))
  elif [[ $output != *"$needle"* ]]; then
    echo "FAIL $name: message '$output' lacks '$needle'"; failures=$((failures + 1))
  else
    echo "ok   $name"
  fi
}

e500=$(printf 'é%.0s' $(seq 500))
e501=$(printf 'é%.0s' $(seq 501))

expect_ok   "consistent, no tag"            "$(make_root 1.0.0 10000 'Note' 'Note')"
expect_ok   "consistent, matching tag"      "$(make_root 1.0.0 10000 'Note' 'Note')" v1.0.0
expect_ok   "1.2.3 is 10203"                "$(make_root 1.2.3 10203 'Note' 'Note')" v1.2.3
expect_ok   "500 accented characters fit"   "$(make_root 1.0.0 10000 "$e500" 'Note')"
expect_fail "tag without v"         "is not vMAJOR.MINOR.PATCH"   "$(make_root 1.0.0 10000 'Note' 'Note')" 1.0.0
expect_fail "tag mismatch"          "does not match versionName"  "$(make_root 1.0.0 10000 'Note' 'Note')" v1.0.1
expect_fail "wrong versionCode"     "should be 10000"             "$(make_root 1.0.0 10001 'Note' 'Note')"
expect_fail "two-part versionName"  "is not MAJOR.MINOR.PATCH"    "$(make_root 1.0 10000 'Note' 'Note')"
expect_fail "minor too large"       "below 100"                   "$(make_root 1.100.0 20000 'Note' 'Note')"
expect_fail "missing en changelog"  "missing or empty changelog"  "$(make_root 1.0.0 10000 'Note' -)"
expect_fail "empty fr changelog"    "missing or empty changelog"  "$(make_root 1.0.0 10000 '' 'Note')"
expect_fail "501 characters"        "has 501 characters"          "$(make_root 1.0.0 10000 "$e501" 'Note')"

if (( failures > 0 )); then
  echo "$failures check-release test(s) failed"
  exit 1
fi
echo "all check-release tests passed"
