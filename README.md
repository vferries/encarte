# Encarté

Offline-only loyalty card wallet for Android. Encarté stores your loyalty cards and shows their
barcode at checkout. It never connects to the network: the app has no `INTERNET` permission.

- Scan cards with the camera, or type them in.
- Back up and restore in Catima's export format (ZIP, optionally AES-encrypted).
- Optional app lock (biometrics or device credential).

## Build

Requires JDK 21 and the Android SDK (API 37).

    ./gradlew assembleDebug testDebugUnitTest lintDebug verifyNoNetworkPermission

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
