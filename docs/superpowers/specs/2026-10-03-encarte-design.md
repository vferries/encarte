# Encarté — Design Spec

- **Date:** 2026-10-03
- **Status:** Draft, pending review
- **App name:** Encarté (ASCII form `encarte` in identifiers)
- **applicationId:** `io.github.vferries.encarte`
- **License:** GPL-3.0-or-later

## 1. Context and goals

The user currently keeps loyalty cards in Klarna, which increasingly pushes consumer credit, in-app payments and strong authentication. Encarté is a replacement that **only stores loyalty cards and shows them at checkout**, with **no network access at all**.

The app is meant to be **published** on F-Droid and Google Play, for a primarily French-speaking audience (English supported).

Success criteria:

1. A physical card can be added by scanning it, and the code shown by the app is read by a real checkout scanner.
2. The release APK cannot reach the network (no `INTERNET` permission in the merged manifest, enforced by the build).
3. A backup exported from Encarté restores identically (cards and photos), with or without a password.
4. A Catima export (plain or password-protected) imports into Encarté, and an Encarté export imports into Catima.
5. The codebase follows current Android recommendations (Compose, UDF, Room, DataStore, Navigation 3) and is 100 % FOSS so F-Droid accepts it without anti-features.

## 2. Non-goals (v1)

- iOS / multiplatform.
- Any network feature: sync, cloud backup, logo or brand lookup, crash reporting, analytics.
- Bundled brand logos: they would trigger the F-Droid *NonFreeAssets* anti-feature and risk Play Store IP takedowns. A separately-distributed, user-imported logo pack could be considered later.
- Balance, expiry date, groups and archive (supported by Catima, not by Encarté v1).
- Two-pane tablet layout, home-screen widgets, app shortcuts, Wear OS, image cropping UI.
- Importers other than Catima (e.g. FidMe). Klarna has no known export.

## 3. Constraints

- **Offline by construction:** no `INTERNET` / `ACCESS_NETWORK_STATE` permission. Both are explicitly stripped with `tools:node="remove"` in case a dependency declares them, and a Gradle check fails the build if the merged release manifest contains either.
- **100 % FOSS dependencies:** no Google Play Services, no ML Kit, no Firebase.
- **No Hilt:** manual dependency injection.
- `minSdk 26` (Android 8.0); `compileSdk` / `targetSdk` = latest stable at implementation time.
- English is the default locale (`values/`), with a complete French translation (`values-fr/`).

## 4. Dependencies

All are Apache-2.0 unless noted, and all are new to the project (which is empty). **Exact versions must be checked against the latest stable releases at implementation time, not taken from memory.**

| Purpose | Artifact(s) | Note |
|---|---|---|
| Language/build | Kotlin, AGP, KSP, Compose compiler plugin, kotlinx-serialization plugin | Gradle Kotlin DSL + version catalog |
| UI | Compose BOM: `ui`, `foundation`, `material3`; `activity-compose` | Icons: Material Symbols imported as vector drawables (no `material-icons-extended`) |
| Lifecycle | `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`, `lifecycle-process` | `ProcessLifecycleOwner` drives re-locking |
| Navigation | `navigation3-runtime`, `navigation3-ui`, `lifecycle-viewmodel-navigation3` | Keys must be `@Serializable` |
| Serialization | `kotlinx-serialization-json` | Nav keys + brand catalog |
| Persistence | `room-runtime`, `room-ktx`, `room-compiler` (KSP); `datastore-preferences` | Room schema exported from v1 |
| Camera | `camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-compose` | Compose-native viewfinder |
| Barcode decoding | `io.github.zxing-cpp:android` | Native code: adds roughly 1–2 MB per ABI |
| Barcode encoding | `com.google.zxing:core` | Pure Java |
| App lock | `androidx.biometric` | |
| Backup archive | `net.lingala.zip4j:zip4j` | AES ZIP, required to read encrypted Catima exports |
| CSV | `org.apache.commons:commons-csv` | RFC 4180, multi-line notes |
| Tests | JUnit 4, `kotlinx-coroutines-test`, Robolectric (MIT), `androidx.test` (core, ext-junit, runner), `compose-ui-test-junit4`, `room-testing` | |

## 5. Architecture

Single Gradle module `:app`, **package-by-feature**, unidirectional data flow (UDF).

```
io.github.vferries.encarte
├── EncarteApp.kt            Application; owns the AppContainer
├── AppContainer.kt          Manual DI: builds DB, DAOs, repositories, services once
├── MainActivity.kt          Single activity: edge-to-edge, lock gate, NavDisplay
├── navigation/              NavKey definitions, back stack, entry provider
├── core/
│   ├── barcode/             BarcodeFormat, BarcodeValidator, BarcodeEncoder (ZXing), BarcodeDecoder (zxing-cpp)
│   ├── data/                EncarteDatabase, Card, CardDao, CardRepository, ImageStore, converters
│   ├── prefs/               SettingsRepository (DataStore)
│   └── ui/                  Theme, card palette, shared composables (CardTile, BarcodeImage)
├── brands/                  Brand, BrandCatalog (assets/brands.json)
├── cards/
│   ├── list/                CardListScreen, CardListViewModel
│   ├── display/             CardDisplayScreen, CardDisplayViewModel
│   └── edit/                CardEditScreen, CardEditViewModel
├── scan/                    ScannerScreen, ScannerViewModel, BarcodeAnalyzer
├── backup/                  CatimaCsv, CatimaArchive, BackupService, ImportResult/ExportResult
├── lock/                    LockManager, LockScreen
└── settings/                SettingsScreen, SettingsViewModel
```

Layering (no separate domain/use-case layer; it would add nothing at this size):

- **UI:** each screen has a stateful `XxxRoute` that collects the ViewModel state with `collectAsStateWithLifecycle()`, and a stateless `XxxScreen(state, callbacks…)` used by previews and UI tests.
- **ViewModel:** exposes one `StateFlow<XxxUiState>`, plus functions for user actions. One-shot outcomes (saved, deleted, import finished) are modelled as state fields that the UI consumes and acknowledges, not as event channels.
- **Data:** repositories and services expose `Flow`s and `suspend` functions. They are the only code that touches Room, DataStore and the file system.

**Manual DI.** `AppContainer` is created in `EncarteApp.onCreate()` and holds singletons. ViewModels are created with `viewModelFactory { initializer { … } }`, so arguments such as `cardId` are passed through the constructor. With Navigation 3, ViewModels are scoped to their back-stack entry via the ViewModel nav-entry decorator.

**Navigation 3.** `@Serializable` keys:

- `CardList`
- `CardDisplay(cardId: Long)`
- `Scanner`
- `CardEdit(cardId: Long? = null, barcodeValue: String? = null, barcodeFormat: BarcodeFormat? = null)`
- `Settings`

The back stack comes from `rememberNavBackStack` and survives process death. Predictive back is supported.

## 6. Data model

A single Room table, `cards`. `Card` is both the Room entity and the model passed to the UI: one class is enough at this size, and it can be split later if the layers diverge.

| Field | Type | Notes |
|---|---|---|
| `id` | `Long` | Auto-generated primary key |
| `storeName` | `String` | Required, trimmed, non-blank |
| `cardNumber` | `String` | Required, trimmed, non-blank. Human-readable number shown under the code. Catima rejects cards without a `cardid`, so it is required here too to keep exports importable |
| `barcodeValue` | `String?` | Encoded value **if it differs** from `cardNumber`; `null` means the code encodes `cardNumber` |
| `barcodeFormat` | `BarcodeFormat?` | `null` means no barcode (only the number is displayed) |
| `note` | `String` | Free text; empty by default |
| `color` | `Int` | ARGB tile color |
| `isFavorite` | `Boolean` | Favorites are pinned at the top of the list |
| `frontImage` | `String?` | File name inside `filesDir/images/` |
| `backImage` | `String?` | File name inside `filesDir/images/` |
| `createdAt` | `Instant` | |
| `lastUsedAt` | `Instant?` | Set each time CardDisplay opens |

- Converters: `Instant` is stored as epoch milliseconds. `BarcodeFormat` is stored by `name`; an unknown name (a future value read by an older app) becomes `null` and is logged.
- `BarcodeFormat` is Encarté's own enum, limited to what ZXing can **encode**: `QR_CODE`, `AZTEC`, `DATA_MATRIX`, `PDF_417`, `EAN_13`, `EAN_8`, `UPC_A`, `UPC_E`, `CODE_128`, `CODE_39`, `CODE_93`, `CODABAR`, `ITF`. It is mapped to and from ZXing / zxing-cpp types only inside `core/barcode`.
- Preferences (DataStore): `sortOrder` (`NAME` | `RECENTLY_USED`, default `NAME`) and `lockEnabled` (default `false`).

Sorting and search run in Kotlin over the full card list. A user has at most hundreds of cards, and this lets sorting use `java.text.Collator` for locale-aware, accent-insensitive results ("Écomarché" sorts with E). Search matches a normalized query (lowercased, accents stripped) against `storeName` and `cardNumber`.

## 7. Screens

### 7.1 CardList (start destination)

- A grid of tiles at roughly credit-card aspect ratio (1.586:1).
- Each tile shows the front photo (`ContentScale.Crop`) if there is one. Otherwise it is a tile in `color` with the store name, in black or white depending on the color's luminance.
- Favorites section first, then the other cards in the selected sort order.
- Top bar: search field, sort menu, Settings.
- FAB "+" opens the Scanner.
- Empty state: an "Add a card" button, and an "Import (Catima / backup)" button that goes to the import flow.

### 7.2 CardDisplay (checkout screen)

- Store name, the barcode as large as the screen allows, and the card number below it (selectable text). The front and back photos appear as thumbnails; tapping one opens it full screen.
- While this screen is shown, the window's `screenBrightness` is set to `BRIGHTNESS_OVERRIDE_FULL` and the screen is kept on. Both are restored when the screen leaves composition (`DisposableEffect`).
- **The barcode is always drawn black on white, with a quiet zone, whatever the theme.** It is rendered at an integer pixel scale with no filtering, so bars stay crisp.
- Actions: edit, toggle favorite, delete (with a confirmation dialog). Deleting a card also deletes its image files.
- Opening the screen updates `lastUsedAt`.
- If `barcodeFormat` is `null`, only the number is shown, in large text.

### 7.3 Scanner

- A CameraX preview (`camera-compose` viewfinder) with an `ImageAnalysis` use case using `STRATEGY_KEEP_ONLY_LATEST`. Each frame is decoded with zxing-cpp off the main thread.
- The first successful decode navigates to `CardEdit(barcodeValue, barcodeFormat)`, prefilled. If the decoded format is not in `BarcodeFormat` (e.g. GS1 DataBar), the value goes into `cardNumber` with no format, and CardEdit shows a notice that this code type cannot be displayed.
- Buttons: torch toggle, "Enter manually" (goes to an empty CardEdit), "From an image" (Photo Picker, which needs no permission; the picked image is decoded with zxing-cpp, and if nothing is found a message is shown).
- The camera permission is requested on entering the screen. If it is denied, the screen shows the rationale and the two alternatives instead of the preview. "Don't ask again" is handled with a link to the app's system settings.

### 7.4 CardEdit (create / edit)

- Fields:
  - store name, with autocomplete from `BrandCatalog`;
  - card number (required);
  - barcode format (a dropdown that includes "None");
  - an "Encoded value differs from number" advanced toggle, which reveals `barcodeValue`;
  - color (a fixed palette of 12 swatches; no color-picker dependency);
  - note;
  - front and back photos (take a photo, pick an image, or remove).
- **Live barcode preview.** The value is validated against the format (EAN/UPC check digit and length, character sets for Code 39/93, Codabar, ITF, and so on). The field shows an error and Save is disabled while the value is invalid.
- Picking a brand suggestion sets the store name and the brand color. Typing a name that normalizes to a known brand sets the brand color, unless the user has already chosen a color manually. Unknown names get a deterministic palette color derived from the name's hash.
- Photos: "Take a photo" uses `ActivityResultContracts.TakePicture` to a `FileProvider` URI in `cacheDir`. "Pick" uses `PickVisualMedia`. Images are downscaled (longest side 1600 px), re-encoded as JPEG (quality 85), and saved under a UUID file name by `ImageStore`. Replaced and removed images are deleted on save.
- Back with unsaved changes asks for confirmation.

### 7.5 Settings

- App lock toggle (see §10).
- Backup: "Export…", "Import…" (the same action imports Encarté backups and Catima exports, since they share a format).
- About: version, GPL-3.0 license, a source code link (opens the browser through an `Intent`; the app itself still makes no network call), and third-party licenses (a static screen listing the dependencies above).

### 7.6 Cross-cutting UI

- Edge-to-edge layout.
- Material 3 with dynamic color on Android 12+, and a fallback color scheme otherwise. Light and dark themes follow the system.
- Content descriptions on every icon-only button. The barcode image gets a description that states its format and number.
- Layouts must not break on large screens (content width is capped), but there is no dedicated two-pane layout.

## 8. Barcode handling

- **`BarcodeEncoder`** wraps ZXing's `MultiFormatWriter`, producing a `BitMatrix`, then an `ImageBitmap`. It runs on `Dispatchers.Default` and is cached per (value, format, size). It throws a typed error for invalid input, which `BarcodeValidator` should already have prevented.
- **Charset policy (2D formats: QR, Aztec, DataMatrix, PDF417):** ASCII content is encoded with ZXing's default charset. Non-ASCII content is encoded as UTF-8 (the `CHARACTER_SET` hint). 1D formats only accept their own character sets, which `BarcodeValidator` enforces. The charset is derived from the content and is not stored.
- **`BarcodeDecoder`** wraps zxing-cpp for both `ImageProxy` (live camera) and `Bitmap` (picked image), and returns a decoded value with zxing-cpp's format, mapped to `BarcodeFormat?`.
- **`BarcodeValidator`** is pure Kotlin with no Android dependencies, and is unit-tested.

## 9. Brand catalog

- `assets/brands.json` contains `[{ "name": "Carrefour", "aliases": ["Carrefour Market", "Carrefour City"], "color": "#RRGGBB" }, …]`, with roughly 100–150 common French retail brands for v1.
- Only names and colors are bundled, never logos. Colors are approximations of the brands' primary colors, and the user can always override them.
- `BrandCatalog` loads the file lazily once and exposes:
  - `suggest(query): List<Brand>` (normalized prefix match first, then substring match, at most 5 results);
  - `match(name): Brand?` (exact match on the normalized name or an alias).

## 10. App lock and privacy

- **Lock (opt-in, off by default).**
  - Authentication uses `BiometricPrompt` with `BIOMETRIC_WEAK | DEVICE_CREDENTIAL`. `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` is not supported on API 28–29.
  - Enabling the lock requires one successful authentication first. The toggle is disabled, with an explanation, when `BiometricManager.canAuthenticate()` reports no device credential.
  - `LockManager` keeps the locked/unlocked state in memory:
    - it starts locked whenever `lockEnabled` is true, which covers process death;
    - it records the time on `ProcessLifecycleOwner` `ON_STOP`;
    - it locks again on `ON_START` if more than 60 s have passed.
  - While locked, `MainActivity` composes only `LockScreen` (an "Unlock" button that re-opens the prompt) and never the navigation content.
  - **Lockout prevention:** if the lock is enabled but the device no longer has any credential, the lock is disabled automatically and the user is told why.
  - While the lock is enabled, `FLAG_SECURE` is set, which hides the app from the recents preview and blocks screenshots.
  - **Stated limitation:** the lock gates the UI only. Data at rest relies on Android file-based encryption; the database is not encrypted by the app (SQLCipher is out of scope).
- **Android Auto Backup.**
  - `dataExtractionRules` (API 31+): exclude all domains from `cloud-backup`, allow `device-transfer`.
  - `fullBackupContent` (API ≤ 30): exclude all domains, because cloud and device-to-device transfer cannot be separated there.
- **Permissions:** `CAMERA` (with `<uses-feature android:name="android.hardware.camera" android:required="false"/>`), and `USE_BIOMETRIC` (merged from androidx.biometric). Nothing else.
- **No telemetry and no crash reporting.**

## 11. Backup and import (Catima v2 format)

The **Catima v2 export format is Encarté's native backup format**. This gives one code path for "restore my backup" and "import from Catima", and lets users move to Catima at any time.

### 11.1 Format

A ZIP archive containing:

- `catima.csv` (RFC 4180, UTF-8), in three sections separated by an empty line:
  1. A version record: `2`.
  2. A groups section: header `_id`, then one row per group. Encarté writes the header only.
  3. A cards section with the header `_id, store, note, validfrom, expiry, balance, balancetype, cardid, barcodeid, barcodetype, barcodeencoding, headercolor, starstatus, lastused, archive`.
  4. A card-to-group mapping section: header `cardId, groupId`. Encarté writes the header only.
- Images named `card_<id>_front.png`, `card_<id>_back.png` (and `card_<id>_icon.png`, which Encarté ignores on import). Catima's importer accepts only names matching `^card_\d+_(front|back|icon)\.png$`.
- **No other entries.** Catima's importer rejects any unexpected file in the archive, so Encarté must never add extra entries (such as a sidecar `encarte.json`).
- With a password, every entry is encrypted with zip4j AES-256. ZIP-AES does not hide entry names; this is acceptable and is stated in the export UI.

The importer also accepts, as Catima does:

- **Catima v1** files: a single cards section with a header and no version line. The version is the leading run of digits before the first whitespace, or 1 if there is none.
- A **bare CSV file** (not zipped), from older Catima versions.

Unlike Catima, Encarté's importer ignores (and logs) unexpected ZIP entries instead of failing.

### 11.2 Field mapping

| Catima | Encarté | Import | Export |
|---|---|---|---|
| `_id` | — | Local key used to match image files only | Room `id` |
| `store` | `storeName` | Required; a row without it is rejected | ✓ |
| `note` | `note` | ✓ | ✓ |
| `cardid` | `cardNumber` | Required; a row without it is rejected (same rule as Catima) | ✓ |
| `barcodeid` | `barcodeValue` | Empty means `null` | `null` is written as empty |
| `barcodetype` | `barcodeFormat` | ZXing enum name; empty or unknown means `null` (logged) | `name` or empty |
| `barcodeencoding` | — | Ignored (the charset is derived from the content, §8) | `UTF-8` for a 2D code with non-ASCII content, empty otherwise (Catima then uses its default, ISO-8859-1) |
| `headercolor` | `color` | Empty means a color is derived from the name | ARGB int |
| `starstatus` | `isFavorite` | `1` means `true` | `1` / `0` |
| `lastused` | `lastUsedAt` | **Unix seconds**; `0` means `null` | Unix seconds, `0` if `null` |
| `validfrom`, `expiry` | — | **Epoch millis**; appended to `note` as localized lines ("Valid from: …", "Expires: …") | Empty |
| `balance`, `balancetype` | — | Appended to `note` when the balance is non-zero ("Balance: 12.50 EUR" or "Balance: 120 points") | `0` / empty |
| `archive` | — | Ignored (archived cards are imported as normal cards) | `0` |
| groups and mappings | — | Ignored | Headers only |
| front / back images | `frontImage` / `backImage` | Decoded, downscaled and re-encoded through `ImageStore` | Re-encoded to PNG |

`createdAt` is not exported; on import it is set to the import time.

### 11.3 Flows

- **Export:**
  1. The user picks a destination with `CreateDocument("application/zip")` (default name `encarte-backup-YYYY-MM-DD.zip`) and optionally enters a password, with a confirmation field.
  2. The archive is written to a temporary file in `cacheDir`, then copied to the chosen URI.
  3. The temporary file is deleted in every case.
- **Import:**
  1. The user picks a file with `OpenDocument`, and it is copied to `cacheDir` (zip4j needs random access, and a retry after a wrong password must not re-open the picker).
  2. If any entry is encrypted, the user is asked for the password. A wrong password shows an error and asks again.
  3. The CSV and the images are parsed and validated completely before anything is written.
  4. **Merge, not replace:** a card whose normalized `storeName` and `cardNumber` both match an existing card is skipped.
  5. **All or nothing:** images are first written to a staging directory, cards are inserted in a single Room transaction, and the staged images are then moved into `images/`. Any failure rolls back the transaction and deletes the staging directory.
  6. Result summary: "N cards imported, M duplicates skipped".
- **Guards:** at most 10,000 cards and 500 MB of uncompressed data per archive; anything beyond is rejected as invalid.

### 11.4 Results

`ImportResult` is a sealed type: `Success(imported, skippedDuplicates)`, `PasswordRequired`, `WrongPassword`, `UnsupportedVersion(version)`, `Invalid(reason)`, `IoError`. `ExportResult` is `Success(count)` or `IoError`. Each value maps to a user-facing message.

## 12. Error handling and logging

- Every `catch` block logs through `android.util.Log`, with a class-level tag, and either surfaces an error state or rethrows. Exceptions are never swallowed silently.
- **Card numbers, barcode values, notes and passwords are never logged.** Log card ids instead.
- Unexpected branches (an unknown enum value, an unmapped decoded format, a missing image file referenced by a card) are logged at warning level and handled gracefully: the format is treated as missing, the image as absent.
- **Orphan cleanup:** at startup, a background job deletes files in `images/` that no card references, and anything left in the staging and cache directories.

## 13. Testing strategy

**JVM unit tests (`test/`):**

- `BarcodeValidator`: valid and invalid values for each format, including EAN-13, EAN-8, UPC-A and UPC-E check digits.
- `BarcodeFormat` mapping to and from ZXing.
- `CatimaCsv`: read v1 and v2, write v2, quoted fields, multi-line notes, empty columns, unknown barcode types, millisecond and second timestamps.
- `BackupService` round trip: export then import gives identical cards and images, with and without a password. This runs on Robolectric, with a real Room database in memory and a temporary directory for files.
- Fixtures: Catima's own test CSVs (v1 and v2, GPL-3.0 like Encarté, with attribution) copied into `test/resources/catima/`. ZIP archives, plain and encrypted, are built from these CSVs inside the tests with zip4j.
- `BrandCatalog`: normalization, prefix and alias matching.
- ViewModels: with fake repositories and `kotlinx-coroutines-test`.

**Robolectric tests (`test/`):**

- `CardDao` queries, and Room migration tests from v1 onward (`room-testing`, exported schema).
- Compose UI tests of the stateless screens: CardList (empty, populated, search), CardEdit (validation disables Save), CardDisplay.

**Instrumented tests (`androidTest/`, on an emulator):**

- `BarcodeDecoder` against reference images of each supported format (zxing-cpp's native library does not run on the JVM).
- An encode-then-decode round trip for every `BarcodeFormat`, using `BarcodeEncoder` and then `BarcodeDecoder`.

**Build checks:**

- The `verifyNoNetworkPermission` Gradle task inspects the merged release manifest and fails if it contains `INTERNET` or `ACCESS_NETWORK_STATE`.

**Manual acceptance (before the first release):**

- A scanned physical card is read by a real checkout scanner.
- An Encarté export imports into Catima, installed on an emulator.

## 14. Build, CI and publishing

- Gradle Kotlin DSL, `gradle/libs.versions.toml`, KSP for Room, Room schema exported to `app/schemas/`.
- Release builds use R8 minification and resource shrinking. No build timestamps or other non-deterministic values in `BuildConfig` (keeps the door open to F-Droid reproducible builds later).
- **CI (GitHub Actions, approved):** on push and pull request, run `assembleDebug`, `lint`, `testDebugUnitTest` and `verifyNoNetworkPermission`. Instrumented tests are not run in CI for v1.
- `fastlane/metadata/android/{en-US,fr-FR}/` directory structure, read by F-Droid. The listing text and screenshots come later.
- Repository files: `LICENSE` (GPL-3.0), `README.md`.

**Before the first public release (outside the v1 code scope):**

- INPI / EUIPO trademark search for "Encarté" (classes 9 and 42).
- Check that `io.github.vferries.encarte` is free on both stores.
- Final app icon (v1 ships a placeholder adaptive icon).
- A privacy policy page: Play requires one because of the `CAMERA` permission. Its content is "no data collected".
- The Play Data safety form ("no data collected or shared").
- Play signing key management (keystore kept outside the repository).

## 15. Open points to verify during implementation

- Latest stable versions of every dependency, and the current Navigation 3 and androidx.biometric APIs (e.g. whether `BiometricPrompt` still requires a `FragmentActivity`). Both are being checked while the implementation plan is written.
- Resolved on 2026-10-03 by reading Catima's `CatimaImporter`:
  - an empty `barcodeencoding` is accepted;
  - unknown ZIP entries are rejected;
  - `cardid` is required;
  - `validfrom` and `expiry` are read as epoch milliseconds.
