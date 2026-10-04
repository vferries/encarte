#!/usr/bin/env bash
# Fails unless <file> (AAB or APK) carries a valid signature from the certificate with the expected SHA-256:
# scripts/check-signer.sh <file> <expected sha256, colons and case ignored>
# It catches a release signed with the wrong keystore (release spec §4).
set -euo pipefail

file=${1:?usage: check-signer.sh <file> <expected-sha256>}
expected=${2:-}
fail() { echo "check-signer: $*" >&2; exit 1; }

[[ -n $expected ]] || fail "no expected fingerprint (set the UPLOAD_CERT_SHA256 repository variable)"
# The JDK tools localize their output (French prints "SHA 256:"), and this parses it: force English.
jdk_en=(-J-Duser.language=en -J-Duser.country=US)
# Captured first: piping jarsigner into grep -q could kill it with SIGPIPE under pipefail.
verify=$(jarsigner "${jdk_en[@]}" -verify "$file" 2>&1) || fail "jarsigner failed on $file: $verify"
grep -q '^jar verified' <<< "$verify" || fail "$file is not signed, or its signature is invalid"

normalize() { tr -d ':[:space:]' | tr '[:lower:]' '[:upper:]'; }
actual=$(keytool "${jdk_en[@]}" -printcert -jarfile "$file" | sed -n 's/^[[:space:]]*SHA256: //p' | head -n 1)
[[ $(normalize <<< "$actual") == "$(normalize <<< "$expected")" ]] \
  || fail "$file is signed by $actual, expected $expected"
echo "check-signer: $file is signed by the expected certificate"
