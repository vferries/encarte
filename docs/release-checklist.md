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
- [ ] After `v1.0.0`: open the F-Droid merge request (`docs/release/fdroid/README.md`).
- [ ] When the Play listing is live, remove `play-soon` from `<body>` in `site/index.html` and `site/en/index.html`;
      when F-Droid lists the app, remove `fdroid-soon` the same way (see `branding/README.md`).

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
- [ ] Approve the `Release` run on GitHub (environment `release`) only if its commit is the tagged commit on `main` and
      CI on `main` is green for it; the run must end green.
- [ ] If the run fails: fix on `main` and push a new patch version. Never move or re-push a tag that F-Droid may
      already have built. Before F-Droid inclusion and before any upload to Play, deleting and re-creating the same
      tag is acceptable.
- [ ] Download the `encarte-vX.Y.Z-aab` artifact; upload it to the Play internal testing track; install and smoke-test;
      promote to production (`play-console.md` §6).
- [ ] F-Droid (once included): check a few days later that the new version built on f-droid.org
      (nothing to submit; it follows the tag).
