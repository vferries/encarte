# Encarté Release and Store Publication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn a `vX.Y.Z` tag into a signed Play bundle without any secret in the repository, publish `encarte.fr` with a privacy policy, and prepare everything the user needs to submit to Google Play and F-Droid.

**Architecture:**
- **Signing:** Gradle reads four `ENCARTE_UPLOAD_*` environment variables. Release builds stay unsigned without them.
- **Release workflow:** a tag-only GitHub workflow runs in a protected `release` environment. It decodes the upload keystore from secrets into a temporary file, builds and checks the AAB, and publishes it as a run artifact.
- **Release scripts:** small bash scripts check release consistency (`check-release.sh`) and the signer certificate (`check-signer.sh`).
- **Site:** static HTML/CSS in `site/`, assembled by `scripts/build-site.sh` and deployed by a Pages workflow.
- **Store kits:** Markdown guides and an F-Droid metadata draft under `docs/release/`.

**Tech Stack:** Gradle Kotlin DSL (AGP 9.4.1), GitHub Actions (official actions pinned by SHA), bash, keytool/jarsigner (JDK 21), static HTML/CSS, headless Chrome for checks, PyYAML for workflow syntax checks.

**Spec:** `docs/superpowers/specs/2026-10-04-encarte-release-design.md`

## Global Constraints

**Secrets**
- No secret value (keystore, password, token) ever enters the repository, a commit, a log, a plan, a report or the chat. The user types secret values in their own terminal. Throwaway test keystores live only in `mktemp -d` directories.
- Environment variable names: `ENCARTE_UPLOAD_KEYSTORE`, `ENCARTE_UPLOAD_KEYSTORE_PASSWORD`, `ENCARTE_UPLOAD_KEY_ALIAS`, `ENCARTE_UPLOAD_KEY_PASSWORD`.
- Secrets in the `release` environment: `UPLOAD_KEYSTORE_BASE64`, `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD`. Repository variable: `UPLOAD_CERT_SHA256`.

**Versioning**
- `versionName` is `MAJOR.MINOR.PATCH`, and `versionCode = MAJOR*10000 + MINOR*100 + PATCH`. Both stay literals in `app/build.gradle.kts`.
- The first release is `1.0.0` / `10000`, with tag `v1.0.0`.
- Changelogs live at `fastlane/metadata/android/<fr-FR|en-US>/changelogs/<versionCode>.txt`, at most 500 characters.

**Workflows**
- No new Gradle dependency.
- New workflows use only official `actions/*` and `gradle/actions`, each pinned to a full 40-character commit SHA with the version in a comment:
  - `actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1`
  - `actions/setup-java@de7274f081f381c8f8158605e0321c36c376e2e6 # v6.0.1`
  - `gradle/actions/setup-gradle@3f5f9adaf7d9fecd50b5935e54106014257a94e6 # v6.4.0`
  - `actions/cache@55cc8345863c7cc4c66a329aec7e433d2d1c52a9 # v6.1.0`
  - `actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1`
  - `actions/configure-pages@45bfe0192ca1faeb007ade9deae92b16b8254a0d # v6.0.0`
  - `actions/upload-pages-artifact@fc324d3547104276b827a68afc52ff2a11cc49c9 # v5.0.0`
  - `actions/deploy-pages@368f82528645a54fb793d4d04e342629a3f51346 # v5.0.1`

**Site**
- No JavaScript and no resource from another origin. French at the root, English under `en/`.
- Publisher: Vincent Ferries. Host: GitHub, Inc., 88 Colin P. Kelly Jr. Street, San Francisco, CA 94107, USA.
- Brand palette:
  - Cream `#FFF6E8`, Teal `#2E8C83`, Navy `#1D2440`, White `#FFFFFF`;
  - dark-theme accent: light teal `#7FD1C7`.

**Repository and style**
- Commit directly on `main` (personal project), with conventional prefixes and **no `Co-Authored-By` trailer**.
- Comments explain why, not what, matching the surrounding code.
- Any change to GitHub settings, or any push, is done by the controller only after the user says yes.

## Review Focus

1. **A half-configured release silently ships unsigned.** With some `ENCARTE_UPLOAD_*` variables set but not all, Gradle must fail and name the missing ones. This is pinned by Task 2, Step 4.
2. **A wrong or swapped keystore signs the bundle.** `check-signer.sh` must refuse a bundle whose certificate does not match `UPLOAD_CERT_SHA256`. This is pinned by Task 4, Step 3, with a wrong fingerprint.
3. **French changelogs are counted in bytes.** "é" is 2 bytes, but Play counts characters, so a 300-character French note must pass. This is pinned by the 500-« é » cases in Task 1.
4. **A later site edit loads a font or script from a CDN, or breaks a link.** `build-site.sh` must fail on an external resource or a broken relative link. This is pinned by Task 5, Step 4, with injected faults.
5. **Release secrets are reachable from a pull request, or through an unpinned action.** `release.yml` must have no `pull_request` trigger, must declare `environment: release`, and must pin every `uses:` by SHA. This is pinned by the structural checks in Task 4, Step 5.

---

### Task 1: Version 1.0.0, changelogs and the release consistency check

**Files:**
- Create: `scripts/check-release.sh` (executable)
- Create: `scripts/check-release.test.sh` (executable)
- Create: `fastlane/metadata/android/fr-FR/changelogs/10000.txt`
- Create: `fastlane/metadata/android/en-US/changelogs/10000.txt`
- Modify: `app/build.gradle.kts:21-22` (`versionCode`, `versionName`)
- Modify: `.github/workflows/ci.yml` (one step)

**Interfaces:**
- Produces:
  - `scripts/check-release.sh [vX.Y.Z]`: exit 0 with a one-line OK message, or exit 1 with `check-release: <reason>` on stderr;
  - the environment variable `ENCARTE_ROOT` overrides the repository root (used by the tests);
  - Task 4 calls the script with `"$GITHUB_REF_NAME"`.

- [ ] **Step 1: Write the failing tests**

Create `scripts/check-release.test.sh`:

```bash
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
```

Run `chmod +x scripts/check-release.test.sh`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `scripts/check-release.test.sh`
Expected: every line is `FAIL …`, because `check-release.sh` does not exist yet and every call fails. The "expect_fail" cases also fail, on the missing message text. The script exits 1.

- [ ] **Step 3: Implement `scripts/check-release.sh`**

```bash
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
```

Run `chmod +x scripts/check-release.sh`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `scripts/check-release.test.sh`
Expected: 12 `ok` lines, then `all check-release tests passed`, exit 0.

- [ ] **Step 5: Bump the version and write the changelogs**

In `app/build.gradle.kts` (in `defaultConfig`):

```kotlin
        versionCode = 10000
        versionName = "1.0.0"
```

Create `fastlane/metadata/android/fr-FR/changelogs/10000.txt`:

```
Première version publique.

• Rangez vos cartes de fidélité hors-ligne et présentez-les en caisse, luminosité au maximum.
• Scannez une carte avec l'appareil photo ou depuis une image, ou saisissez son numéro.
• Sauvegardes compatibles Catima, chiffrées si vous le souhaitez.
• Verrouillage facultatif de l'application.
```

Create `fastlane/metadata/android/en-US/changelogs/10000.txt`:

```
First public release.

• Keep your loyalty cards offline and show them at checkout at full brightness.
• Scan a card with the camera or from an image, or type its number.
• Catima-compatible backups, encrypted if you want.
• Optional app lock.
```

Run: `scripts/check-release.sh v1.0.0`
Expected: `check-release: 1.0.0 (10000) is consistent with tag v1.0.0`.

- [ ] **Step 6: Run the checks in normal CI**

In `.github/workflows/ci.yml`, insert this step right after `- uses: actions/checkout@v7`:

```yaml
      - name: Release consistency (scripts and current version)
        run: |
          scripts/check-release.test.sh
          scripts/check-release.sh
```

Check the YAML: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))" && echo yaml-ok`
Expected: `yaml-ok`.

- [ ] **Step 7: Make sure the app still builds with the new version**

Run: `./gradlew testDebugUnitTest lintDebug`
Expected: BUILD SUCCESSFUL. The About screen shows `BuildConfig.VERSION_NAME`, so `SettingsScreenTest` may assert a version string. If a test fails on "0.1.0", update that expectation to "1.0.0" and say so in the report.

- [ ] **Step 8: Commit**

```bash
git add scripts/check-release.sh scripts/check-release.test.sh app/build.gradle.kts \
  fastlane/metadata/android/fr-FR/changelogs/10000.txt fastlane/metadata/android/en-US/changelogs/10000.txt \
  .github/workflows/ci.yml
git commit -m "feat: version 1.0.0 with release consistency checks"
```

(Include any test file that Step 7 had to update.)

---

### Task 2: Release signing from the environment

**Files:**
- Modify: `app/build.gradle.kts` (top-level values before `android {`, a `signingConfigs` block, and `signingConfig` in `buildTypes.release`)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `./gradlew bundleRelease` writes `app/build/outputs/bundle/release/app-release.aab`. It is signed by the keystore given in `ENCARTE_UPLOAD_*` when all four variables are set, unsigned when none is set, and the configuration fails when only some are. Task 4 relies on this.

- [ ] **Step 1: Record the current behavior (unsigned)**

Run: `./gradlew bundleRelease && jarsigner -verify app/build/outputs/bundle/release/app-release.aab`
Expected: BUILD SUCCESSFUL, then `jar is unsigned.`

- [ ] **Step 2: Add the signing configuration**

In `app/build.gradle.kts`, after the `plugins { … }` block and before `android {`, add:

```kotlin
// Release signing comes only from the environment (the CI `release` environment, release spec §4), never from
// files in the repo. Without it the release build stays unsigned, which is what CI and F-Droid expect.
val uploadSigningEnv = listOf(
    "ENCARTE_UPLOAD_KEYSTORE",
    "ENCARTE_UPLOAD_KEYSTORE_PASSWORD",
    "ENCARTE_UPLOAD_KEY_ALIAS",
    "ENCARTE_UPLOAD_KEY_PASSWORD",
).associateWith { providers.environmentVariable(it).orNull }
val missingUploadSigning = uploadSigningEnv.filterValues { it.isNullOrEmpty() }.keys
// A half-set environment must never fall back to an unsigned bundle that looks like a release.
if (missingUploadSigning.isNotEmpty() && missingUploadSigning.size < uploadSigningEnv.size) {
    throw GradleException("Release signing is half-configured; missing: ${missingUploadSigning.joinToString()}")
}
```

Inside `android { … }`, before `buildTypes {`, add:

```kotlin
    signingConfigs {
        if (missingUploadSigning.isEmpty()) {
            create("upload") {
                storeFile = file(uploadSigningEnv.getValue("ENCARTE_UPLOAD_KEYSTORE")!!)
                storePassword = uploadSigningEnv.getValue("ENCARTE_UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = uploadSigningEnv.getValue("ENCARTE_UPLOAD_KEY_ALIAS")
                keyPassword = uploadSigningEnv.getValue("ENCARTE_UPLOAD_KEY_PASSWORD")
            }
        }
    }
```

In `buildTypes { release { … } }`, add as its first line:

```kotlin
            signingConfig = signingConfigs.findByName("upload")
```

- [ ] **Step 3: Verify the signed path with a throwaway keystore**

```bash
T=$(mktemp -d)
keytool -genkeypair -keystore "$T/test.jks" -storetype PKCS12 -storepass throwaway -keypass throwaway \
  -alias test -keyalg RSA -keysize 2048 -validity 1 -dname "CN=Throwaway test key" 2>/dev/null
ENCARTE_UPLOAD_KEYSTORE="$T/test.jks" ENCARTE_UPLOAD_KEYSTORE_PASSWORD=throwaway \
  ENCARTE_UPLOAD_KEY_ALIAS=test ENCARTE_UPLOAD_KEY_PASSWORD=throwaway ./gradlew bundleRelease
jarsigner -verify app/build/outputs/bundle/release/app-release.aab | head -1
keytool -printcert -jarfile app/build/outputs/bundle/release/app-release.aab | grep 'SHA256:'
keytool -list -v -keystore "$T/test.jks" -storepass throwaway | grep 'SHA256:'
```

Expected: BUILD SUCCESSFUL, `jar verified.`, and the same `SHA256:` fingerprint from the bundle and from the keystore. Keep `$T` for Step 4, and for Task 4 if it runs in the same shell.

- [ ] **Step 4: Verify the unsigned and half-configured paths**

```bash
./gradlew bundleRelease && jarsigner -verify app/build/outputs/bundle/release/app-release.aab
ENCARTE_UPLOAD_KEYSTORE="$T/test.jks" ./gradlew help; echo "exit $?"
```

Expected: the first command prints BUILD SUCCESSFUL and `jar is unsigned.`. The second fails with `Release signing is half-configured; missing: ENCARTE_UPLOAD_KEYSTORE_PASSWORD, ENCARTE_UPLOAD_KEY_ALIAS, ENCARTE_UPLOAD_KEY_PASSWORD` and a non-zero exit.

- [ ] **Step 5: Run the normal checks**

Run: `./gradlew assembleRelease lintDebug testDebugUnitTest verifyNoNetworkPermission`
Expected: BUILD SUCCESSFUL, with no `ENCARTE_UPLOAD_*` variable set.

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts
git commit -m "feat: sign release bundles from ENCARTE_UPLOAD_* environment variables"
```

Then delete the throwaway directory: `rm -rf "$T"`.

---

### Task 3: Leak guard for keystores and keys

**Files:**
- Modify: `.gitignore` (append 4 lines)
- Modify: `.github/workflows/ci.yml` (one step)

**Interfaces:**
- Consumes: the `ci.yml` step added by Task 1, which stays in place.
- Produces: CI fails when a keystore or key file is tracked.

- [ ] **Step 1: Ignore keystores and keys**

Append to `.gitignore`:

```
*.jks
*.keystore
*.p12
*.pem
```

- [ ] **Step 2: Add the guard step**

In `.github/workflows/ci.yml`, insert right after `- uses: actions/checkout@v7`, before the Task 1 step:

```yaml
      # .gitignore keeps keystores out by accident; this catches a forced add.
      - name: Refuse tracked keystores and keys
        run: |
          tracked=$(git ls-files -- '*.jks' '*.keystore' '*.p12' '*.pem')
          if [ -n "$tracked" ]; then
            echo "::error::Keystore or key files must never be committed: $tracked"
            exit 1
          fi
```

Check the YAML: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))" && echo yaml-ok`

- [ ] **Step 3: Prove the guard fails on a tracked keystore**

Use a scratch worktree, so `main` never sees the file:

```bash
W=$(mktemp -d)/wt
git worktree add --detach "$W" HEAD
( cd "$W" && touch test.jks && git add -f test.jks \
  && tracked=$(git ls-files -- '*.jks' '*.keystore' '*.p12' '*.pem') \
  && if [ -n "$tracked" ]; then echo "guard would fail: $tracked"; else echo "guard would pass"; fi )
git worktree remove --force "$W"
tracked=$(git ls-files -- '*.jks' '*.keystore' '*.p12' '*.pem'); [ -z "$tracked" ] && echo "main is clean"
```

Expected: `guard would fail: test.jks`, then `main is clean`.

- [ ] **Step 4: Commit**

```bash
git add .gitignore .github/workflows/ci.yml
git commit -m "chore: refuse committed keystores and keys"
```

---

### Task 4: Release workflow and signer check

**Files:**
- Create: `scripts/check-signer.sh` (executable)
- Create: `.github/workflows/release.yml`

**Interfaces:**
- Consumes:
  - `scripts/check-release.sh` (Task 1);
  - the `ENCARTE_UPLOAD_*` signing (Task 2), which writes `app/build/outputs/bundle/release/app-release.aab`.
- Produces: `scripts/check-signer.sh <file> <expected-sha256>`. It exits 0 when the file is signed by a certificate with that SHA-256. Colons and case are ignored.

- [ ] **Step 1: Write `scripts/check-signer.sh`**

```bash
#!/usr/bin/env bash
# Fails unless <file> (AAB or APK) carries a valid signature from the certificate with the expected SHA-256:
# scripts/check-signer.sh <file> <expected sha256, colons and case ignored>
# It catches a release signed with the wrong keystore (release spec §4).
set -euo pipefail

file=${1:?usage: check-signer.sh <file> <expected-sha256>}
expected=${2:-}
fail() { echo "check-signer: $*" >&2; exit 1; }

[[ -n $expected ]] || fail "no expected fingerprint (set the UPLOAD_CERT_SHA256 repository variable)"
# Captured first: piping jarsigner into grep -q could kill it with SIGPIPE under pipefail.
verify=$(jarsigner -verify "$file" 2>&1) || fail "jarsigner failed on $file: $verify"
grep -q '^jar verified' <<< "$verify" || fail "$file is not signed, or its signature is invalid"

normalize() { tr -d ':[:space:]' | tr '[:lower:]' '[:upper:]'; }
actual=$(keytool -printcert -jarfile "$file" | sed -n 's/^[[:space:]]*SHA256: //p' | head -n 1)
[[ $(normalize <<< "$actual") == "$(normalize <<< "$expected")" ]] \
  || fail "$file is signed by $actual, expected $expected"
echo "check-signer: $file is signed by the expected certificate"
```

Run `chmod +x scripts/check-signer.sh`.

- [ ] **Step 2: Check the signer script on an unsigned bundle**

Run: `./gradlew bundleRelease && scripts/check-signer.sh app/build/outputs/bundle/release/app-release.aab AA:BB; echo "exit $?"`
Expected: `check-signer: … is not signed, or its signature is invalid`, then `exit 1`.

- [ ] **Step 3: Check the signer script with a throwaway key: right fingerprint, then a wrong one**

```bash
T=$(mktemp -d)
keytool -genkeypair -keystore "$T/test.jks" -storetype PKCS12 -storepass throwaway -keypass throwaway \
  -alias test -keyalg RSA -keysize 2048 -validity 1 -dname "CN=Throwaway test key" 2>/dev/null
ENCARTE_UPLOAD_KEYSTORE="$T/test.jks" ENCARTE_UPLOAD_KEYSTORE_PASSWORD=throwaway \
  ENCARTE_UPLOAD_KEY_ALIAS=test ENCARTE_UPLOAD_KEY_PASSWORD=throwaway ./gradlew bundleRelease
fp=$(keytool -list -v -keystore "$T/test.jks" -storepass throwaway | sed -n 's/^[[:space:]]*SHA256: //p')
scripts/check-signer.sh app/build/outputs/bundle/release/app-release.aab "$fp"; echo "exit $?"
scripts/check-signer.sh app/build/outputs/bundle/release/app-release.aab "$(tr 'A-F' 'a-f' <<< "$fp" | tr -d ':')"; echo "exit $?"
scripts/check-signer.sh app/build/outputs/bundle/release/app-release.aab "00:11:22"; echo "exit $?"
rm -rf "$T"
```

Expected:
1. the exact fingerprint gives `is signed by the expected certificate`, exit 0;
2. the lowercase fingerprint without colons gives the same result, exit 0;
3. `00:11:22` gives `is signed by …, expected 00:11:22`, exit 1.

- [ ] **Step 4: Write `.github/workflows/release.yml`**

```yaml
name: Release

# Builds the Play bundle from a vX.Y.Z tag, signed with the upload key held in the protected `release`
# environment (release spec §6). The AAB is uploaded to the Play Console by hand; F-Droid builds the tag itself.
on:
  push:
    tags: ['v*']

permissions:
  contents: read

jobs:
  bundle:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    environment: release
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
        with:
          persist-credentials: false
      - uses: actions/setup-java@de7274f081f381c8f8158605e0321c36c376e2e6 # v6.0.1
        with:
          distribution: temurin
          java-version: 21
      # Tag builds only read Gradle caches, so no cache entry is written while signing secrets are in the environment.
      - uses: gradle/actions/setup-gradle@3f5f9adaf7d9fecd50b5935e54106014257a94e6 # v6.4.0
        with:
          cache-read-only: true
      - uses: actions/cache@55cc8345863c7cc4c66a329aec7e433d2d1c52a9 # v6.1.0
        with:
          path: ~/.m2/repository/org/robolectric
          key: robolectric-4.17-sdk37
      - name: Release consistency
        run: scripts/check-release.sh "$GITHUB_REF_NAME"
      - name: Lint, unit tests, offline guardrail
        run: ./gradlew lintDebug testDebugUnitTest verifyNoNetworkPermission
      - name: Decode the upload keystore
        env:
          UPLOAD_KEYSTORE_BASE64: ${{ secrets.UPLOAD_KEYSTORE_BASE64 }}
        run: |
          umask 077
          printf '%s' "$UPLOAD_KEYSTORE_BASE64" | base64 --decode > "$RUNNER_TEMP/upload.jks"
      - name: Build the signed bundle
        env:
          ENCARTE_UPLOAD_KEYSTORE: ${{ runner.temp }}/upload.jks
          ENCARTE_UPLOAD_KEYSTORE_PASSWORD: ${{ secrets.UPLOAD_KEYSTORE_PASSWORD }}
          ENCARTE_UPLOAD_KEY_ALIAS: ${{ secrets.UPLOAD_KEY_ALIAS }}
          ENCARTE_UPLOAD_KEY_PASSWORD: ${{ secrets.UPLOAD_KEY_PASSWORD }}
        run: ./gradlew bundleRelease
      - name: Check the bundle's signer
        env:
          UPLOAD_CERT_SHA256: ${{ vars.UPLOAD_CERT_SHA256 }}
        run: scripts/check-signer.sh app/build/outputs/bundle/release/app-release.aab "$UPLOAD_CERT_SHA256"
      - uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1
        with:
          name: encarte-${{ github.ref_name }}-aab
          path: app/build/outputs/bundle/release/app-release.aab
          retention-days: 30
          if-no-files-found: error
      - name: Remove the keystore
        if: always()
        run: rm -f "$RUNNER_TEMP/upload.jks"
```

- [ ] **Step 5: Structural checks**

```bash
python3 - <<'EOF'
import re, yaml
wf = yaml.safe_load(open('.github/workflows/release.yml'))
triggers = wf[True]  # PyYAML reads the bare key `on` as True
assert list(triggers) == ['push'] and list(triggers['push']) == ['tags'], triggers
assert wf['permissions'] == {'contents': 'read'}
job = wf['jobs']['bundle']
assert job['environment'] == 'release'
for step in job['steps']:
    if 'uses' in step:
        assert re.fullmatch(r'[\w.-]+/[\w./-]+@[0-9a-f]{40}', step['uses']), step['uses']
print('release.yml structure ok')
EOF
grep -c 'secrets\.' .github/workflows/release.yml
```

Expected: `release.yml structure ok`, then `4`. That is one reference per secret: `UPLOAD_KEYSTORE_BASE64`, `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS` and `UPLOAD_KEY_PASSWORD`, each used only in the step that needs it.

- [ ] **Step 6: Commit**

```bash
git add scripts/check-signer.sh .github/workflows/release.yml
git commit -m "feat: build the signed Play bundle from release tags"
```

---

### Task 5: The encarte.fr site

**Files:**
- Create: `site/style.css`
- Create: `site/index.html`
- Create: `site/privacy.html`
- Create: `site/en/index.html`
- Create: `site/en/privacy.html`
- Create: `scripts/build-site.sh` (executable)

**Interfaces:**
- Consumes: `app/src/main/res/font/nunito.ttf` and `branding/icon.svg` (both existing).
- Produces: `scripts/build-site.sh` writes `build/site/`, with the pages, `style.css`, `fonts/nunito.ttf`, `icon.svg` and `CNAME`, and fails on an external resource or a broken relative link. Task 6 deploys `build/site/`.

- [ ] **Step 1: Write `site/style.css`**

```css
/* encarte.fr: same palette as the store graphics (visual identity spec §3). Everything is served from this site. */
@font-face {
  font-family: Nunito;
  src: url("fonts/nunito.ttf") format("truetype");
  font-weight: 200 1000;
  font-display: swap;
}

:root {
  --bg: #FFF6E8;
  --text: #1D2440;
  --accent: #2E8C83;
  --muted: #4A5170;
  --rule: #E8DCC7;
}

@media (prefers-color-scheme: dark) {
  :root {
    --bg: #1D2440;
    --text: #FFF6E8;
    --accent: #7FD1C7;
    --muted: #C9C3B8;
    --rule: #343C5E;
  }
}

* { box-sizing: border-box; }

body {
  margin: 0;
  background: var(--bg);
  color: var(--text);
  font: 500 1.0625rem/1.6 Nunito, system-ui, sans-serif;
}

main, footer {
  max-width: 42rem;
  margin: 0 auto;
  padding: 1.5rem 1rem;
}

a { color: var(--accent); font-weight: 700; }

.lang { text-align: right; font-size: 0.9375rem; }

.hero { text-align: center; padding: 1.5rem 0 1rem; }
.hero img { width: 7rem; height: 7rem; border-radius: 1.5rem; }
h1 { font-size: 2.75rem; font-weight: 900; line-height: 1.1; margin: 0.75rem 0 0.25rem; }
h2 { font-size: 1.375rem; font-weight: 800; margin: 2rem 0 0.5rem; }
.tagline { font-size: 1.375rem; font-weight: 800; color: var(--accent); margin: 0; }

.promises { list-style: none; padding: 0; margin: 2rem 0; display: grid; gap: 0.75rem; }
.promises li { padding: 0.75rem 1rem; border: 2px solid var(--rule); border-radius: 0.5rem; }
.promises strong { font-weight: 900; }

.links { text-align: center; }

footer { border-top: 2px solid var(--rule); color: var(--muted); font-size: 0.9375rem; }
footer h2 { font-size: 1rem; margin-top: 0.5rem; }
```

- [ ] **Step 2: Write the four pages**

`site/index.html`:

```html
<!doctype html>
<html lang="fr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Encarté — vos cartes de fidélité, hors-ligne</title>
<meta name="description" content="Encarté range vos cartes de fidélité sur votre téléphone et affiche leur code-barres en caisse. Hors-ligne, sans compte, logiciel libre.">
<link rel="icon" href="icon.svg" type="image/svg+xml">
<link rel="stylesheet" href="style.css">
<link rel="alternate" hreflang="en" href="en/">
</head>
<body>
<main>
  <nav class="lang"><a href="en/" hreflang="en" lang="en">English</a></nav>
  <header class="hero">
    <img src="icon.svg" alt="" width="112" height="112">
    <h1>Encarté</h1>
    <p class="tagline">Vos cartes de fidélité. Rien d'autre.</p>
  </header>
  <ul class="promises">
    <li><strong>Hors-ligne.</strong> L'application n'a pas accès à Internet : elle ne peut rien envoyer.</li>
    <li><strong>Sans compte.</strong> Rien à créer, rien à synchroniser.</li>
    <li><strong>Libre.</strong> Code source ouvert, sous licence GPL-3.0 ou ultérieure.</li>
  </ul>
  <p>Encarté range vos cartes de fidélité sur votre téléphone et affiche leur code-barres en caisse, luminosité au maximum. Scannez une carte, retrouvez-la en un instant, et sauvegardez le tout dans un fichier compatible Catima.</p>
  <p class="links"><a href="https://github.com/vferries/encarte">Code source</a> · <a href="privacy.html">Confidentialité</a></p>
</main>
<footer>
  <h2>Mentions légales</h2>
  <p>Éditeur : Vincent Ferries. Contact : <a href="https://github.com/vferries/encarte/issues">suivi des tickets sur GitHub</a>.<br>
  Hébergeur : GitHub, Inc., 88 Colin P. Kelly Jr. Street, San Francisco, CA 94107, États-Unis.</p>
  <p>Ce site n'utilise ni cookie ni traceur.</p>
</footer>
</body>
</html>
```

`site/privacy.html`:

```html
<!doctype html>
<html lang="fr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Confidentialité — Encarté</title>
<meta name="description" content="Politique de confidentialité d'Encarté : aucune donnée collectée, aucune donnée transmise.">
<link rel="icon" href="icon.svg" type="image/svg+xml">
<link rel="stylesheet" href="style.css">
<link rel="alternate" hreflang="en" href="en/privacy.html">
</head>
<body>
<main>
  <nav class="lang"><a href="./">Accueil</a> · <a href="en/privacy.html" hreflang="en" lang="en">English</a></nav>
  <h1>Politique de confidentialité</h1>
  <p>En vigueur au 4 octobre 2026. Elle s'applique à l'application Android Encarté.</p>

  <h2>Aucune donnée collectée</h2>
  <p>Encarté ne collecte, ne transmet et ne vend aucune donnée. L'application ne demande pas l'accès à Internet : elle n'a techniquement aucun moyen d'envoyer quoi que ce soit.</p>

  <h2>Appareil photo</h2>
  <p>L'appareil photo sert uniquement à lire le code-barres d'une carte et, si vous le souhaitez, à photographier son recto et son verso. Les images sont traitées sur votre téléphone et n'en sortent pas.</p>

  <h2>Vos cartes restent sur votre téléphone</h2>
  <p>Les cartes, leurs numéros et leurs photos sont enregistrés dans l'espace privé de l'application. Ils sont exclus de la sauvegarde dans le cloud de Google. Lors d'un transfert direct d'un ancien téléphone vers un nouveau, Android peut les copier d'un appareil à l'autre. Désinstaller l'application les supprime.</p>

  <h2>Sauvegardes</h2>
  <p>Une sauvegarde est un fichier que vous créez vous-même et que vous enregistrez où vous le choisissez. Si vous définissez un mot de passe, elle est chiffrée (AES-256).</p>

  <h2>Verrouillage</h2>
  <p>Le verrouillage facultatif utilise l'empreinte, le visage ou le code de votre appareil, par l'intermédiaire d'Android. Encarté ne reçoit aucune donnée biométrique.</p>

  <h2>Ni publicité, ni statistiques, ni traceurs</h2>
  <p>Encarté n'intègre aucun outil tiers de publicité, de mesure d'audience ou de suivi.</p>

  <h2>Contact</h2>
  <p>Pour toute question : <a href="https://github.com/vferries/encarte/issues">suivi des tickets sur GitHub</a>. Toute modification de cette politique sera publiée sur cette page, avec sa nouvelle date.</p>
</main>
<footer>
  <p>Éditeur : Vincent Ferries. Hébergeur : GitHub, Inc., 88 Colin P. Kelly Jr. Street, San Francisco, CA 94107, États-Unis. Ce site n'utilise ni cookie ni traceur ; l'hébergeur peut conserver des journaux techniques de connexion.</p>
</footer>
</body>
</html>
```

`site/en/index.html`:

```html
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Encarté — your loyalty cards, offline</title>
<meta name="description" content="Encarté keeps your loyalty cards on your phone and shows their barcode at checkout. Offline, no account, open source.">
<link rel="icon" href="../icon.svg" type="image/svg+xml">
<link rel="stylesheet" href="../style.css">
<link rel="alternate" hreflang="fr" href="../">
</head>
<body>
<main>
  <nav class="lang"><a href="../" hreflang="fr" lang="fr">Français</a></nav>
  <header class="hero">
    <img src="../icon.svg" alt="" width="112" height="112">
    <h1>Encarté</h1>
    <p class="tagline">Your loyalty cards. Nothing else.</p>
  </header>
  <ul class="promises">
    <li><strong>Offline.</strong> The app has no internet access: it cannot send anything.</li>
    <li><strong>No account.</strong> Nothing to sign up for, nothing to sync.</li>
    <li><strong>Open source.</strong> Free software under the GPL-3.0-or-later license.</li>
  </ul>
  <p>Encarté keeps your loyalty cards on your phone and shows their barcode at checkout, at full brightness. Scan a card, find it in a second, and back everything up to a Catima-compatible file.</p>
  <p class="links"><a href="https://github.com/vferries/encarte">Source code</a> · <a href="privacy.html">Privacy</a></p>
</main>
<footer>
  <h2>Legal notice</h2>
  <p>Publisher: Vincent Ferries. Contact: <a href="https://github.com/vferries/encarte/issues">GitHub issue tracker</a>.<br>
  Host: GitHub, Inc., 88 Colin P. Kelly Jr. Street, San Francisco, CA 94107, USA.</p>
  <p>This site uses no cookies and no trackers.</p>
</footer>
</body>
</html>
```

`site/en/privacy.html`:

```html
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Privacy — Encarté</title>
<meta name="description" content="Encarté's privacy policy: no data collected, no data sent.">
<link rel="icon" href="../icon.svg" type="image/svg+xml">
<link rel="stylesheet" href="../style.css">
<link rel="alternate" hreflang="fr" href="../privacy.html">
</head>
<body>
<main>
  <nav class="lang"><a href="./">Home</a> · <a href="../privacy.html" hreflang="fr" lang="fr">Français</a></nav>
  <h1>Privacy policy</h1>
  <p>Effective October 4, 2026. It applies to the Encarté Android app.</p>

  <h2>No data collected</h2>
  <p>Encarté collects, sends and sells no data. The app does not request internet access, so it has no technical means of sending anything.</p>

  <h2>Camera</h2>
  <p>The camera is used only to read a card's barcode and, if you want, to photograph its front and back. Images are processed on your phone and never leave it.</p>

  <h2>Your cards stay on your phone</h2>
  <p>Cards, their numbers and their photos are stored in the app's private storage. They are excluded from Google's cloud backup. During a direct transfer from an old phone to a new one, Android may copy them from one device to the other. Uninstalling the app deletes them.</p>

  <h2>Backups</h2>
  <p>A backup is a file you create yourself and save wherever you choose. If you set a password, it is encrypted (AES-256).</p>

  <h2>App lock</h2>
  <p>The optional app lock uses your device's fingerprint, face or screen lock, through Android. Encarté receives no biometric data.</p>

  <h2>No ads, no analytics, no trackers</h2>
  <p>Encarté includes no third-party advertising, analytics or tracking tools.</p>

  <h2>Contact</h2>
  <p>Questions: <a href="https://github.com/vferries/encarte/issues">GitHub issue tracker</a>. Any change to this policy will be published on this page with its new date.</p>
</main>
<footer>
  <p>Publisher: Vincent Ferries. Host: GitHub, Inc., 88 Colin P. Kelly Jr. Street, San Francisco, CA 94107, USA. This site uses no cookies and no trackers; the host may keep technical connection logs.</p>
</footer>
</body>
</html>
```

- [ ] **Step 3: Write `scripts/build-site.sh`**

```bash
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
```

Run `chmod +x scripts/build-site.sh`.

- [ ] **Step 4: Build, and prove both checks fail on a fault**

```bash
scripts/build-site.sh
ls build/site build/site/en build/site/fonts
```

Expected: `build-site: build/site ready (8 files)`. That is `CNAME`, `icon.svg`, `index.html`, `privacy.html`, `style.css`, `fonts/nunito.ttf`, `en/index.html` and `en/privacy.html`.

Then inject one fault at a time, and restore each with `git checkout -- site` (or by re-editing if the files are not committed yet):
1. Add `<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Nunito">` to `site/index.html`. `scripts/build-site.sh` must fail with `external resource found`.
2. Change `href="privacy.html"` to `href="privacy-old.html"` in `site/en/index.html`. It must fail with `broken link in build/site/en/index.html: privacy-old.html`.

After restoring, `scripts/build-site.sh` succeeds again.

- [ ] **Step 5: Look at the pages**

```bash
P=$(mktemp -d); O=$(mktemp -d)
for page in index privacy en/index en/privacy; do
  for scheme in 1 0; do   # 1 = light, 0 = dark
    for size in 390,1400 1280,1000; do
      google-chrome --headless=new --user-data-dir="$P" --disable-gpu --hide-scrollbars --allow-file-access-from-files \
        --blink-settings=preferredColorScheme=$scheme --window-size=$size \
        --screenshot="$O/$(tr '/' '-' <<< "$page")-$scheme-${size%%,*}.png" "file://$PWD/build/site/$page.html" 2>/dev/null
    done
  done
done
ls "$O"
```

Open the 16 PNGs and check each one:
- the font is Nunito (rounded), not a serif fallback;
- the icon shows;
- the dark scheme uses navy with cream text;
- nothing overflows at 390 px;
- the language switch and home links are visible.

Report anything off.

- [ ] **Step 6: Commit**

`build/` is already ignored, so only the sources are committed.

```bash
git add site scripts/build-site.sh
git commit -m "feat: add the encarte.fr home page and privacy policy"
```

---

### Task 6: Pages workflow

**Files:**
- Create: `.github/workflows/pages.yml`

**Interfaces:**
- Consumes: `scripts/build-site.sh` and its `build/site/` output (Task 5).
- Produces: a deployment to GitHub Pages. It only works once the controller enables Pages with the "GitHub Actions" source (Task 10).

- [ ] **Step 1: Write `.github/workflows/pages.yml`**

```yaml
name: Pages

# Publishes encarte.fr from site/ (release spec §8). The build script copies the app's font and icon, so changes
# to those also redeploy.
on:
  push:
    branches: [main]
    paths:
      - 'site/**'
      - 'scripts/build-site.sh'
      - 'app/src/main/res/font/nunito.ttf'
      - 'branding/icon.svg'
      - '.github/workflows/pages.yml'
  workflow_dispatch:

permissions:
  contents: read
  pages: write
  id-token: write

concurrency:
  group: pages
  cancel-in-progress: false

jobs:
  deploy:
    runs-on: ubuntu-latest
    timeout-minutes: 10
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
        with:
          persist-credentials: false
      - name: Build the site
        run: scripts/build-site.sh
      - uses: actions/configure-pages@45bfe0192ca1faeb007ade9deae92b16b8254a0d # v6.0.0
      - uses: actions/upload-pages-artifact@fc324d3547104276b827a68afc52ff2a11cc49c9 # v5.0.0
        with:
          path: build/site
      - id: deployment
        uses: actions/deploy-pages@368f82528645a54fb793d4d04e342629a3f51346 # v5.0.1
```

- [ ] **Step 2: Structural checks**

```bash
python3 - <<'EOF'
import re, yaml
wf = yaml.safe_load(open('.github/workflows/pages.yml'))
assert wf['permissions'] == {'contents': 'read', 'pages': 'write', 'id-token': 'write'}
for step in wf['jobs']['deploy']['steps']:
    if 'uses' in step:
        assert re.fullmatch(r'[\w.-]+/[\w./-]+@[0-9a-f]{40}', step['uses']), step['uses']
print('pages.yml structure ok')
EOF
```

Expected: `pages.yml structure ok`.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/pages.yml
git commit -m "chore: deploy encarte.fr with GitHub Pages"
```

---

### Task 7: F-Droid submission kit

Research done while writing this plan (2026-10-04) cleared both risks in spec §9:
- **Build environment.** Solid Share (`com.erfangholami.solidshare`, fdroiddata MR 45278, merged 2026-08-16) builds on F-Droid with AGP 9.4.0, Gradle 9.6.1, compileSdk 37, `jvmToolchain(21)` and a plain `gradle:` entry, with no `sudo:`. F-Droid replaces the wrapper with `gradlew-fdroid`, which reads `distributionUrl` and verifies Gradle 9.8.0 against its signed checksum log.
- **zxing-cpp.** `io.github.zxing-cpp:android` straight from Maven Central is accepted with no `scanignore`. Solid Share uses 3.1.1, Chompass 3.1.0 and SPAYD Decoder 2.3.0. Only apps that vendor the AAR in their own repo need special handling.
- **Category.** After F-Droid's 2026 category overhaul, Catima uses `Pass Wallet` ("Store boarding passes, tickets, loyalty cards, and coupons").
- **Residual uncertainty.** AGP 9.4.1 and Gradle 9.8.0 are slightly newer than the versions proven on F-Droid. The MR pipeline settles it.

**Files:**
- Create: `docs/release/fdroid/io.github.vferries.encarte.yml`
- Create: `docs/release/fdroid/README.md`

**Interfaces:**
- Consumes:
  - the version literals and changelogs from Task 1;
  - the fastlane metadata (title, descriptions, images) already in the repo.
- Produces: the file the user adds to their `fdroiddata` merge request, plus the steps. Task 9 links to the README.

- [ ] **Step 1: Write the metadata draft**

`docs/release/fdroid/io.github.vferries.encarte.yml`:

```yaml
Categories:
  - Pass Wallet
License: GPL-3.0-or-later
AuthorName: Vincent Ferries
WebSite: https://encarte.fr
SourceCode: https://github.com/vferries/encarte
IssueTracker: https://github.com/vferries/encarte/issues

AutoName: Encarté

RepoType: git
Repo: https://github.com/vferries/encarte.git

Builds:
  - versionName: 1.0.0
    versionCode: 10000
    commit: FULL_COMMIT_HASH_OF_TAG_v1.0.0
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags ^v\d+\.\d+\.\d+$
CurrentVersion: 1.0.0
CurrentVersionCode: 10000
```

`FULL_COMMIT_HASH_OF_TAG_v1.0.0` is deliberate. fdroiddata wants the full commit hash, which exists only once the tag is pushed, and the README tells the user to replace it. Other fields are deliberately absent:
- **`AllowedAPKSigningKeys`, `Binaries`:** omitted, so that F-Droid signs with its own key (spec decision). It is a one-way choice.
- **`Changelog`:** omitted, because the field wants a human-readable releases page. F-Droid reads `fastlane/.../changelogs/<versionCode>.txt` by itself.
- **`AuthorEmail`:** optional. Added only if the user wants it.

- [ ] **Step 2: Write `docs/release/fdroid/README.md`**

````markdown
# F-Droid submission

F-Droid builds Encarté from the `vX.Y.Z` tag and signs it with its own key. Texts, images and per-version
changelogs come from `fastlane/metadata/android/` in this repository. After inclusion, new tags are picked up
automatically (`UpdateCheckMode: Tags`, `AutoUpdateMode: Version`), so later releases need no merge request.

## Before submitting

- `v1.0.0` is tagged and pushed, and the `Release` workflow passed on it.
- The repository has `fastlane/metadata/android/en-US/` (title, short and full description), which F-Droid requires.

## Merge request

1. Sign in to GitLab and fork https://gitlab.com/fdroid/fdroiddata.
2. In your fork, create a branch named `io.github.vferries.encarte`.
3. Copy `docs/release/fdroid/io.github.vferries.encarte.yml` to `metadata/io.github.vferries.encarte.yml`, and replace
   `FULL_COMMIT_HASH_OF_TAG_v1.0.0` with the output of `git rev-parse 'v1.0.0^{commit}'` (40 hexadecimal characters).
4. Optional local checks, with fdroidserver installed in a virtualenv (`pip install fdroidserver`), from the fork's
   root: `fdroid readmeta`, `fdroid rewritemeta io.github.vferries.encarte`, `fdroid lint io.github.vferries.encarte`.
   The merge-request pipeline runs the same checks plus a full build.
5. Commit (`New app: Encarté`), push, and open the merge request against `fdroid/fdroiddata` `master` with the
   "App inclusion" template. Tick its checklist: FOSS license, no non-free dependencies (all from Maven Central /
   Google Maven), no tracking, no network permission, the app is yours.
6. Follow the pipeline (checkupdates, lint, build, scanner) and answer the reviewers. Inclusion usually takes a
   few weeks. The app then appears on f-droid.org after the next index publication.

## Known facts for reviewers

- The build is a plain Gradle build of `app/`, with no flavors, no `sudo:` and no prebuilt binaries in the repository.
- `io.github.zxing-cpp:android` comes from Maven Central. Other apps in fdroiddata use it as is (Solid Share 3.1.1,
  Chompass 3.1.0).
- The release build is unsigned when no `ENCARTE_UPLOAD_*` variable is set, which is the case on F-Droid's build server.
````

- [ ] **Step 3: Check the draft**

```bash
python3 - <<'EOF'
import yaml
m = yaml.safe_load(open('docs/release/fdroid/io.github.vferries.encarte.yml'))
for key in ['Categories', 'License', 'AuthorName', 'SourceCode', 'IssueTracker', 'RepoType', 'Repo', 'Builds',
            'AutoUpdateMode', 'UpdateCheckMode', 'CurrentVersion', 'CurrentVersionCode']:
    assert key in m, key
b = m['Builds'][0]
assert (b['versionName'], b['versionCode'], b['subdir']) == ('1.0.0', 10000, 'app'), b
assert 'AllowedAPKSigningKeys' not in m and 'Binaries' not in m
print('fdroid metadata ok')
EOF
grep -n 'versionName = "1.0.0"\|versionCode = 10000' app/build.gradle.kts
ls fastlane/metadata/android/en-US/ fastlane/metadata/android/en-US/changelogs/
```

Expected:
- `fdroid metadata ok`;
- both literals found in `build.gradle.kts`;
- the en-US directory lists `title.txt`, `short_description.txt`, `full_description.txt`, `images` and `changelogs/10000.txt`.

- [ ] **Step 4: Commit**

```bash
git add docs/release/fdroid
git commit -m "docs: add the F-Droid metadata draft and submission steps"
```

---

### Task 8: One-time setup guide and Play Console guide

**Files:**
- Create: `docs/release/setup.md`
- Create: `docs/release/play-console.md`

**Interfaces:**
- Consumes:
  - names from Tasks 2 to 6: environment variables, secrets, the variable, the workflows;
  - paths of the store assets in `fastlane/metadata`.
- Produces: the two guides that Task 9's checklist links to.

- [ ] **Step 1: Write `docs/release/setup.md`**

````markdown
# One-time release setup

Run these once, before the first release. Every command runs **in your own terminal**: secret values never go
through chat, logs or the repository. Repository: `vferries/encarte`.

## 1. Upload key

Create the key outside the repository and keep a copy in your password manager (the file and both passwords).
With Play App Signing, Google holds the app signing key; this key only authenticates uploads, and Play support
can reset it if it is lost or leaked.

```bash
keytool -genkeypair -v -keystore ~/encarte-upload.jks -storetype PKCS12 -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Vincent Ferries, O=Encarte"
```

`keytool` asks for the keystore password. With PKCS12, the key password is the same.

Record the certificate fingerprint (public, not a secret):

```bash
keytool -list -v -keystore ~/encarte-upload.jks -alias upload | sed -n 's/^[[:space:]]*SHA256: //p'
```

## 2. GitHub `release` environment, secrets and variable

The controller creates the environment (tag rule `v*`, you as required reviewer) once you agree; you can also do
it in Settings → Environments → New environment. Then set the secrets; each command prompts for the value or
reads it from a pipe:

```bash
base64 -w0 ~/encarte-upload.jks | gh secret set UPLOAD_KEYSTORE_BASE64 --env release --repo vferries/encarte
gh secret set UPLOAD_KEYSTORE_PASSWORD --env release --repo vferries/encarte   # prompts
gh secret set UPLOAD_KEY_PASSWORD --env release --repo vferries/encarte        # prompts (same as above with PKCS12)
gh secret set UPLOAD_KEY_ALIAS --env release --repo vferries/encarte --body upload
gh variable set UPLOAD_CERT_SHA256 --repo vferries/encarte --body "<fingerprint from step 1>"
```

## 3. Secret scanning and push protection

Settings → Code security → enable **Secret scanning** and **Push protection** (free on public repositories).
The controller can do it with `gh api` once you agree.

## 4. encarte.fr on GitHub Pages

1. The controller enables Pages with the **GitHub Actions** source and sets the custom domain `encarte.fr`
   once you agree. The `Pages` workflow then deploys `site/`.
2. **Verify the domain** in your account (prevents anyone else from claiming it on Pages): github.com →
   Settings (your profile) → Pages → Add a domain → `encarte.fr`. GitHub shows a `TXT` record named
   `_github-pages-challenge-vferries.encarte.fr`: add it at your registrar, then click Verify.
3. At your registrar, add the site records:

   | Name | Type | Value |
   |---|---|---|
   | `@` | A | `185.199.108.153` |
   | `@` | A | `185.199.109.153` |
   | `@` | A | `185.199.110.153` |
   | `@` | A | `185.199.111.153` |
   | `@` | AAAA | `2606:50c0:8000::153` |
   | `@` | AAAA | `2606:50c0:8001::153` |
   | `@` | AAAA | `2606:50c0:8002::153` |
   | `@` | AAAA | `2606:50c0:8003::153` |
   | `www` | CNAME | `vferries.github.io.` |

   Remove any other `A`/`AAAA` record on `@` (the registrar's parking page, for instance).
4. Once the certificate is issued (Settings → Pages shows it), tick **Enforce HTTPS**.
5. Check: `https://encarte.fr/` and `https://encarte.fr/privacy.html` load, and `https://www.encarte.fr/` redirects.
````

- [ ] **Step 2: Write `docs/release/play-console.md`**

````markdown
# Google Play Console: first publication

Account: existing personal developer account (created before November 2023: no mandatory closed test).
Every answer below matches the app's real behavior; re-check it if the app changes.

## 1. Create the app

Play Console → **Create app**:
- App name: `Encarté`
- Default language: French (France) – fr-FR
- App or game: **App** · Free or paid: **Free**
- Accept the declarations.

The package name is fixed by the first upload: `io.github.vferries.encarte`.

## 2. App signing

At the first release (step 6), keep **Play App Signing** with a key generated by Google (default). The first
uploaded AAB, signed with your upload key, registers that key as the upload key. Download nothing else.

## 3. Store listing (Grow → Store presence → Main store listing)

Add English (United States) as a second language: **Manage translations** → add en-US.

| Field | fr-FR | en-US |
|---|---|---|
| App name | `fastlane/metadata/android/fr-FR/title.txt` | `…/en-US/title.txt` |
| Short description | `…/fr-FR/short_description.txt` | `…/en-US/short_description.txt` |
| Full description | `…/fr-FR/full_description.txt` | `…/en-US/full_description.txt` |
| App icon | `…/fr-FR/images/icon.png` (512 × 512) | same file |
| Feature graphic | `…/fr-FR/images/featureGraphic.png` | `…/en-US/images/featureGraphic.png` |
| Phone screenshots | `…/fr-FR/images/phoneScreenshots/1.png` … `5.png` | `…/en-US/images/phoneScreenshots/1.png` … `5.png` |

Category: **Shopping**. Tags: loyalty cards, wallet (pick the closest offered).
Contact details: an email address you are willing to show publicly (required); website `https://encarte.fr`.

## 4. App content (Policy → App content)

- **Privacy policy:** `https://encarte.fr/privacy.html`
- **Ads:** No, the app contains no ads.
- **App access:** All functionality is available without special access (no login).
- **Content rating:** start the questionnaire, category **Utility, Productivity, Communication or Other**.
  Answer **No** to every question (no violence, sexual content, profanity, drugs, gambling, user-generated content,
  sharing of location or personal information, digital purchases). Expected rating: Everyone / PEGI 3.
- **Target audience and content:** ages **13–15, 16–17, 18 and over** (not under 13, so the Families policy does
  not apply). The app is not designed to appeal to children.
- **News app:** No.
- **Data safety:**
  - Does your app collect or share any of the required user data types? **No.**
  - Is all of the user data collected by your app encrypted in transit? Not applicable (nothing is collected).
  - Do you provide a way for users to request that their data be deleted? Not applicable (nothing is collected).
  - Result shown on the listing: "No data collected", "No data shared with third parties".
- **Government apps:** No. **Financial features:** None (the app does not handle money or payments).
- **Health:** None.

## 5. Countries

Production → Countries/regions: add all (or at least France, Belgium, Switzerland, Canada, Luxembourg).

## 6. Release

1. Push the tag (see `docs/release-checklist.md`); approve the `release` run on GitHub; download the artifact
   `encarte-vX.Y.Z-aab` and unzip it to get `app-release.aab`.
2. Testing → **Internal testing** → create a release → upload `app-release.aab`. Release notes: paste
   `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` for fr-FR and en-US.
3. Add yourself as a tester, install from the opt-in link on a phone, smoke-test (open, scan, show a card).
4. **Promote release** → Production. Rollout: 100 % for 1.0.0 (nobody to protect yet); later releases can use a
   staged rollout.
5. Send for review. First reviews can take several days.
````

- [ ] **Step 3: Check the guides for accuracy against the code**

Run these checks and report the output:

```bash
grep -n "permission" app/src/main/AndroidManifest.xml
grep -rn "ACCESS_FINE_LOCATION\|READ_CONTACTS\|INTERNET" app/src/main/AndroidManifest.xml || echo "no sensitive permission"
ls fastlane/metadata/android/*/images/phoneScreenshots/
```

Every claim in the Data safety and content-rating answers must hold: only CAMERA and biometric permissions, no network, five screenshots per locale.

- [ ] **Step 4: Commit**

```bash
git add docs/release/setup.md docs/release/play-console.md
git commit -m "docs: add the one-time release setup and Play Console guides"
```

---

### Task 9: Release checklist

**Files:**
- Modify (full rewrite): `docs/release-checklist.md`

**Interfaces:**
- Consumes: the scripts from Tasks 1 and 4, the guides from Tasks 7 and 8, and the workflows from Tasks 4 and 6.

- [ ] **Step 1: Rewrite `docs/release-checklist.md`**

```markdown
# Release checklist

Guides: one-time setup in `docs/release/setup.md`, Play Console in `docs/release/play-console.md`,
F-Droid in `docs/release/fdroid/README.md`.

## Before the first public release

- [ ] Trademark search for "Encarté" at INPI and EUIPO (classes 9 and 42).
- [ ] Check that `io.github.vferries.encarte` is free on Google Play and F-Droid.
- [x] Create the GitHub repository `vferries/encarte` (the About screen links to it).
- [x] Final app icon (replace the placeholder adaptive icon).
- [x] Store screenshots in `fastlane/metadata/android/*/images/phoneScreenshots/` (regenerate with `branding/render.sh`).
- [x] Feature graphic and store icon in `fastlane/metadata/android/*/images/` (same script).
- [ ] Decide on the French scanner label wrapping at 360 dp ("Depuis une image").
- [ ] Decide whether to recapture the screenshots without the Wi-Fi "!" and "3G" status icons.
- [ ] Upload key created, backed up in a password manager, `release` environment secrets and `UPLOAD_CERT_SHA256` set
      (`docs/release/setup.md` §1–2).
- [ ] Secret scanning and push protection enabled (`setup.md` §3).
- [ ] `https://encarte.fr` and `https://encarte.fr/privacy.html` live over HTTPS, domain verified (`setup.md` §4).
- [ ] Play Console app created, listing and App content complete (`play-console.md` §1–5).

## Every release

- [ ] `./gradlew clean assembleRelease lintDebug testDebugUnitTest verifyNoNetworkPermission` is green.
- [ ] Instrumented tests pass on an emulator: start `Pixel_10` read-only
      (`emulator -avd Pixel_10 -no-window -read-only -no-snapshot`), then `./gradlew connectedDebugAndroidTest`.
      Note: `--tests` is not supported by connectedDebugAndroidTest; use `-Pandroid.testInstrumentationRunnerArguments.class=<fully.qualified.Test>` to run a single class.
- [ ] Scan a real card with a physical phone, then have a real checkout scanner read the code
      displayed by Encarté (EAN-13 and one 2D code at least).
- [ ] Export from Encarté (with and without a password) and import the file into Catima
      (install Catima from F-Droid on an emulator). All cards and photos appear.
- [ ] Export from Catima (with and without a password) and import into Encarté.
- [ ] On a device, export and re-import a wallet of at least 150 cards with photos (with and without a password).
- [ ] App lock: enable, background for more than one minute, come back: the lock screen appears.
- [ ] Returning to the app after more than one minute shows the lock screen first, with no flash of card content.
- [ ] After unlocking, the screen you were on is restored.
- [ ] Relock while the full-screen photo or a dialog is open: nothing of the card shows above the lock screen, and Back leaves the app.
- [ ] Remove the device screen lock while the app lock is on: the app turns its lock off and says so.
- [ ] Rotate on every screen; "Don't keep activities" round-trip on the card display and the editor.
- [ ] Bump `versionName` (`MAJOR.MINOR.PATCH`) and `versionCode` (`MAJOR*10000 + MINOR*100 + PATCH`)
      in `app/build.gradle.kts`.
- [ ] Write `fastlane/metadata/android/{fr-FR,en-US}/changelogs/<versionCode>.txt` (500 characters max).
- [ ] `scripts/check-release.sh vX.Y.Z` passes.
- [ ] Commit, then `git tag vX.Y.Z && git push origin main vX.Y.Z`.
- [ ] Approve the `Release` run on GitHub (environment `release`); it must end green.
- [ ] Download the `encarte-vX.Y.Z-aab` artifact; upload it to the Play internal testing track; install and smoke-test;
      promote to production (`play-console.md` §6).
- [ ] F-Droid (once included): check a few days later that the new version built on f-droid.org
      (nothing to submit; it follows the tag).
```

- [ ] **Step 2: Commit**

```bash
git add docs/release-checklist.md
git commit -m "docs: rewrite the release checklist for tag-based releases"
```

---

### Task 10: GitHub settings and push (controller, with the user's consent)

The controller runs this task, not a subagent, and asks the user before each outward-facing action. These commands contain no secret values.

- [ ] **Step 1: Ask, then push `main`**

Ask the user to confirm the push. Then run `git push origin main`. CI runs on the push and must end green (`gh run watch`). The Pages workflow also starts, and fails until Step 2 enables Pages. That is expected: re-run it after Step 2.

- [ ] **Step 2: Ask, then enable Pages (Actions source) and the custom domain**

```bash
gh api -X POST repos/vferries/encarte/pages -f build_type=workflow
gh workflow run pages.yml --repo vferries/encarte && sleep 5 && gh run watch --repo vferries/encarte $(gh run list --repo vferries/encarte --workflow pages.yml --limit 1 --json databaseId --jq '.[0].databaseId')
gh api -X PUT repos/vferries/encarte/pages -f cname=encarte.fr
```

Then hand `docs/release/setup.md` §4 (domain verification and DNS) to the user.

- [ ] **Step 3: Ask, then create the `release` environment**

```bash
uid=$(gh api user --jq .id)
gh api -X PUT repos/vferries/encarte/environments/release --input - <<EOF
{"reviewers":[{"type":"User","id":$uid}],"deployment_branch_policy":{"protected_branches":false,"custom_branch_policies":true}}
EOF
gh api -X POST repos/vferries/encarte/environments/release/deployment-branch-policies -f name='v*' -f type=tag
gh api repos/vferries/encarte/environments/release --jq '{reviewers: [.protection_rules[]?.reviewers[]?.reviewer.login], policy: .deployment_branch_policy}'
```

Expected: the reviewer is `vferries`, with `custom_branch_policies: true`.

- [ ] **Step 4: Ask, then enable secret scanning and push protection**

```bash
gh api -X PATCH repos/vferries/encarte --input - <<'EOF'
{"security_and_analysis":{"secret_scanning":{"status":"enabled"},"secret_scanning_push_protection":{"status":"enabled"}}}
EOF
gh api repos/vferries/encarte --jq .security_and_analysis
```

- [ ] **Step 5: Hand over to the user**

Give the user the remaining one-time steps:
- `setup.md` §1–2: upload key, secrets and variable;
- §4: DNS and domain verification;
- `play-console.md`;
- `fdroid/README.md`.

Point out that the first tag `v1.0.0` waits until the "Before the first public release" items in the checklist are done.
