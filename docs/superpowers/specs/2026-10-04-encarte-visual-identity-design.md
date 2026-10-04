# Encarté — Visual Identity Design Spec

- **Date:** 2026-10-04
- **Status:** Draft, pending review
- **Parent spec:** `docs/superpowers/specs/2026-10-03-encarte-design.md`

## 1. Context and goals

v1 works on device but has no visual identity: it uses a placeholder launcher icon, Roboto, stock Material 3 shapes and no store graphics. This spec defines the identity before the first public release on F-Droid and Google Play. It also covers how the store graphics are produced.

Success criteria:

1. The launcher icon is the "fan of cards" (C3c) on cream. It stays inside the adaptive-icon safe zone, and it has a themed (monochrome) variant.
2. Every text style in the app uses the bundled Nunito font. No font is fetched at runtime and the app still has no network permission.
3. The store assets are reproducible from sources in the repo, in fr-FR and en-US:
   - the icon;
   - the feature graphic;
   - five screenshots per language.

## 2. Scope

**In scope:**
- launcher icon;
- typography;
- corner radii;
- empty-state illustration;
- Play/F-Droid icon, feature graphic and phone screenshots;
- the tooling that renders them.

**Out of scope:**
- **Brand colors in the UI.** Dynamic color stays, and the API < 31 fallback colors are unchanged.
- **Dialog corner radius.** It stays at the Material 3 default.
- **Custom splash screen.** The system splash already shows the new icon.
- **Visual assets not listed in scope:** wordmark, tablet screenshots, dark-mode screenshots.
- **Store submission itself.** That is the next phase.

## 3. Palette (brand graphics only)

| Name | Hex | Use |
|---|---|---|
| Cream | `#FFF6E8` | icon background, feature graphic, screenshot 3 |
| Teal | `#2E8C83` | back card, subtitle, screenshots 1 and 5 |
| Mustard | `#F2A93B` | middle card, screenshot 2 |
| Navy | `#1D2440` | outlines, bars, headline text, screenshot 4 |
| White | `#FFFFFF` | front card, captions on dark backgrounds |

These colors are used only in the icon, the empty-state illustration and the store graphics. The app UI keeps Material You.

## 4. Launcher icon

### 4.1 Artwork (C3c), 108 × 108 viewport

The base geometry uses card rectangles `x=30 y=34 w=48 h=32`, with corner radius 5:

| Layer | Fill | Stroke | Transform |
|---|---|---|---|
| Back card | Teal | none | `rotate(-16)` around (54, 70) |
| Middle card | Mustard | none | `rotate(-2)` around (54, 70) |
| Front card (group) | White | Navy, 2.5 | `rotate(12)` around (54, 70) |

The front card's barcode is drawn inside the front-card group:
- 10 Navy bars at `y=44`, each with `height=15`;
- `(x, width)` pairs: (36,2) (40,1) (43,3) (48,1) (51,2) (55,1) (58,3) (63,1) (66,2) (70,1).

**Safe-zone fit.** As drawn, the art's bounding box is centered at (53.1, 50.8). Its outer extent, stroke included, reaches 33.1 dp from the canvas center, and the safe-zone radius is 33 dp. To fit, the whole art is wrapped in one group that:
1. translates it so its bounding box is centered on (54, 54) (offset (+0.89, +3.23) before scaling);
2. scales it by 0.95 around (54, 54).

The resulting outer extent is ≤ 31.5 dp.

In a VectorDrawable, `<group>` applies scale before translate. The group is therefore `pivotX=54 pivotY=54 scaleX=scaleY=0.95 translateX=0.85 translateY=3.07`, and the post-scale offset is 0.95 × the pre-scale one.

### 4.2 Android resources

- **`res/values/ic_launcher_background.xml`:** set `ic_launcher_background` to `#FFF6E8`.
- **`res/drawable/ic_launcher_foreground.xml`:** the artwork from §4.1 as a VectorDrawable, 108 dp, viewport 108. The rounded rectangles become path data.
- **`res/drawable/ic_launcher_monochrome.xml`:** same viewport and outer group transform, single color (`#FF000000`; the system tints it):
  - the back and middle cards are outlines, stroke 4, no fill;
  - the front card is a filled rounded rectangle with the 10 bars cut out (`fillType="evenOdd"`).
- **`res/mipmap-anydpi/ic_launcher.xml`:** `<monochrome>` points to `ic_launcher_monochrome`.

minSdk is 26, so no raster mipmaps are needed.

### 4.3 Store icon

Play and F-Droid need a 512 × 512 PNG with no alpha. It is rendered from `branding/icon.svg`:
- a full-bleed Cream square;
- the same artwork and group transform;
- viewBox `18 18 72 72`, which is the visible adaptive area.

No rounded corners are baked in, because Play applies its own mask.

`branding/icon.svg` and the two VectorDrawables share the same numbers. A comment in each file points to the others.

## 5. Typography

- **Font file:** Nunito variable font (`Nunito[wght].ttf` from `github.com/google/fonts`, `ofl/nunito`), stored as `app/src/main/res/font/nunito.ttf`. The plan pins the upstream commit. Size is about 0.3 MB.
- **License:** SIL Open Font License 1.1.
  - The license text is committed at `branding/fonts/Nunito-OFL.txt`.
  - The font's name table carries the notice.
  - `third_party_licenses` (both locales use the untranslatable string) gains the line `Nunito — SIL Open Font License 1.1`.
- **`core/ui/theme/Type.kt`:** defines `NunitoFamily`, declaring the resource font at weights 400, 500, 700 and 800. It also defines `EncarteTypography`, a copy of the Material 3 default `Typography()` where every one of the 15 styles uses `NunitoFamily`:

| Styles | Weight |
|---|---|
| display\*, headline\*, title\* | 800 (ExtraBold) |
| body\* | 500 (Medium) |
| label\* | 700 (Bold) |

  Sizes and line heights stay at the Material 3 defaults.
- **`EncarteTheme`:** passes `typography = EncarteTypography`.
- **Variable-font API:** the plan verifies against Compose UI 1.12.1 that a resource `Font(...)` with a `FontWeight` selects the matching `wght` axis value without an experimental opt-in. If it does need one, the plan stops and reports back rather than adding `@OptIn`.

## 6. UI details

### 6.1 Corner radii

**`core/ui/theme/Shape.kt`** defines `EncarteShapes`, and `EncarteTheme` passes `shapes = EncarteShapes`:

| Token | Radius | Picked up by |
|---|---|---|
| extraSmall | 6 dp | `OutlinedTextField` (search field, edit form, password fields), menus, snackbars |
| small | 8 dp | buttons (explicitly, see below) |
| medium | 8 dp | card tiles |
| large | 8 dp | extended FAB |
| extraLarge | Material 3 default | dialogs |

**Buttons.** Material 3's `Button` and `OutlinedButton` do not read the theme shapes, so the following 7 call sites pass `shape = MaterialTheme.shapes.small`:

| File | Calls |
|---|---|
| `cards/list/CardListScreen.kt` | empty-state `Button` and `OutlinedButton` |
| `scan/ScannerScreen.kt` | two `OutlinedButton`s and one `Button` |
| `cards/edit/CardEditScreen.kt` | one `OutlinedButton` |
| `lock/LockScreen.kt` | one `Button` |

`TextButton`s keep the default.

**Card tiles.** `core/ui/CardTile.kt` replaces `RoundedCornerShape(12.dp)` with `MaterialTheme.shapes.medium`.

Existing explicit 8 dp clips (card display, edit-screen photos) and the circular color swatches stay as they are.

### 6.2 Card tiles

Tiles show the store name only, with no initial badge. This is the current behavior; only the radius changes (§6.1).

### 6.3 Empty-state illustration

- **`res/drawable/illustration_card_fan.xml`:** the same three cards, as a VectorDrawable with viewport 108 × 90.
  - Cards sit at `y=24`, rotated around (54, 60); bars at `y=34`.
  - The colors are fixed brand colors, so the illustration looks the same in light and dark themes.
- **`EmptyState` in `CardListScreen.kt`:** shows it above the title, 160 dp wide (aspect 108:90), with `contentDescription = null` because it is decorative.

## 7. Store assets

All store assets exist in two locales, fr-FR and en-US, and all are PNG without alpha. Output paths follow the fastlane layout, which both F-Droid and fastlane supply read:

```
fastlane/metadata/android/<locale>/images/icon.png              512 × 512
fastlane/metadata/android/<locale>/images/featureGraphic.png    1024 × 500
fastlane/metadata/android/<locale>/images/phoneScreenshots/1..5.png   1080 × 1920
```

### 7.1 Feature graphic (F1)

The layout is 1024 × 500 on a Cream background.

**Text, drawn at the canvas's real size:**

| Element | Font | Color | Position |
|---|---|---|---|
| Headline line 1 | Nunito 900, 54 px | Navy | x 64, baseline 205 |
| Headline line 2 | Nunito 900, 54 px | Navy | x 64, baseline 270 |
| Subtitle | Nunito 700, 24 px | Teal | x 66, baseline 330 |

**Fan.** The fan artwork (the illustration geometry, viewBox `0 0 108 90`) is placed at x 610, y 40, size 400 × 420.

| Locale | Line 1 | Line 2 | Subtitle |
|---|---|---|---|
| fr-FR | Vos cartes de fidélité. | Rien d'autre. | Hors-ligne · Sans compte · Libre |
| en-US | Your loyalty cards. | Nothing else. | Offline · No account · Open source |

### 7.2 Phone screenshots (S2)

**Layout,** 1080 × 1920:
- **Background:** a solid fill, set per shot.
- **Caption:** two lines, centered, Nunito 900, 76 px, line height 1.12, caption box top at y 150.
- **Phone frame:** generic, with no brand or model.
  - Shape: a `#111111` body 800 px wide, centered (x 140), top at y 470, outer radius 72 px, bezel 18 px.
  - Screen: the raw capture, scaled to 764 px wide, with a 54 px screen radius.
  - The frame runs off the bottom edge.

**Shots:**

| # | Screen | Background | Caption color | fr-FR | en-US |
|---|---|---|---|---|---|
| 1 | Card list with a Favorites section | Teal | White | Toutes vos cartes, / au même endroit | All your cards, / in one place |
| 2 | Card display (Boulangerie Martin, EAN-13) | Mustard | Navy | Présentez-la en caisse, / luminosité au max | Show it at checkout, / at full brightness |
| 3 | Scanner aimed at a demo card | Cream | Navy | Scannez une carte / en un instant | Scan a card / in seconds |
| 4 | Settings (backup and lock sections visible) | Navy | White | Sauvegarde chiffrée, / compatible Catima | Encrypted backups, / Catima-compatible |
| 5 | Empty state on first launch | Teal | White | Hors-ligne. / Aucun compte. | Offline. / No account. |

`/` marks the line break. White on Teal has a contrast ratio of 4.0:1, which is enough for large text.

**Why shot 5 is the empty state and not the lock screen.** When the app lock is enabled, `SecureWindowEffect` sets `FLAG_SECURE`, so `adb screencap` returns a black image. For the same reason, all captures are taken with the app lock **disabled**. Shot 4 still shows the lock setting.

### 7.3 Demo data

- **`branding/demo/cards.csv`:** a Catima v2 export, in the same format as `app/src/test/resources/catima/catima_v2.csv`.
  - It holds 8 fictional stores: Boulangerie Martin, Librairie du Coin, Fromagerie Dupont, Cinéma Lumière, Jardinerie des Lilas, Pharmacie du Centre, Atelier Vélo, Café des Arts.
  - Boulangerie Martin and Librairie du Coin are starred.
  - Formats are a mix of EAN-13 (with valid check digits), Code 128 and QR code.
  - Header colors are distinct `CardPalette.swatches` entries. There are no photos.
- **No real brands appear** in any store asset, to avoid trademark and impersonation rejections.
- **`branding/demo/card-face.html`:** renders a fictional card face for the scanner shot (Librairie du Coin, logo side, **no barcode**, so the scanner does not decode it and navigate away).

### 7.4 Capture procedure (semi-manual, documented in `branding/README.md`)

1. Start the `Pixel_10` AVD with `-read-only`, so the setup is discarded on exit.
   - Enable SystemUI demo mode: clock 12:00, full battery and signal, no notifications.
   - Set the wallpaper to a solid Teal image, so Material You derives a palette in the brand's tones.
2. Install the debug build.
3. Take shot 5 first, on an empty database.
4. Push `cards.csv` to `Download/`, import it through Settings → Import, and take shots 1, 2 and 4.
5. For shot 3, set the rendered card face as the emulator camera's virtual-scene wall poster (Extended controls → Camera), then aim at it.
   - Feasibility is checked first.
   - If it fails, shot 3 is captured on a physical device aimed at the card face shown on a computer screen.
6. `branding/capture.sh <locale> <n>` runs `adb exec-out screencap -p` and writes `branding/screenshots/raw/<locale>/<n>.png`.
7. Switch the emulator's system language and repeat for the other locale.

The raw captures are committed (demo data only), so caption or layout tweaks don't require recapturing.

### 7.5 Rendering

`branding/render.sh` regenerates every PNG in §7 from:
- the sources: `icon.svg`, `feature-graphic.html`, `screenshot.html`;
- the raw captures;
- the bundled font `app/src/main/res/font/nunito.ttf`, loaded through `@font-face` with a relative path, so there is no network access and no system font.

Steps:
1. Headless Google Chrome screenshots each template at its exact size.
   - The locale, caption and background are passed in the URL query string.
   - The flag `--allow-file-access-from-files` lets the templates load local files.
2. ImageMagick flattens each image (`-alpha off`).
3. `magick identify` checks the dimensions and the absence of alpha. The script fails on any mismatch.

## 8. Dependencies

- **No new Gradle dependency.**
- **New bundled asset:** Nunito (OFL-1.1), flagged here and listed in the in-app licenses.
- **Dev-only tools for `branding/`:** Google Chrome, ImageMagick and adb. They are not needed to build the app, and CI does not run them.

## 9. Verification

- **Unit test** `core/ui/theme/EncarteTypographyTest`: all 15 styles use `NunitoFamily`, with the weights from §5.
- **Build:** `./gradlew lintDebug testDebugUnitTest assembleRelease verifyNoNetworkPermission` is green.
- **On the S23:**
  - launcher icon under the device's mask;
  - themed icons on;
  - splash;
  - empty state in light and dark themes;
  - Nunito on list, display, edit, settings and lock screens;
  - 8 dp corners on tiles, the FAB and buttons;
  - 6 dp corners on the search field.
- **Store assets:** `branding/render.sh` passes its own size and alpha checks, and the user reviews all output PNGs before they are committed.
- **`docs/release-checklist.md`:** the "Final app icon" and "Store screenshots" items are ticked, and a "Feature graphic" item is added and ticked.
