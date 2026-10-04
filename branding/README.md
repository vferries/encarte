# Branding

Sources for Encarté's identity and store graphics. Spec: `docs/superpowers/specs/2026-10-04-encarte-visual-identity-design.md`.

Palette (icon, empty-state illustration and store graphics only; the app UI keeps Material You):

| Name | Hex |
|---|---|
| Cream | `#FFF6E8` |
| Teal | `#2E8C83` |
| Mustard | `#F2A93B` |
| Navy | `#1D2440` |
| White | `#FFFFFF` |

## Font

`app/src/main/res/font/nunito.ttf` is the Nunito variable font (`Nunito[wght].ttf`, weights 200–1000), taken unmodified
from `github.com/google/fonts`, commit `8b0a1d0f5983c89bc2b93f1b5fb55f9e252744b5`, folder `ofl/nunito`
(SHA-256 `bb55a5ca5c2042335b3991af27c4d0705d0ef41cac6164ac737fd8f2a1e85207`).
It is licensed under the SIL Open Font License 1.1: see `fonts/Nunito-OFL.txt`.
The store templates load the same file, so the app and its graphics never drift apart.

## Icon

Three files share the launcher art's numbers (spec §4); change them together:

- `app/src/main/res/drawable/ic_launcher_foreground.xml`: color foreground of the adaptive icon (background `#FFF6E8`).
- `app/src/main/res/drawable/ic_launcher_monochrome.xml`: themed icon, one color; outlines behind the front cards are clipped.
- `icon.svg`: 512 × 512 store icon, the visible 72 × 72 area of the adaptive icon on full-bleed Cream.

`BrandDrawablesTest` renders both vectors and fails if any pixel leaves the 33 dp safe zone.

## Demo data

- `demo/cards.csv`: a Catima v2 export of 8 fictional stores; Boulangerie Martin and Librairie du Coin are starred.
  EAN-13 numbers use the in-store prefixes 20–29, so none is a real product code. `DemoCardsTest` checks that it
  imports and that every code is valid.
- `demo/card-face.html`: a fictional card face (logo side, no barcode) for the scanner shot.

No real brand may appear in a store asset.

## Capturing the raw screenshots

Needs the Android SDK `emulator` and `adb`, Google Chrome and ImageMagick 7. Captures are taken with the app lock
**off**: with it on, `FLAG_SECURE` makes `screencap` black (`capture.sh` refuses black captures).
If a phone is also plugged in, set `ANDROID_SERIAL=emulator-5554` first.

1. Render the card face for the camera poster, then start the emulator read-only (all setup is discarded on exit):

   ```bash
   P=$(mktemp -d)
   google-chrome --headless=new --user-data-dir="$P" --disable-gpu --hide-scrollbars --allow-file-access-from-files \
     --force-device-scale-factor=1 --virtual-time-budget=10000 --window-size=1011,638 \
     --screenshot="$P/card-face.png" "file://$PWD/branding/demo/card-face.html"
   __NV_PRIME_RENDER_OFFLOAD=1 __GLX_VENDOR_LIBRARY_NAME=nvidia \
     emulator -avd Pixel_10 -read-only -no-snapshot -gpu host -virtualscene-poster wall="$P/card-face.png" &
   adb wait-for-device shell 'while [ -z "$(getprop sys.boot_completed)" ]; do sleep 1; done'
   adb shell svc power stayon true
   ```

   `-gpu host` matters: with the default `auto`, the emulator distrusts the hybrid NVIDIA + AMD drivers, falls back
   to software rendering ("Switching to software rendering" in its log) and crawls. The two variables put it on the
   NVIDIA GPU; drop them on a single-GPU machine.

2. SystemUI demo mode (12:00, full battery and signal, no notifications):

   ```bash
   adb shell settings put global sysui_demo_allowed 1
   adb shell am broadcast -a com.android.systemui.demo -e command enter
   adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200
   adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
   adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e mobile show -e datatype none -e level 4
   adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
   ```

3. Solid Teal wallpaper, so Material You derives the app palette from the brand:

   ```bash
   magick -size 1080x2424 xc:'#2E8C83' "$P/teal.png"
   adb push "$P/teal.png" /sdcard/Pictures/teal.png
   ```

   There is no shell command to set a wallpaper: index the image, then hand it to the system's "Use as" chooser.

   ```bash
   adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/teal.png
   adb shell content query --uri content://media/external/images/media --projection _id:_data   # note teal.png's _id
   adb shell am start -a android.intent.action.ATTACH_DATA -t image/png --grant-read-uri-permission \
     -d content://media/external/images/media/<_id>
   ```

   In the chooser, tap **Wallpaper** → **Just once** → **SET WALLPAPER**.

   Also give the device a screen lock (`adb shell locksettings set-pin 1234`). Without one, Settings shows "Lock the
   app" disabled with "Set a screen lock on this device first" instead of its normal summary. The app lock itself
   stays off.

   Clear any notification left in the status bar before each capture (pull down the shade → **Clear all**). The
   ongoing "New ads privacy features" one only goes away when you open it and tap **Got it**.

4. Install and prepare the app (the demo data stays in the emulator; `pm clear` restarts from an empty wallet):

   ```bash
   ./gradlew installDebug
   adb push branding/demo/cards.csv /sdcard/Download/cards.csv
   ```

5. For each locale (`en-US`, then `fr-FR`):

   ```bash
   L=en-US   # then fr-FR
   adb shell pm clear io.github.vferries.encarte
   adb shell pm grant io.github.vferries.encarte android.permission.CAMERA
   adb shell cmd locale set-app-locales io.github.vferries.encarte --locales "$L"
   adb shell am start -n io.github.vferries.encarte/.MainActivity
   ```

   - Shot 5: the empty state, right after launch: `branding/capture.sh $L 5`.
   - Import (empty state) → Import cards → ☰ roots → Downloads → `cards.csv`.
   - Shot 4: Settings, once the "8 cards imported" snackbar has gone (backup and lock sections both show):
     `branding/capture.sh $L 4`.
   - Shot 1: back to the card list, with the Favorites section: `branding/capture.sh $L 1`.
   - Shot 2: open Boulangerie Martin (EAN-13): `branding/capture.sh $L 2`.
   - Shot 3: back, + → scanner: `branding/capture.sh $L 3`.

   For shot 3, aim the virtual camera at the wall poster once, in the emulator window, before the first locale:
   click into the screen, hold **Alt** and move the mouse to look around, and use **Alt + W/A/S/D** to move. Frame the
   blue "Librairie du Coin" card large and roughly square in the viewfinder. The pose persists while the emulator
   runs, so the second locale reuses it. No console command sets this pose.

The raw captures in `screenshots/raw/` are committed (demo data only), so caption or layout changes never need a
recapture.

## Rendering the store graphics

`branding/render.sh` regenerates all 14 PNGs in `fastlane/metadata/android/<fr-FR|en-US>/images/` (icon 512 × 512,
`featureGraphic.png` 1024 × 500, `phoneScreenshots/1..5.png` 1080 × 1920) from `icon.svg`, `feature-graphic.html`,
`screenshot.html` and the raw captures. Captions, colors and feature-graphic texts live in the tables at the bottom
of the two HTML templates.

It uses headless Google Chrome (with a throwaway profile) and ImageMagick 7. It fails if a template could not load
the bundled Nunito or its capture, if an image has the wrong size, or if it still has an alpha channel.
Neither the app build nor CI runs it. Set `CHROME=chromium` (or any path) if `google-chrome` is not installed.
