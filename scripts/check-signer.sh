#!/usr/bin/env bash
# Fails unless <file> (AAB or APK) carries a valid signature from the certificate with the expected SHA-256:
# scripts/check-signer.sh <file> <expected sha256, colons and case ignored>
# It catches a release signed with the wrong keystore (release spec §4).
set -euo pipefail

file=${1:?usage: check-signer.sh <file> <expected-sha256>}
expected=${2:-}
fail() { echo "check-signer: $*" >&2; exit 1; }

normalize() { tr -d ':[:space:]' | tr '[:lower:]' '[:upper:]'; }

[[ -n $expected ]] || fail "no expected fingerprint (set the UPLOAD_CERT_SHA256 repository variable)"
expected_norm=$(normalize <<< "$expected")
# Without this, an expected value like ":" and unparsable output would both normalize to "" and compare equal.
[[ $expected_norm =~ ^[0-9A-F]{64}$ ]] || fail "the expected fingerprint is not 64 hex characters (colons and case ignored)"
# The JDK tools localize their output (French prints "SHA 256:"), and this parses it: force English.
jdk_en=(-J-Duser.language=en -J-Duser.country=US)
# Captured first: piping jarsigner into grep -q could kill it with SIGPIPE under pipefail.
verify=$(jarsigner "${jdk_en[@]}" -verify "$file" 2>&1) || fail "jarsigner failed on $file: $verify"
grep -q '^jar verified' <<< "$verify" || fail "$file is not signed, or its signature is invalid"

certs=$(keytool "${jdk_en[@]}" -printcert -jarfile "$file")
# With several signers, reading only the first one would let a second, unexpected certificate through.
signers=$(grep -c '^Signer #' <<< "$certs" || true)
[[ $signers -eq 1 ]] || fail "$file has $signers signers, expected exactly 1"
actual=$(sed -n 's/^[[:space:]]*SHA256: //p' <<< "$certs" | head -n 1)
[[ $(normalize <<< "$actual") == "$expected_norm" ]] \
  || fail "$file is signed by $actual, expected $expected"
echo "check-signer: $file is signed by the expected certificate"
