# Encarté — Release and Store Publication Design Spec

- **Date:** 2026-10-04
- **Status:** Draft, pending review
- **Parent specs:** `2026-10-03-encarte-design.md`, `2026-10-04-encarte-visual-identity-design.md`

## 1. Context and goals

Encarté v1 and its visual identity are on `main` and pushed to `github.com/vferries/encarte`. The app is not yet published anywhere. This spec covers what turns a commit into a release on **Google Play** and **F-Droid**, plus the public site that Play requires for its privacy-policy URL.

Decisions taken with the user (2026-10-04):

| Topic | Decision |
|---|---|
| Play account | Old personal account (created before Nov 2023): no mandatory closed test |
| Where the signed build is made | GitHub Actions, on a `v*` tag |
| How far CI goes on Play | Signed AAB as a **run artifact only**; the user uploads it by hand |
| F-Droid signing | F-Droid builds from the tag and signs with **its own key** |
| Site | GitHub Pages, custom domain `encarte.fr` |

Success criteria:

1. **No secret in the repository or its history.** No keystore, password, token or credential is committed. CI fails if a keystore-like file is tracked.
2. **Signing secrets exist only in a GitHub environment named `release`.** That environment can be used only by `v*` tags and needs the user's approval for each run.
3. **Pushing `vX.Y.Z` produces a signed AAB artifact**, after the same checks as normal CI and after release consistency checks: tag matches the version, version code follows the scheme, and changelogs exist.
4. **`https://encarte.fr` serves a home page and a privacy policy** in French and English, over HTTPS, with no third-party request.
5. **The user has everything needed to submit by hand:**
   - an F-Droid metadata file, plus the merge-request steps;
   - a Play Console guide with every form answer prepared.

## 2. Scope

**In scope:**
- versioning and changelogs;
- release signing configuration;
- the release workflow;
- the leak guard in CI;
- the `site/` sources and their Pages workflow;
- the F-Droid metadata draft;
- the Play Console guide;
- the rewritten release checklist.

**Out of scope:**
- Uploading to Play through its API, and therefore any Google credential in GitHub.
- Reproducible builds, and a shared signature across stores.
- APKs on GitHub Releases.
- A link to the site inside the app.
- Store submission itself. The user does it with their own accounts.
- Non-technical prerequisites. These stay as checklist items and must be done before the first publication:
  - trademark search;
  - S23 acceptance;
  - the two open screenshot decisions (French scanner label at 360 dp, Wi-Fi "!" in the captures).

## 3. Versioning and changelogs

- **Version name:** `versionName` is semantic: `MAJOR.MINOR.PATCH`. The first public release is `1.0.0`.
- **Version code:** `versionCode = MAJOR × 10000 + MINOR × 100 + PATCH`, so `1.0.0` gives `10000` and `1.2.3` gives `10203`. It is monotonic, and MINOR and PATCH stay below 100.
- **Literals:** both values stay **literals** in `app/build.gradle.kts`. F-Droid's `checkupdates` reads them with regular expressions and cannot evaluate a computed value. The release check verifies that they agree.
- **Tag:** the tag of a release is `v` + `versionName` (`v1.0.0`), on the commit that sets those values.
- **Changelogs:** one file per locale, `fastlane/metadata/android/<fr-FR|en-US>/changelogs/<versionCode>.txt`, plain text, at most **500 characters** (the Play limit). F-Droid shows the same files. The release check fails if either file is missing, empty or too long.

## 4. Release signing

- **Signing config:** `app/build.gradle.kts` gets a `release` signing config that reads four environment variables through Gradle's `providers.environmentVariable`, which is compatible with the configuration cache:
  - `ENCARTE_UPLOAD_KEYSTORE`: absolute path of the keystore;
  - `ENCARTE_UPLOAD_KEYSTORE_PASSWORD`;
  - `ENCARTE_UPLOAD_KEY_ALIAS`;
  - `ENCARTE_UPLOAD_KEY_PASSWORD`.
- **When it applies:** the release build type uses that signing config only when all four are set. Otherwise the release build stays **unsigned**, as today. That covers normal CI, F-Droid's build server and the user's machine. If only some are set, the build fails and names the missing variables, so a half-configured release cannot silently produce an unsigned bundle.
- **Upload key:** the user creates it once, locally, with `keytool` (RSA 4096, validity 25 years or more). It stays outside the repository, with a copy in their password manager.
  - With **Play App Signing**, Google holds the app signing key, and this key only authenticates uploads.
  - A lost or leaked upload key can be reset through Play support.
- **Expected certificate:** the SHA-256 fingerprint of the upload certificate is public, and is stored as a repository variable `UPLOAD_CERT_SHA256` (not a secret). The release workflow checks that the AAB is signed by that certificate, which catches a wrong or swapped keystore.
- **No new Gradle dependency**, and no third-party action for signing.

## 5. Release consistency check

`scripts/check-release.sh <tag>` runs locally and in CI, needs only `bash` and `grep`/`sed`, and fails with a clear message when any of these does not hold:

1. the tag matches `^v[0-9]+\.[0-9]+\.[0-9]+$`;
2. the tag equals `v` + the `versionName` literal;
3. the `versionCode` literal equals the formula in §3 applied to `versionName`, with MINOR and PATCH below 100;
4. both changelog files for that `versionCode` exist, are non-empty, and are at most 500 characters.

With no argument, it checks rules 3 and 4 against the current `versionName`. That way the user can run it before tagging.

## 6. Release workflow (`.github/workflows/release.yml`)

**Trigger and protection:**
- `push` of tags matching `v*` only. No `pull_request`, no `workflow_dispatch`.
- The job runs in `environment: release`. In the repository settings, the user configures that environment as follows:
  - the deployment branches and tags rule allows only the tag pattern `v*`;
  - the user is the **required reviewer**;
  - it holds the secrets.

**Secrets in the `release` environment:**

| Secret | Contents |
|---|---|
| `UPLOAD_KEYSTORE_BASE64` | the keystore file, base64-encoded |
| `UPLOAD_KEYSTORE_PASSWORD` | |
| `UPLOAD_KEY_ALIAS` | |
| `UPLOAD_KEY_PASSWORD` | |

**Hardening:**
- `permissions: contents: read`.
- Every action is pinned to a full commit SHA, with the version in a comment.
- `persist-credentials: false` on checkout.
- No secret is printed, and secrets reach steps only through `env:`.

**Steps:**
1. Checkout, then JDK 21, Gradle setup and the Robolectric cache, with the same versions as `ci.yml`.
2. `scripts/check-release.sh "$GITHUB_REF_NAME"`.
3. `./gradlew lintDebug testDebugUnitTest verifyNoNetworkPermission`.
4. Decode the keystore into `$RUNNER_TEMP/upload.jks` with `umask 077`.
5. Run `./gradlew bundleRelease` with the four `ENCARTE_UPLOAD_*` variables.
6. Verify with `jarsigner -verify` and check the signer certificate's SHA-256 against `vars.UPLOAD_CERT_SHA256`.
7. Upload `app-release.aab` as the artifact `encarte-<versionName>-aab`, with a 30-day retention. The AAB carries its R8 mapping, so Play can deobfuscate crashes.
8. In an `if: always()` step, delete `$RUNNER_TEMP/upload.jks`.

## 7. Leak guard in normal CI

- `.gitignore` adds `*.jks`, `*.keystore`, `*.p12` and `*.pem`.
- `ci.yml` gets a first step that fails if `git ls-files` lists any file with those extensions. This is CI only, so it adds no Gradle task.
- Recommended to the user, as a setting they change themselves: enable GitHub **secret scanning** and **push protection** on the repository.

## 8. Site (`site/`, GitHub Pages)

**Pages:**
- `index.html`: icon, name, tagline ("Vos cartes de fidélité. Rien d'autre."), three promises (offline, no account, open source), and a source-code link.
  - Store links are added as plain text links once the listings exist, in a later small change.
  - No official badge images: they would add third-party artwork with its own usage rules.
- `privacy.html`: the privacy policy.
- English versions of both under `en/`.
- A language switch on every page.

**Privacy policy content,** in plain language:
- Encarté collects no data and sends nothing. It has no internet permission.
- The camera is used only to scan barcodes on the device, and no image leaves the device.
- Cards and photos stay in the app's private storage.
- Backups are files the user creates, saved where the user chooses. They are encrypted when the user sets a password.
- No analytics, ads, or third-party SDKs.
- Contact: the GitHub issue tracker.
- Effective date.

**Legal notice:** a short publisher and host section on the home page. The host is GitHub, Inc., with its postal address. The publisher identity is confirmed with the user during review.

**Constraints:**
- No JavaScript.
- No external font, script, image or analytics. Nunito is served from the site itself, and every asset uses a relative path.
- Brand palette from the visual identity spec, in light and dark themes (`prefers-color-scheme`).
- Readable on a phone.

**Build:**
- `scripts/build-site.sh` assembles `build/site/` from `site/`, copies `app/src/main/res/font/nunito.ttf` and the icon from `branding/icon.svg`, and writes `CNAME` with the content `encarte.fr`.
- There is one source per asset, and the same script serves for local preview.

**Publication:** `.github/workflows/pages.yml` runs on a push to `main` touching `site/**`, `scripts/build-site.sh` or the copied assets, or on manual dispatch. It runs the build script, then the official `actions/configure-pages`, `upload-pages-artifact` and `deploy-pages`, all pinned by SHA, with the minimal Pages permissions.

**DNS:** done by the user at their registrar. The guide gives the exact records:
- apex `A` records 185.199.108.153, 185.199.109.153, 185.199.110.153 and 185.199.111.153;
- apex `AAAA` records 2606:50c0:8000::153, 2606:50c0:8001::153, 2606:50c0:8002::153 and 2606:50c0:8003::153;
- `www` as a `CNAME` to `vferries.github.io`;
- a `TXT` record verifying the domain in the user's GitHub account, so no other account can claim `encarte.fr` on Pages;
- HTTPS enforced once the certificate is issued.

## 9. Store submission kits

**F-Droid:** the draft `docs/release/fdroid/io.github.vferries.encarte.yml` is the file the user's merge request adds to `fdroiddata`. It contains:
- license `GPL-3.0-or-later`;
- category chosen from fdroiddata's current list, with Catima's as reference;
- `WebSite`, `SourceCode`, `IssueTracker` and `Changelog` URLs;
- `RepoType: git`;
- one `Builds` entry for `1.0.0` / `10000` (`commit: v1.0.0`, `subdir: app`, `gradle: [yes]`);
- `AutoUpdateMode: Version` and `UpdateCheckMode: Tags`, plus the current version and code.

Texts and images come from the repository's `fastlane/metadata`. `docs/release/fdroid/README.md` lists the merge-request steps (fork, branch, add the file, open the MR, follow the pipeline).

**Risks to clear before the merge request,** checked during implementation:
1. F-Droid's build server must support AGP 9.4.1, compileSdk 37 and JDK 21.
2. The `zxing-cpp` AAR ships prebuilt native libraries, and F-Droid's inclusion policy may reject prebuilt binaries.

If either one blocks inclusion, implementation stops and the user decides. Replacing the scanner library is a separate project.

**Play:** `docs/release/play-console.md` is a step-by-step guide:
- create the app (`io.github.vferries.encarte`, free, default language fr-FR);
- store listing: the texts from `fastlane/metadata`, and the icon, feature graphic and screenshots from their paths;
- enroll in Play App Signing with the upload key;
- **Data safety:** no data collected or shared;
- content rating questionnaire answers;
- target audience 13+, outside the Families program;
- no ads;
- all functionality available without an account;
- category Shopping;
- contact details: the email is the user's choice;
- privacy policy URL `https://encarte.fr/privacy.html`;
- release flow: upload the AAB to the **internal testing** track, install from Play on a device, then promote to production.

## 10. Release checklist

`docs/release-checklist.md` is rewritten into three parts:

1. **Before the first public release:** the existing items, plus:
   - the two screenshot decisions;
   - domain DNS and verification;
   - secret scanning;
   - creating the `release` environment and its secrets;
   - generating the upload key;
   - recording `UPLOAD_CERT_SHA256`.
2. **Every release:**
   - acceptance checks;
   - bump `versionName` and `versionCode`;
   - write both changelogs;
   - `scripts/check-release.sh`;
   - commit and tag `vX.Y.Z`, then push the tag;
   - approve the `release` run;
   - download the AAB;
   - internal track, test, promote.
3. **After the first F-Droid inclusion:** nothing per release. F-Droid picks up new tags by itself, and you check that its build succeeded.

## 11. Verification

- **Signing config, tested locally** with a throwaway keystore generated in a temporary directory:
  - with all four variables, `bundleRelease` produces an AAB that `jarsigner -verify` accepts, signed by that certificate;
  - with none, the build succeeds unsigned;
  - with only some, the build fails and names the missing variables.
- **`scripts/check-release.sh`** is run against good and bad cases: wrong tag format, tag/version mismatch, wrong code, missing, empty or over-long changelog. Each bad case fails with its own message.
- **CI leak guard:** a temporary tracked `test.jks`, in a scratch branch or worktree, makes the check fail. The check passes on `main`.
- **Workflows:**
  - YAML is syntax-checked locally;
  - every `uses:` is pinned to a 40-character SHA;
  - `release.yml` has no `pull_request` trigger and declares `environment: release`.
- **Site:**
  - `scripts/build-site.sh` output is opened in headless Chrome at phone and desktop widths, in light and dark;
  - the pages make no request outside `build/site/`;
  - every internal link resolves.
- **First real run:** pushing `v1.0.0`. If the run fails before producing the artifact, fix and re-tag. Nothing is published until the user uploads the AAB.
