# Encarté

Offline-only loyalty card wallet for Android. Encarté stores your loyalty cards and shows their
barcode at checkout. It never connects to the network: the app has no `INTERNET` permission.

- Scan cards with the camera, or type them in.
- Back up and restore in Catima's export format (ZIP, optionally AES-encrypted).
- Optional app lock (biometrics or device credential).

## Build

Requires JDK 21 and the Android SDK (API 37).

    ./gradlew assembleDebug testDebugUnitTest lintDebug verifyNoNetworkPermission

## Development

- Unit and Robolectric tests: `./gradlew testDebugUnitTest`
- Instrumented tests (zxing-cpp decoding), with an emulator running: `./gradlew connectedDebugAndroidTest`
- Offline guardrail: `./gradlew verifyNoNetworkPermission` fails if any dependency adds a network permission.
- Design: `docs/superpowers/specs/2026-10-03-encarte-design.md`. Release steps: `docs/release-checklist.md`.

Backups use Catima's export format, so you can move between Encarté and
[Catima](https://catima.app) at any time.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
