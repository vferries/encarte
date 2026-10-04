# Release checklist

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
- [ ] App lock: enable, background for more than one minute, come back: the lock screen appears
      and the screen you were on is restored after unlocking. After unlocking, the screen you were
      on is shown immediately, with no flash of card content before the lock screen appears when
      returning to the app.
- [ ] Remove the device screen lock while the app lock is on: the app turns its lock off and says so.
- [ ] Rotate on every screen; "Don't keep activities" round-trip on the card display and the editor.
- [ ] Bump `versionCode` and `versionName` in `app/build.gradle.kts`.

## Before the first public release

- [ ] Trademark search for "Encarté" at INPI and EUIPO (classes 9 and 42).
- [ ] Check that `io.github.vferries.encarte` is free on Google Play and F-Droid.
- [ ] Create the GitHub repository `vferries/encarte` (the About screen links to it).
- [ ] Final app icon (replace the placeholder adaptive icon).
- [ ] Privacy policy page (Play requires one because of the CAMERA permission): "no data is collected".
- [ ] Play Data safety form: no data collected or shared.
- [ ] Play signing key, kept outside the repository.
- [ ] Store screenshots in `fastlane/metadata/android/*/images/phoneScreenshots/`.
