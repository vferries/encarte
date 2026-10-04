# Encarté Visual Identity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Encarté its visual identity (fan-of-cards launcher icon, bundled Nunito, tighter corners, empty-state illustration) and produce reproducible fr-FR/en-US store graphics.

**Architecture:** The app changes stay in the Compose theme (`core/ui/theme`), a few call sites, and Android vector resources; Robolectric renders the vectors to check geometry and colors. Store graphics are HTML/SVG templates under `branding/`, rendered by headless Chrome and checked by ImageMagick from `branding/render.sh`; raw phone captures come from the `Pixel_10` emulator via `branding/capture.sh`.

**Tech Stack:** Kotlin 2.4.20, Compose BOM 2026.09.00 (UI 1.12.1, Material 3 1.4.0), Robolectric 4.17 (native graphics), Android VectorDrawable, Google Chrome 151 headless, ImageMagick 7.1, adb/emulator.

**Spec:** `docs/superpowers/specs/2026-10-04-encarte-visual-identity-design.md`

## Global Constraints

- No new Gradle dependency. The only new bundled asset is Nunito (SIL Open Font License 1.1).
- No font is fetched at runtime; the app keeps no network permission (`verifyNoNetworkPermission` stays green).
- minSdk 26: no raster mipmaps.
- Brand palette, used only in the icon, the empty-state illustration and store graphics: Cream `#FFF6E8`, Teal `#2E8C83`, Mustard `#F2A93B`, Navy `#1D2440`, White `#FFFFFF`.
- The app UI keeps Material You dynamic color; the API < 31 fallback colors in `Theme.kt` are unchanged.
- Dialog corner radius stays the Material 3 default (`extraLarge` untouched). `TextButton`s keep their default shape.
- Store PNGs have no alpha: icon 512 × 512, feature graphic 1024 × 500, phone screenshots 1080 × 1920, in `fastlane/metadata/android/<fr-FR|en-US>/images/`.
- No real brand appears in any store asset.
- `branding/` tools (Google Chrome, ImageMagick 7, adb) are dev-only: the app build and CI never run them.
- The Compose font API needs no `@OptIn`; never add one. Correction found in Task 1: the short `Font(resId, weight)` overload leaves `variationSettings` empty, so `Type.kt` passes `variationSettings = FontVariation.Settings(weight, FontStyle.Normal)` explicitly through a private `nunitoFont(weight)` helper.
- Commit directly on `main` (personal project), conventional prefixes (`feat:`, `fix:`, `docs:`, `chore:`), **no `Co-Authored-By` trailer**.
- Comments explain why, not what; match the surrounding code's density and idiom.

## Review Focus

1. **Launcher masks clip the art.** A circle mask must never cut a card corner. Pinned by `BrandDrawablesTest.*StaysInsideTheSafeZone` (Task 3): every painted pixel of the foreground and monochrome icons lies within 33 dp of the center.
2. **Themed icon bars look broken.** Drawn literally as the spec words it, the back card's 4-unit outline crosses the front card's bar cut-outs (computed: it crosses bar x=66). The plan clips the outlines behind the cards in front, like the color icon. Pinned by `BrandDrawablesTest.monochromeBarsShowNothingBehindThem` (Task 3).
3. **Variable font renders every weight as Regular.** If a declared weight doesn't pin the `wght` axis, headings lose their ExtraBold look with no error. Pinned by `EncarteTypographyTest.eachWeightPinsTheVariableAxis` (Task 1).
4. **Demo wallet doesn't import cleanly or shows "This code can't be displayed".** A wrong EAN-13 check digit or unknown type would ruin screenshot 2. Pinned by `DemoCardsTest` (Task 5).
5. **Store graphics silently use a fallback font.** Chrome blocks `file://` fonts without `--allow-file-access-from-files`, and a moved font path falls back to a system font with no error. Pinned by the `data-ready` check in `render.sh` (Task 7), proven by breaking the font path once.

**Deviations from the spec, on purpose:**
- §4.2 monochrome: back and middle outlines get `<clip-path>`s so nothing shows through the bar cut-outs (Review Focus 2). Shapes, strokes and fill types are as specified.
- §7.5: captions, colors and feature-graphic texts live in tables inside the templates, keyed by the `locale` (and `n`) query parameters, instead of being passed whole in the query string. That way each asset's copy lives in the file that renders it, and no URL encoding is needed in bash.
- §7.4 step 7: the locale is switched per app (`cmd locale set-app-locales`). The captures only show app UI and an empty status bar. If the command has no effect on this image, fall back to the system language in Settings.

---

### Task 1: Bundled Nunito typography

**Files:**
- Create: `app/src/main/res/font/nunito.ttf` (binary, downloaded)
- Create: `branding/fonts/Nunito-OFL.txt` (downloaded, verbatim)
- Create: `branding/README.md`
- Create: `app/src/main/java/io/github/vferries/encarte/core/ui/theme/Type.kt`
- Modify: `app/src/main/java/io/github/vferries/encarte/core/ui/theme/Theme.kt`
- Modify: `app/src/main/res/values/strings.xml:118` (`third_party_licenses`)
- Create: `app/src/test/java/io/github/vferries/encarte/core/ui/theme/EncarteTypographyTest.kt`
- Modify: `app/src/test/java/io/github/vferries/encarte/settings/SettingsScreenTest.kt:80-86`

**Interfaces:**
- Produces: `val NunitoFamily: FontFamily`, `val EncarteTypography: Typography` (package `io.github.vferries.encarte.core.ui.theme`); `R.font.nunito`. Task 2 edits the same `MaterialTheme(...)` call in `Theme.kt`.

- [ ] **Step 1: Download the pinned font and license**

Upstream: `github.com/google/fonts`, commit `8b0a1d0f5983c89bc2b93f1b5fb55f9e252744b5`, folder `ofl/nunito`.

```bash
cd /home/vincent/projects/fidelio
C=8b0a1d0f5983c89bc2b93f1b5fb55f9e252744b5
mkdir -p app/src/main/res/font branding/fonts
curl -sSfL -o app/src/main/res/font/nunito.ttf "https://raw.githubusercontent.com/google/fonts/$C/ofl/nunito/Nunito%5Bwght%5D.ttf"
curl -sSfL -o branding/fonts/Nunito-OFL.txt "https://raw.githubusercontent.com/google/fonts/$C/ofl/nunito/OFL.txt"
sha256sum app/src/main/res/font/nunito.ttf branding/fonts/Nunito-OFL.txt
```

Expected:
```
bb55a5ca5c2042335b3991af27c4d0705d0ef41cac6164ac737fd8f2a1e85207  app/src/main/res/font/nunito.ttf
580df76c95a1ec5ab878ceb25bb3d85c6a076804e9c970c8c6972aea775fdf65  branding/fonts/Nunito-OFL.txt
```
If a hash differs, stop and report (do not continue with another file).

- [ ] **Step 2: Write the failing typography test**

Create `app/src/test/java/io/github/vferries/encarte/core/ui/theme/EncarteTypographyTest.kt`:

```kotlin
package io.github.vferries.encarte.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import io.github.vferries.encarte.R
import org.junit.Assert.assertEquals
import org.junit.Test

class EncarteTypographyTest {
    private fun Typography.styles(): Map<String, TextStyle> = mapOf(
        "displayLarge" to displayLarge, "displayMedium" to displayMedium, "displaySmall" to displaySmall,
        "headlineLarge" to headlineLarge, "headlineMedium" to headlineMedium, "headlineSmall" to headlineSmall,
        "titleLarge" to titleLarge, "titleMedium" to titleMedium, "titleSmall" to titleSmall,
        "bodyLarge" to bodyLarge, "bodyMedium" to bodyMedium, "bodySmall" to bodySmall,
        "labelLarge" to labelLarge, "labelMedium" to labelMedium, "labelSmall" to labelSmall,
    )

    private val styles = EncarteTypography.styles()

    @Test
    fun everyStyleUsesNunito() {
        assertEquals(15, styles.size)
        styles.forEach { (name, style) -> assertEquals(name, NunitoFamily, style.fontFamily) }
    }

    @Test
    fun weightsFollowTheTextRole() {
        styles.forEach { (name, style) ->
            val expected = when {
                name.startsWith("body") -> FontWeight.Medium
                name.startsWith("label") -> FontWeight.Bold
                else -> FontWeight.ExtraBold
            }
            assertEquals(name, expected, style.fontWeight)
        }
    }

    @Test
    fun sizesAndLineHeightsStayMaterialDefaults() {
        val defaults = Typography().styles()
        styles.forEach { (name, style) ->
            assertEquals(name, defaults.getValue(name).fontSize, style.fontSize)
            assertEquals(name, defaults.getValue(name).lineHeight, style.lineHeight)
        }
    }

    @Test
    fun eachWeightPinsTheVariableAxis() {
        val fonts = (NunitoFamily as FontListFontFamily).fonts.map { it as ResourceFont }
        assertEquals(listOf(400, 500, 700, 800), fonts.map { it.weight.weight })
        fonts.forEach { font ->
            assertEquals(R.font.nunito, font.resId)
            // One variable file serves every weight: without this setting, all of them render at the file's default.
            assertEquals(FontVariation.Settings(font.weight, font.style), font.variationSettings)
        }
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew testDebugUnitTest --tests 'io.github.vferries.encarte.core.ui.theme.EncarteTypographyTest'`
Expected: compilation FAILS with `Unresolved reference 'EncarteTypography'` / `'NunitoFamily'`.

- [ ] **Step 4: Implement `Type.kt`**

Create `app/src/main/java/io/github/vferries/encarte/core/ui/theme/Type.kt`:

```kotlin
package io.github.vferries.encarte.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.vferries.encarte.R

/** Bundled variable font: each declared weight pins the file's wght axis, so no font is ever downloaded. */
val NunitoFamily = FontFamily(
    Font(R.font.nunito, FontWeight.Normal),
    Font(R.font.nunito, FontWeight.Medium),
    Font(R.font.nunito, FontWeight.Bold),
    Font(R.font.nunito, FontWeight.ExtraBold),
)

private fun TextStyle.nunito(weight: FontWeight) = copy(fontFamily = NunitoFamily, fontWeight = weight)

// Heavy rounded headings carry the identity; Medium body text keeps Nunito's thin strokes legible at small sizes.
val EncarteTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.nunito(FontWeight.ExtraBold),
        displayMedium = displayMedium.nunito(FontWeight.ExtraBold),
        displaySmall = displaySmall.nunito(FontWeight.ExtraBold),
        headlineLarge = headlineLarge.nunito(FontWeight.ExtraBold),
        headlineMedium = headlineMedium.nunito(FontWeight.ExtraBold),
        headlineSmall = headlineSmall.nunito(FontWeight.ExtraBold),
        titleLarge = titleLarge.nunito(FontWeight.ExtraBold),
        titleMedium = titleMedium.nunito(FontWeight.ExtraBold),
        titleSmall = titleSmall.nunito(FontWeight.ExtraBold),
        bodyLarge = bodyLarge.nunito(FontWeight.Medium),
        bodyMedium = bodyMedium.nunito(FontWeight.Medium),
        bodySmall = bodySmall.nunito(FontWeight.Medium),
        labelLarge = labelLarge.nunito(FontWeight.Bold),
        labelMedium = labelMedium.nunito(FontWeight.Bold),
        labelSmall = labelSmall.nunito(FontWeight.Bold),
    )
}
```

If the compiler reports that `Font(...)` or `copy(...)` requires an opt-in, stop and report (spec §5): do not add `@OptIn`.

- [ ] **Step 5: Run the typography test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests 'io.github.vferries.encarte.core.ui.theme.EncarteTypographyTest'`
Expected: 4 tests PASS.

- [ ] **Step 6: Wire the theme**

In `Theme.kt`, replace the last line of `EncarteTheme`:

```kotlin
    MaterialTheme(colorScheme = colorScheme, typography = EncarteTypography, content = content)
```

- [ ] **Step 7: Credit Nunito in the licenses dialog (test first)**

In `SettingsScreenTest.licensesDialogLists`, add after the zxing-cpp assertion:

```kotlin
        composeRule.onNodeWithText("Nunito — SIL Open Font License 1.1", substring = true).assertIsDisplayed()
```

Run: `./gradlew testDebugUnitTest --tests 'io.github.vferries.encarte.settings.SettingsScreenTest.licensesDialogLists'`
Expected: FAIL (`could not find any node`).

Then in `app/src/main/res/values/strings.xml`, append `\nNunito — SIL Open Font License 1.1` at the end of the `third_party_licenses` value (after `Material Symbols — Apache-2.0`). The string is `translatable="false"`, so `values-fr` needs no change.

Run the same test again. Expected: PASS.

- [ ] **Step 8: Create `branding/README.md`**

```markdown
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
```

- [ ] **Step 9: Run the whole unit test suite**

The app-level tests (`MainActivityTest`, `AppFlowTest`) now load the font through Robolectric.

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, no failures. If a test fails while loading `R.font.nunito`, stop and report the stack trace.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/res/font/nunito.ttf branding/fonts/Nunito-OFL.txt branding/README.md \
  app/src/main/java/io/github/vferries/encarte/core/ui/theme/Type.kt \
  app/src/main/java/io/github/vferries/encarte/core/ui/theme/Theme.kt \
  app/src/main/res/values/strings.xml \
  app/src/test/java/io/github/vferries/encarte/core/ui/theme/EncarteTypographyTest.kt \
  app/src/test/java/io/github/vferries/encarte/settings/SettingsScreenTest.kt
git commit -m "feat: use the bundled Nunito font for every text style"
```

---

### Task 2: Corner radii

**Files:**
- Create: `app/src/main/java/io/github/vferries/encarte/core/ui/theme/Shape.kt`
- Modify: `app/src/main/java/io/github/vferries/encarte/core/ui/theme/Theme.kt` (the `MaterialTheme(...)` call)
- Modify: `app/src/main/java/io/github/vferries/encarte/core/ui/CardTile.kt:34`
- Modify: `app/src/main/java/io/github/vferries/encarte/cards/list/CardListScreen.kt:198-199`
- Modify: `app/src/main/java/io/github/vferries/encarte/scan/ScannerScreen.kt:205,209,226`
- Modify: `app/src/main/java/io/github/vferries/encarte/cards/edit/CardEditScreen.kt:415`
- Modify: `app/src/main/java/io/github/vferries/encarte/lock/LockScreen.kt:34`
- Create: `app/src/test/java/io/github/vferries/encarte/core/ui/theme/EncarteShapesTest.kt`

**Interfaces:**
- Consumes: `EncarteTypography` from Task 1 (already passed in `Theme.kt`).
- Produces: `val EncarteShapes: Shapes` (package `io.github.vferries.encarte.core.ui.theme`).

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/io/github/vferries/encarte/core/ui/theme/EncarteShapesTest.kt`:

```kotlin
package io.github.vferries.encarte.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class EncarteShapesTest {
    @Test
    fun textFieldsMenusAndSnackbarsGetSixDp() {
        assertEquals(RoundedCornerShape(6.dp), EncarteShapes.extraSmall)
    }

    @Test
    fun buttonsTilesAndFabGetEightDp() {
        assertEquals(RoundedCornerShape(8.dp), EncarteShapes.small)
        assertEquals(RoundedCornerShape(8.dp), EncarteShapes.medium)
        assertEquals(RoundedCornerShape(8.dp), EncarteShapes.large)
    }

    @Test
    fun dialogsKeepTheMaterialDefault() {
        assertEquals(Shapes().extraLarge, EncarteShapes.extraLarge)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew testDebugUnitTest --tests 'io.github.vferries.encarte.core.ui.theme.EncarteShapesTest'`
Expected: compilation FAILS with `Unresolved reference 'EncarteShapes'`.

- [ ] **Step 3: Implement `Shape.kt` and wire the theme**

Create `app/src/main/java/io/github/vferries/encarte/core/ui/theme/Shape.kt`:

```kotlin
package io.github.vferries.encarte.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Tighter than Material 3's 12/16 dp so tiles look like real cards; dialogs keep the default extraLarge.
val EncarteShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
)
```

In `Theme.kt`:

```kotlin
    MaterialTheme(
        colorScheme = colorScheme,
        typography = EncarteTypography,
        shapes = EncarteShapes,
        content = content,
    )
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests 'io.github.vferries.encarte.core.ui.theme.EncarteShapesTest'`
Expected: 3 tests PASS.

- [ ] **Step 5: Card tiles read the theme**

In `CardTile.kt`, replace `shape = RoundedCornerShape(12.dp),` with `shape = MaterialTheme.shapes.medium,` and delete the now unused `import androidx.compose.foundation.shape.RoundedCornerShape`.

- [ ] **Step 6: Buttons read the theme**

Material 3's `Button` and `OutlinedButton` ignore `MaterialTheme.shapes`, so pass `shape = MaterialTheme.shapes.small` explicitly at exactly these 7 call sites (add `import androidx.compose.material3.MaterialTheme` where missing; leave every `TextButton` alone):

`CardListScreen.kt` (`EmptyState`):
```kotlin
        Button(onClick = onAddCard, shape = MaterialTheme.shapes.small) { Text(stringResource(R.string.action_add_card)) }
        OutlinedButton(onClick = onImport, shape = MaterialTheme.shapes.small) { Text(stringResource(R.string.action_import)) }
```

`ScannerScreen.kt`:
```kotlin
                OutlinedButton(onClick = onManualEntry, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) {
```
```kotlin
                OutlinedButton(onClick = onPickImage, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) {
```
```kotlin
        Button(onClick = onAction, shape = MaterialTheme.shapes.small) { Text(stringResource(action)) }
```

`CardEditScreen.kt`:
```kotlin
            OutlinedButton(onClick = { onPickImage(side) }, shape = MaterialTheme.shapes.small) {
```

`LockScreen.kt`:
```kotlin
            Button(onClick = onUnlock, shape = MaterialTheme.shapes.small) { Text(stringResource(R.string.action_unlock)) }
```

Check that nothing was missed:

Run: `grep -rnE '\b(Outlined)?Button\(' app/src/main/java | grep -v 'shape = MaterialTheme.shapes.small'`
Expected: no output.

- [ ] **Step 7: Run the whole unit test suite and lint**

Run: `./gradlew testDebugUnitTest lintDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/io/github/vferries/encarte/core/ui/theme/Shape.kt \
  app/src/main/java/io/github/vferries/encarte/core/ui/theme/Theme.kt \
  app/src/main/java/io/github/vferries/encarte/core/ui/CardTile.kt \
  app/src/main/java/io/github/vferries/encarte/cards/list/CardListScreen.kt \
  app/src/main/java/io/github/vferries/encarte/scan/ScannerScreen.kt \
  app/src/main/java/io/github/vferries/encarte/cards/edit/CardEditScreen.kt \
  app/src/main/java/io/github/vferries/encarte/lock/LockScreen.kt \
  app/src/test/java/io/github/vferries/encarte/core/ui/theme/EncarteShapesTest.kt
git commit -m "feat: tighten corners to 6 and 8 dp"
```

---

### Task 3: Fan-of-cards launcher icon

**Files:**
- Modify: `app/src/main/res/values/ic_launcher_background.xml`
- Modify (full rewrite): `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- Modify: `app/src/main/res/mipmap-anydpi/ic_launcher.xml`
- Create: `branding/icon.svg`
- Modify: `branding/README.md` (append "Icon" section)
- Create: `app/src/test/java/io/github/vferries/encarte/BrandDrawablesTest.kt`

**Interfaces:**
- Produces: `R.drawable.ic_launcher_monochrome`; `branding/icon.svg` (512 × 512, rendered by Task 7). `BrandDrawablesTest` with a private `render(id, widthPx, heightPx): Bitmap` helper and constant `SCALE = 4` (4 px per viewport unit), which Task 4 extends.

Geometry (spec §4.1), shared by the three files:
- card: `x=30 y=34 w=48 h=32 r=5` → path `M35,34H73A5,5 0 0 1 78,39V61A5,5 0 0 1 73,66H35A5,5 0 0 1 30,61V39A5,5 0 0 1 35,34Z`;
- bars (`y=44 h=15`): `M36,44h2v15h-2z M40,44h1v15h-1z M43,44h3v15h-3z M48,44h1v15h-1z M51,44h2v15h-2z M55,44h1v15h-1z M58,44h3v15h-3z M63,44h1v15h-1z M66,44h2v15h-2z M70,44h1v15h-1z`;
- rotations around (54, 70): back −16°, middle −2°, front 12°;
- outer group: `pivot (54,54) scale 0.95 translate (0.85, 3.07)`. Computed outer extent after it: 31.48 dp (color) and 31.78 dp (monochrome, stroke 4), both under the 33 dp safe-zone radius.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/io/github/vferries/encarte/BrandDrawablesTest.kt`:

```kotlin
package io.github.vferries.encarte

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.hypot

/** Renders the brand vectors for real (native graphics) to check what a launcher or screen will show. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrandDrawablesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun render(id: Int, widthPx: Int, heightPx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val drawable = context.getDrawable(id)!!
        drawable.setBounds(0, 0, widthPx, heightPx)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun assertInsideSafeZone(icon: Bitmap) {
        val center = ICON_PX / 2f
        var painted = 0
        for (y in 0 until ICON_PX) for (x in 0 until ICON_PX) {
            if (Color.alpha(icon.getPixel(x, y)) == 0) continue
            painted++
            val distance = hypot(x + 0.5f - center, y + 0.5f - center)
            assertTrue("pixel ($x, $y) is ${distance / SCALE} dp from the center", distance <= SAFE_RADIUS_PX)
        }
        assertTrue("nothing was drawn", painted > 1_000)
    }

    @Test
    fun foregroundStaysInsideTheSafeZone() {
        assertInsideSafeZone(render(R.drawable.ic_launcher_foreground, ICON_PX, ICON_PX))
    }

    @Test
    fun monochromeStaysInsideTheSafeZone() {
        assertInsideSafeZone(render(R.drawable.ic_launcher_monochrome, ICON_PX, ICON_PX))
    }

    @Test
    fun monochromeBarsShowNothingBehindThem() {
        val icon = render(R.drawable.ic_launcher_monochrome, ICON_PX, ICON_PX)
        // Front card body, left of the bars: solid.
        assertEquals(255, Color.alpha(icon.getPixel(157, 198)))
        // Inside the bar at x=66, right where the back card's outline passes behind the front card.
        assertEquals(0, Color.alpha(icon.getPixel(277, 254)))
    }

    private companion object {
        const val SCALE = 4
        const val ICON_PX = 108 * SCALE
        const val SAFE_RADIUS_PX = 33f * SCALE
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew testDebugUnitTest --tests 'io.github.vferries.encarte.BrandDrawablesTest'`
Expected: compilation FAILS with `Unresolved reference 'ic_launcher_monochrome'`.

- [ ] **Step 3: Write the color icon**

`app/src/main/res/values/ic_launcher_background.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#FFF6E8</color>
</resources>
```

`app/src/main/res/drawable/ic_launcher_foreground.xml` (replace the whole file):

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Launcher art "C3c", a fan of cards. Same numbers as ic_launcher_monochrome.xml and branding/icon.svg:
     change all three together. The outer group centers the art and scales it so it stays inside the
     66 dp safe zone of adaptive icons (visual identity spec §4.1). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
        android:pivotX="54"
        android:pivotY="54"
        android:scaleX="0.95"
        android:scaleY="0.95"
        android:translateX="0.85"
        android:translateY="3.07">
        <group
            android:pivotX="54"
            android:pivotY="70"
            android:rotation="-16">
            <path
                android:fillColor="#2E8C83"
                android:pathData="M35,34H73A5,5 0 0 1 78,39V61A5,5 0 0 1 73,66H35A5,5 0 0 1 30,61V39A5,5 0 0 1 35,34Z" />
        </group>
        <group
            android:pivotX="54"
            android:pivotY="70"
            android:rotation="-2">
            <path
                android:fillColor="#F2A93B"
                android:pathData="M35,34H73A5,5 0 0 1 78,39V61A5,5 0 0 1 73,66H35A5,5 0 0 1 30,61V39A5,5 0 0 1 35,34Z" />
        </group>
        <group
            android:pivotX="54"
            android:pivotY="70"
            android:rotation="12">
            <path
                android:fillColor="#FFFFFF"
                android:strokeColor="#1D2440"
                android:strokeWidth="2.5"
                android:pathData="M35,34H73A5,5 0 0 1 78,39V61A5,5 0 0 1 73,66H35A5,5 0 0 1 30,61V39A5,5 0 0 1 35,34Z" />
            <path
                android:fillColor="#1D2440"
                android:pathData="M36,44h2v15h-2z M40,44h1v15h-1z M43,44h3v15h-3z M48,44h1v15h-1z M51,44h2v15h-2z M55,44h1v15h-1z M58,44h3v15h-3z M63,44h1v15h-1z M66,44h2v15h-2z M70,44h1v15h-1z" />
        </group>
    </group>
</vector>
```

- [ ] **Step 4: Write the monochrome icon**

The two clip paths are the full canvas minus the front card (first) and minus the middle card (second), in art coordinates. Each card is a rotated copy of the card path with its winding reversed, so it cuts a hole under the default non-zero rule. Points were computed by rotating the card's corner tangent points around (54, 70), rounded to 0.01.

`app/src/main/res/drawable/ic_launcher_monochrome.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Themed-icon version of ic_launcher_foreground.xml: same numbers as that file and branding/icon.svg.
     The system tints it, so only the shape matters. Cards behind are outlines clipped where a card in
     front covers them, as in the color icon; otherwise the back card's outline shows through the bars. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
        android:pivotX="54"
        android:pivotY="54"
        android:scaleX="0.95"
        android:scaleY="0.95"
        android:translateX="0.85"
        android:translateY="3.07">
        <group>
            <!-- Everything but the front card. -->
            <clip-path android:pathData="M0,0H108V108H0Z M42.9,30.84A5,5 0 0 0 36.97,34.69L32.4,56.21A5,5 0 0 0 36.25,62.14L73.42,70.04A5,5 0 0 0 79.35,66.19L83.92,44.67A5,5 0 0 0 80.07,38.74Z" />
            <group>
                <!-- Everything but the middle card. -->
                <clip-path android:pathData="M0,0H108V108H0Z M33.76,34.69A5,5 0 0 0 28.93,39.86L29.7,61.84A5,5 0 0 0 34.87,66.67L72.85,65.34A5,5 0 0 0 77.67,60.17L76.9,38.18A5,5 0 0 0 71.73,33.36Z" />
                <group
                    android:pivotX="54"
                    android:pivotY="70"
                    android:rotation="-16">
                    <path
                        android:fillColor="#00000000"
                        android:strokeColor="#FF000000"
                        android:strokeWidth="4"
                        android:pathData="M35,34H73A5,5 0 0 1 78,39V61A5,5 0 0 1 73,66H35A5,5 0 0 1 30,61V39A5,5 0 0 1 35,34Z" />
                </group>
            </group>
            <group
                android:pivotX="54"
                android:pivotY="70"
                android:rotation="-2">
                <path
                    android:fillColor="#00000000"
                    android:strokeColor="#FF000000"
                    android:strokeWidth="4"
                    android:pathData="M35,34H73A5,5 0 0 1 78,39V61A5,5 0 0 1 73,66H35A5,5 0 0 1 30,61V39A5,5 0 0 1 35,34Z" />
            </group>
        </group>
        <group
            android:pivotX="54"
            android:pivotY="70"
            android:rotation="12">
            <path
                android:fillColor="#FF000000"
                android:fillType="evenOdd"
                android:pathData="M35,34H73A5,5 0 0 1 78,39V61A5,5 0 0 1 73,66H35A5,5 0 0 1 30,61V39A5,5 0 0 1 35,34Z M36,44h2v15h-2z M40,44h1v15h-1z M43,44h3v15h-3z M48,44h1v15h-1z M51,44h2v15h-2z M55,44h1v15h-1z M58,44h3v15h-3z M63,44h1v15h-1z M66,44h2v15h-2z M70,44h1v15h-1z" />
        </group>
    </group>
</vector>
```

`app/src/main/res/mipmap-anydpi/ic_launcher.xml`: change the `<monochrome>` line to

```xml
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests 'io.github.vferries.encarte.BrandDrawablesTest'`
Expected: 3 tests PASS.

If `nothing was drawn` fails, Robolectric did not render the vector: stop and report; do not weaken the test. If `monochromeBarsShowNothingBehindThem` fails on the second assertion, the clip path is wrong. To see it, temporarily add `icon.compress(Bitmap.CompressFormat.PNG, 100, java.io.File(System.getProperty("java.io.tmpdir"), "mono.png").outputStream())` to the test, look at the image, then remove the line.

- [ ] **Step 6: Write `branding/icon.svg`**

The SVG transform `translate(54.85 57.07) scale(0.95) translate(-54 -54)` is the VectorDrawable outer group (scale around (54, 54), then translate by (0.85, 3.07)).

```xml
<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="18 18 72 72">
  <!-- Store icon: the visible 72 × 72 area of the adaptive icon, full-bleed Cream, no baked-in corners
       (Play applies its own mask). Same numbers as app/src/main/res/drawable/ic_launcher_foreground.xml
       and ic_launcher_monochrome.xml: change all three together. -->
  <rect x="0" y="0" width="108" height="108" fill="#FFF6E8"/>
  <g transform="translate(54.85 57.07) scale(0.95) translate(-54 -54)">
    <rect x="30" y="34" width="48" height="32" rx="5" fill="#2E8C83" transform="rotate(-16 54 70)"/>
    <rect x="30" y="34" width="48" height="32" rx="5" fill="#F2A93B" transform="rotate(-2 54 70)"/>
    <g transform="rotate(12 54 70)">
      <rect x="30" y="34" width="48" height="32" rx="5" fill="#FFFFFF" stroke="#1D2440" stroke-width="2.5"/>
      <path fill="#1D2440" d="M36,44h2v15h-2z M40,44h1v15h-1z M43,44h3v15h-3z M48,44h1v15h-1z M51,44h2v15h-2z M55,44h1v15h-1z M58,44h3v15h-3z M63,44h1v15h-1z M66,44h2v15h-2z M70,44h1v15h-1z"/>
    </g>
  </g>
</svg>
```

Preview it (scratch output, not committed):

```bash
P=$(mktemp -d); google-chrome --headless=new --user-data-dir="$P" --disable-gpu --hide-scrollbars --window-size=512,512 \
  --screenshot="$P/icon.png" "file://$PWD/branding/icon.svg" 2>/dev/null; echo "$P/icon.png"
```

Open the PNG: Cream square, the three cards centered, nothing clipped.

- [ ] **Step 7: Append the "Icon" section to `branding/README.md`**

```markdown
## Icon

Three files share the launcher art's numbers (spec §4); change them together:

- `app/src/main/res/drawable/ic_launcher_foreground.xml`: color foreground of the adaptive icon (background `#FFF6E8`).
- `app/src/main/res/drawable/ic_launcher_monochrome.xml`: themed icon, one color; outlines behind the front cards are clipped.
- `icon.svg`: 512 × 512 store icon, the visible 72 × 72 area of the adaptive icon on full-bleed Cream.

`BrandDrawablesTest` renders both vectors and fails if any pixel leaves the 33 dp safe zone.
```

- [ ] **Step 8: Run lint and the whole unit test suite**

Run: `./gradlew lintDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL. A lint `VectorPath` warning ("very long vector path") is acceptable; any error is not.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/res/values/ic_launcher_background.xml \
  app/src/main/res/drawable/ic_launcher_foreground.xml \
  app/src/main/res/drawable/ic_launcher_monochrome.xml \
  app/src/main/res/mipmap-anydpi/ic_launcher.xml \
  branding/icon.svg branding/README.md \
  app/src/test/java/io/github/vferries/encarte/BrandDrawablesTest.kt
git commit -m "feat: replace the placeholder launcher icon with a fan of cards"
```

---

### Task 4: Empty-state illustration

**Files:**
- Create: `app/src/main/res/drawable/illustration_card_fan.xml`
- Modify: `app/src/main/java/io/github/vferries/encarte/cards/list/CardListScreen.kt` (`EmptyState`, imports)
- Modify: `app/src/test/java/io/github/vferries/encarte/BrandDrawablesTest.kt` (one test)

**Interfaces:**
- Consumes: `BrandDrawablesTest.render(...)` and `SCALE` from Task 3.
- Produces: `R.drawable.illustration_card_fan` (viewport 108 × 90). Task 7's `feature-graphic.html` repeats its geometry.

Geometry: the icon's cards moved up by 10. Card `x=30 y=24 w=48 h=32 r=5` → path `M35,24H73A5,5 0 0 1 78,29V51A5,5 0 0 1 73,56H35A5,5 0 0 1 30,51V29A5,5 0 0 1 35,24Z`; bars at `y=34`; rotations around (54, 60); no outer group.

- [ ] **Step 1: Write the failing test**

Add to `BrandDrawablesTest` (before the companion object):

```kotlin
    @Test
    fun emptyStateIllustrationKeepsBrandColors() {
        val drawable = context.getDrawable(R.drawable.illustration_card_fan)!!
        assertEquals(108f / 90f, drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight, 0.01f)
        // Fixed colors, not theme attributes: the illustration must look the same in light and dark themes.
        val art = render(R.drawable.illustration_card_fan, 108 * SCALE, 90 * SCALE)
        assertEquals(Color.WHITE, art.getPixel(150, 144)) // front card, left of the bars
        assertEquals(0xFF2E8C83.toInt(), art.getPixel(130, 231)) // back card, below the middle one
        assertEquals(0xFFF2A93B.toInt(), art.getPixel(126, 211)) // middle card, below the front one
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew testDebugUnitTest --tests 'io.github.vferries.encarte.BrandDrawablesTest'`
Expected: compilation FAILS with `Unresolved reference 'illustration_card_fan'`.

- [ ] **Step 3: Write the illustration**

`app/src/main/res/drawable/illustration_card_fan.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Empty-state illustration: the launcher's fan of cards, moved up 10 units into a 108 × 90 viewport.
     Brand colors are fixed so it looks the same in light and dark themes. Same geometry as the fan in
     branding/feature-graphic.html: change both together. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="90dp"
    android:viewportWidth="108"
    android:viewportHeight="90">
    <group
        android:pivotX="54"
        android:pivotY="60"
        android:rotation="-16">
        <path
            android:fillColor="#2E8C83"
            android:pathData="M35,24H73A5,5 0 0 1 78,29V51A5,5 0 0 1 73,56H35A5,5 0 0 1 30,51V29A5,5 0 0 1 35,24Z" />
    </group>
    <group
        android:pivotX="54"
        android:pivotY="60"
        android:rotation="-2">
        <path
            android:fillColor="#F2A93B"
            android:pathData="M35,24H73A5,5 0 0 1 78,29V51A5,5 0 0 1 73,56H35A5,5 0 0 1 30,51V29A5,5 0 0 1 35,24Z" />
    </group>
    <group
        android:pivotX="54"
        android:pivotY="60"
        android:rotation="12">
        <path
            android:fillColor="#FFFFFF"
            android:strokeColor="#1D2440"
            android:strokeWidth="2.5"
            android:pathData="M35,24H73A5,5 0 0 1 78,29V51A5,5 0 0 1 73,56H35A5,5 0 0 1 30,51V29A5,5 0 0 1 35,24Z" />
        <path
            android:fillColor="#1D2440"
            android:pathData="M36,34h2v15h-2z M40,34h1v15h-1z M43,34h3v15h-3z M48,34h1v15h-1z M51,34h2v15h-2z M55,34h1v15h-1z M58,34h3v15h-3z M63,34h1v15h-1z M66,34h2v15h-2z M70,34h1v15h-1z" />
    </group>
</vector>
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests 'io.github.vferries.encarte.BrandDrawablesTest'`
Expected: 4 tests PASS.

- [ ] **Step 5: Show it in the empty state**

In `CardListScreen.kt`, add the imports `androidx.compose.foundation.Image`, `androidx.compose.foundation.layout.aspectRatio` and `androidx.compose.foundation.layout.width`. Then make `EmptyState`'s column start with the illustration. Use `Image`, not `Icon`, which would tint it with the content color:

```kotlin
    ) {
        Image(
            painter = painterResource(R.drawable.illustration_card_fan),
            contentDescription = null, // decorative: the title right below says the list is empty
            modifier = Modifier.width(160.dp).aspectRatio(108f / 90f),
        )
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineSmall)
```

(The rest of `EmptyState`, including the Task 2 button shapes, stays as is.)

- [ ] **Step 6: Run the whole unit test suite and lint**

Run: `./gradlew testDebugUnitTest lintDebug`
Expected: BUILD SUCCESSFUL (`CardListScreenTest.emptyStateOffersImport` still passes).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/drawable/illustration_card_fan.xml \
  app/src/main/java/io/github/vferries/encarte/cards/list/CardListScreen.kt \
  app/src/test/java/io/github/vferries/encarte/BrandDrawablesTest.kt
git commit -m "feat: illustrate the empty card list with the fan of cards"
```

---

### Task 5: Demo wallet and capture tooling

**Files:**
- Create: `branding/demo/cards.csv`
- Create: `branding/demo/card-face.html`
- Create: `branding/capture.sh` (executable)
- Modify: `branding/README.md` (append "Demo data" and "Capturing the raw screenshots" sections)
- Modify: `app/build.gradle.kts` (`testOptions.unitTests.all` block: declare the CSV as a test input)
- Create: `app/src/test/java/io/github/vferries/encarte/backup/DemoCardsTest.kt`

**Interfaces:**
- Consumes: `CatimaCsv.read(text): List<CatimaCard>`, `BarcodeFormat.fromName(name)`, `BarcodeValidator.validate(value, format): BarcodeError?`, `CardPalette.swatches` (existing).
- Produces: `branding/capture.sh <fr-FR|en-US> <1-5>` writing `branding/screenshots/raw/<locale>/<n>.png` (used in Task 6), and `branding/demo/card-face.html` (rendered to the emulator's camera poster in Task 6).

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/io/github/vferries/encarte/backup/DemoCardsTest.kt`:

```kotlin
package io.github.vferries.encarte.backup

import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.core.barcode.BarcodeValidator
import io.github.vferries.encarte.core.color.CardPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The store screenshots show branding/demo/cards.csv: it must import cleanly and every code must display. */
class DemoCardsTest {
    // Gradle runs unit tests from the app module directory.
    private val cards = CatimaCsv.read(File("../branding/demo/cards.csv").readText())

    @Test
    fun holdsTheEightFictionalStores() {
        assertEquals(
            listOf(
                "Boulangerie Martin", "Librairie du Coin", "Fromagerie Dupont", "Cinéma Lumière",
                "Jardinerie des Lilas", "Pharmacie du Centre", "Atelier Vélo", "Café des Arts",
            ),
            cards.map { it.store },
        )
    }

    @Test
    fun onlyTheFirstTwoAreStarred() {
        assertEquals(setOf("Boulangerie Martin", "Librairie du Coin"), cards.filter { it.starred }.map { it.store }.toSet())
    }

    @Test
    fun everyCodeIsValidForItsFormat() {
        val formats = cards.map { card ->
            val format = BarcodeFormat.fromName(card.barcodeType.orEmpty())
            assertNotNull("${card.store}: unknown barcode type ${card.barcodeType}", format)
            assertNull("${card.store}: ${card.cardId}", BarcodeValidator.validate(card.cardId, format!!))
            format
        }
        assertEquals(setOf(BarcodeFormat.EAN_13, BarcodeFormat.CODE_128, BarcodeFormat.QR_CODE), formats.toSet())
    }

    @Test
    fun headerColorsAreDistinctPaletteSwatches() {
        val colors = cards.map { it.headerColor }
        assertEquals(colors.size, colors.toSet().size)
        colors.forEach { assertTrue("$it is not a palette swatch", it in CardPalette.swatches) }
    }
}
```

In `app/build.gradle.kts`, inside the existing `unitTests.all { ... }` block, after the `it.jvmArgs(...)` call, add:

```kotlin
            // DemoCardsTest reads the store-screenshot wallet: editing it must re-run the tests.
            it.inputs.file(rootProject.file("branding/demo/cards.csv"))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew testDebugUnitTest --tests 'io.github.vferries.encarte.backup.DemoCardsTest'`
Expected: FAIL. Gradle reports the missing input file `branding/demo/cards.csv`, or the test throws `FileNotFoundException`.

- [ ] **Step 3: Write the demo wallet**

`branding/demo/cards.csv` (Catima v2 layout, same as `app/src/test/resources/catima/catima_v2.csv`; the groups tables are empty). EAN-13 numbers use the 20–29 prefixes, which GS1 reserves for in-store use, so none is a real product code. Colors are signed ARGB ints of `CardPalette.swatches` entries: orange, blue, yellow, deep purple, green, teal, red, brown.

```
2

_id

_id,store,note,validfrom,expiry,balance,balancetype,cardid,barcodeid,barcodetype,barcodeencoding,headercolor,starstatus,lastused,archive
1,Boulangerie Martin,,,,0,,2000048151625,,EAN_13,,-689152,1,1791100000,0
2,Librairie du Coin,,,,0,,LDC-2048-7731,,CODE_128,,-15108398,1,1791000000,0
3,Fromagerie Dupont,,,,0,,2900002718287,,EAN_13,,-415707,0,1790900000,0
4,Cinéma Lumière,,,,0,,CINE-LUMIERE-000482,,QR_CODE,,-11457112,0,1790800000,0
5,Jardinerie des Lilas,,,,0,,2400016180334,,EAN_13,,-13070788,0,1790700000,0
6,Pharmacie du Centre,,,,0,,PDC 0042 9917,,CODE_128,,-16746133,0,1790600000,0
7,Atelier Vélo,,,,0,,ATELIER-VELO-1207,,QR_CODE,,-2937041,0,1790500000,0
8,Café des Arts,,,,0,,2700014142137,,EAN_13,,-10665929,0,1790400000,0

cardId,groupId
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests 'io.github.vferries.encarte.backup.DemoCardsTest'`
Expected: 4 tests PASS. If `CatimaCsv.read` rejects the empty groups tables, add one group (`_id` / `Demo`) rather than changing the parser, and report it.

- [ ] **Step 5: Write the card face for the scanner shot**

`branding/demo/card-face.html` (1011 × 638 px, the ID-1 ratio at 300 dpi; no barcode, so the scanner does not decode it and leave the screen):

```html
<!doctype html>
<!-- Fictional card face shown to the emulator camera for store screenshot 3 (spec §7.3).
     Logo side only, no barcode: the scanner must not decode it and navigate away. -->
<html>
<head>
<meta charset="utf-8">
<title>Librairie du Coin card face</title>
<style>
  @font-face { font-family: Nunito; src: url("../../app/src/main/res/font/nunito.ttf"); font-weight: 200 1000; }
  html, body { margin: 0; width: 1011px; height: 638px; overflow: hidden; background: #1976D2; }
  body { position: relative; font-family: Nunito; color: #FFFFFF; }
  svg { position: absolute; left: 72px; top: 150px; }
  .name { position: absolute; left: 72px; top: 330px; margin: 0; font-weight: 900; font-size: 92px; line-height: 1; }
  .tag { position: absolute; left: 76px; top: 450px; margin: 0; font-weight: 700; font-size: 38px; opacity: 0.85; }
</style>
</head>
<body>
  <!-- An open book. -->
  <svg width="180" height="130" viewBox="0 0 36 26" fill="none" stroke="#FFFFFF" stroke-width="2.4" stroke-linejoin="round">
    <path d="M18 5 C13 2 7 2 2 4 V23 C7 21 13 21 18 24 Z"/>
    <path d="M18 5 C23 2 29 2 34 4 V23 C29 21 23 21 18 24 Z"/>
  </svg>
  <p class="name">Librairie du Coin</p>
  <p class="tag">Carte de fidélité</p>
  <script>
    document.fonts.load("900 92px Nunito").then(
      faces => { document.documentElement.dataset.ready = faces.length > 0 ? "ok" : "missing-font"; },
      () => { document.documentElement.dataset.ready = "missing-font"; });
  </script>
</body>
</html>
```

Render and look at it (scratch output):

```bash
P=$(mktemp -d); C="google-chrome --headless=new --user-data-dir=$P --disable-gpu --hide-scrollbars --allow-file-access-from-files --force-device-scale-factor=1 --virtual-time-budget=10000"
$C --dump-dom "file://$PWD/branding/demo/card-face.html" 2>/dev/null | grep -o 'data-ready="[^"]*"'
$C --window-size=1011,638 --screenshot="$P/card-face.png" "file://$PWD/branding/demo/card-face.html" 2>/dev/null; echo "$P/card-face.png"
```

Expected: `data-ready="ok"`, and the PNG shows a blue card with a white book, "Librairie du Coin" in Nunito Black, and no barcode.

- [ ] **Step 6: Write `branding/capture.sh`**

```bash
#!/usr/bin/env bash
# Saves the connected device's screen as a raw store-screenshot capture: branding/capture.sh <locale> <n>
# The whole procedure (demo mode, demo data, shot order) is in branding/README.md.
set -euo pipefail

usage="usage: $0 <fr-FR|en-US> <1-5>"
[[ $# -eq 2 && ($1 == fr-FR || $1 == en-US) && $2 =~ ^[1-5]$ ]] || { echo "$usage" >&2; exit 2; }

out="$(dirname "$0")/screenshots/raw/$1/$2.png"
mkdir -p "$(dirname "$out")"
adb exec-out screencap -p > "$out.tmp"
# FLAG_SECURE (app lock on) turns the capture black: refuse it rather than ship a black screenshot.
# Alpha is dropped first: screencap PNGs carry an opaque alpha channel, whose maximum is always 1.
if [[ $(magick "$out.tmp" -alpha off -colorspace Gray -format '%[fx:maxima]' info:) == 0 ]]; then
  rm -f "$out.tmp"
  echo "capture.sh: the capture is black; turn the app lock off and retry" >&2
  exit 1
fi
mv "$out.tmp" "$out"
magick identify -format "capture.sh: wrote $out (%wx%h)\n" "$out"
```

```bash
chmod +x branding/capture.sh
branding/capture.sh; echo "exit $?"              # expected: usage message, exit 2
branding/capture.sh de-DE 1; echo "exit $?"      # expected: usage message, exit 2
branding/capture.sh fr-FR 6; echo "exit $?"      # expected: usage message, exit 2
# Same expression as the script, on RGBA PNGs like screencap's:
magick -size 8x8 xc:black -alpha set PNG32:- | magick - -alpha off -colorspace Gray -format '%[fx:maxima]' info:; echo     # expected: 0
magick -size 8x8 xc:'#2E8C83' -alpha set PNG32:- | magick - -alpha off -colorspace Gray -format '%[fx:maxima]' info:; echo # expected: 0.468086
```

- [ ] **Step 7: Append the demo and capture sections to `branding/README.md`**

````markdown
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
   emulator -avd Pixel_10 -read-only -no-snapshot -virtualscene-poster wall="$P/card-face.png" &
   adb wait-for-device shell 'while [ -z "$(getprop sys.boot_completed)" ]; do sleep 1; done'
   ```

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

   Then set `/sdcard/Pictures/teal.png` as the home and lock screen wallpaper (WALLPAPER_SETUP_STEP).

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
   - Settings → Import → `Download/cards.csv`.
   - Shot 1: the card list, with the Favorites section: `branding/capture.sh $L 1`.
   - Shot 2: open Boulangerie Martin (EAN-13): `branding/capture.sh $L 2`.
   - Shot 4: Settings, scrolled so the backup and lock sections both show: `branding/capture.sh $L 4`.
   - Shot 3: + → scanner; aim the virtual camera at the wall poster (CAMERA_AIM_STEP): `branding/capture.sh $L 3`.

The raw captures in `screenshots/raw/` are committed (demo data only), so caption or layout changes never need a
recapture.
````

`WALLPAPER_SETUP_STEP` and `CAMERA_AIM_STEP` are the two parts of the procedure that can only be confirmed on the emulator. Task 6 replaces them with the steps that actually worked. Until then they stay as literal markers, and Task 6's verification greps for them.

- [ ] **Step 8: Run the whole unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add branding/demo/cards.csv branding/demo/card-face.html branding/capture.sh branding/README.md \
  app/build.gradle.kts app/src/test/java/io/github/vferries/encarte/backup/DemoCardsTest.kt
git commit -m "chore: add the demo wallet and capture script for store screenshots"
```

---

### Task 6: Capture the raw screenshots (interactive, main session)

This task drives the emulator and may need the user, for example to aim the virtual camera with the emulator window's controls. The main session runs it, not a subagent.

**Files:**
- Create: `branding/screenshots/raw/en-US/1.png` … `5.png`
- Create: `branding/screenshots/raw/fr-FR/1.png` … `5.png`
- Modify: `branding/README.md` (replace `WALLPAPER_SETUP_STEP` and `CAMERA_AIM_STEP`)

**Interfaces:**
- Consumes: the debug build with Tasks 1–4, `branding/capture.sh`, `branding/demo/*`.
- Produces: 10 raw captures read by `branding/screenshot.html` in Task 7.

- [ ] **Step 1: Start the emulator and set up demo mode**

Follow `branding/README.md` steps 1–2 exactly.

- [ ] **Step 2: Find a working wallpaper route**

Push the Teal PNG (README step 3). Then try these in order, and stop at the first that sets the wallpaper:
1. `adb shell content query --uri content://media/external/images/media --projection _id:_data` to find the image's id, then `adb shell am start -a android.intent.action.ATTACH_DATA -t image/png -d content://media/external/images/media/<id>` and pick the wallpaper target (`uiautomator dump` + `input tap`).
2. Settings → Wallpaper & style → pick the image from the device's photos.
3. Ask the user to set it from the emulator window.

Verify with `adb exec-out screencap -p` on the home screen (the scratchpad copy is enough). Replace `WALLPAPER_SETUP_STEP` in the README with the route that worked, written as commands or numbered taps.

- [ ] **Step 3: Visual pass on the new identity before capturing**

Install the debug build (README step 4). Take scratch screenshots and check:
- home screen: the launcher icon is the fan on Cream, nothing cut by the mask;
- themed icons on (Wallpaper & style → Themed icons): the monochrome icon reads as three cards, with clean bars;
- splash: shows the new icon;
- empty state in light and dark (`adb shell cmd uimode night yes` / `no`): same illustration colors;
- Nunito on the list, display, edit, settings and lock screens; 8 dp corners on tiles, FAB and buttons; 6 dp on the search field;
- in fr-FR, the two side-by-side scanner buttons and the empty-state buttons fit without clipping (Nunito Bold is wider than Roboto).

Anything wrong: stop, fix it in the task that owns the code (new commit), reinstall, re-check.

- [ ] **Step 4: Capture en-US, then fr-FR**

Follow README step 5 for `en-US`, then `fr-FR`. For shot 3, try aiming at the wall poster: the emulator window's virtual-scene controls (hold Alt, mouse to look, W/A/S/D to move) or the extended controls' virtual sensors. If it can't be automated, ask the user to aim the camera in the emulator window while the scanner is open, then run `branding/capture.sh <locale> 3`. If the virtual scene can't show the poster at all, fall back to spec §7.4: the user captures shot 3 on their phone (`ANDROID_SERIAL=<phone serial> branding/capture.sh <locale> 3`), aimed at `card-face.html` shown full-screen on the computer. Replace `CAMERA_AIM_STEP` in the README with what worked.

After each capture, look at the PNG. Correct screen, demo data only, status bar at 12:00, no lock screen, nothing black.

- [ ] **Step 5: Verify the set and the README**

```bash
for f in branding/screenshots/raw/*/*.png; do magick identify -format '%f %wx%h\n' "$f"; done
grep -n 'WALLPAPER_SETUP_STEP\|CAMERA_AIM_STEP' branding/README.md
```

Expected: 10 files, all 1080 px wide, and no marker left in the README (the grep prints nothing).

- [ ] **Step 6: Commit**

```bash
git add branding/screenshots/raw branding/README.md
git commit -m "chore: add raw store screenshot captures"
```

---

### Task 7: Store graphics rendering

**Files:**
- Create: `branding/feature-graphic.html`
- Create: `branding/screenshot.html`
- Create: `branding/render.sh` (executable)
- Modify: `branding/README.md` (append "Rendering the store graphics")
- Create (generated): `fastlane/metadata/android/{fr-FR,en-US}/images/icon.png`, `featureGraphic.png`, `phoneScreenshots/1.png` … `5.png`

**Interfaces:**
- Consumes: `branding/icon.svg` (Task 3), the illustration geometry (Task 4), `app/src/main/res/font/nunito.ttf` (Task 1), `branding/screenshots/raw/<locale>/<n>.png` (Task 6).
- Produces: the 14 store PNGs.

Contract between templates and `render.sh`: each HTML template sets `data-ready="ok"` on `<html>` once Nunito (and, for screenshots, the raw capture) has loaded; any other value is an error message. `render.sh` reads it with `--dump-dom` before taking the screenshot.

- [ ] **Step 1: Write `branding/feature-graphic.html`**

```html
<!doctype html>
<!-- Play/F-Droid feature graphic, 1024 × 500 (visual identity spec §7.1).
     render.sh opens it as feature-graphic.html?locale=fr-FR (or en-US). -->
<html>
<head>
<meta charset="utf-8">
<title>Encarté feature graphic</title>
<style>
  /* The app's own font file: no network access, no system font. */
  @font-face { font-family: Nunito; src: url("../app/src/main/res/font/nunito.ttf"); font-weight: 200 1000; }
  html, body { margin: 0; width: 1024px; height: 500px; overflow: hidden; background: #FFF6E8; }
  svg { display: block; }
  text { font-family: Nunito; }
</style>
</head>
<body>
<svg width="1024" height="500" viewBox="0 0 1024 500">
  <rect width="1024" height="500" fill="#FFF6E8"/>
  <text id="line1" x="64" y="205" font-weight="900" font-size="54" fill="#1D2440"></text>
  <text id="line2" x="64" y="270" font-weight="900" font-size="54" fill="#1D2440"></text>
  <text id="subtitle" x="66" y="330" font-weight="700" font-size="24" fill="#2E8C83"></text>
  <!-- Same geometry as app/src/main/res/drawable/illustration_card_fan.xml: change both together. -->
  <svg x="610" y="40" width="400" height="420" viewBox="0 0 108 90">
    <rect x="30" y="24" width="48" height="32" rx="5" fill="#2E8C83" transform="rotate(-16 54 60)"/>
    <rect x="30" y="24" width="48" height="32" rx="5" fill="#F2A93B" transform="rotate(-2 54 60)"/>
    <g transform="rotate(12 54 60)">
      <rect x="30" y="24" width="48" height="32" rx="5" fill="#FFFFFF" stroke="#1D2440" stroke-width="2.5"/>
      <path fill="#1D2440" d="M36,34h2v15h-2z M40,34h1v15h-1z M43,34h3v15h-3z M48,34h1v15h-1z M51,34h2v15h-2z M55,34h1v15h-1z M58,34h3v15h-3z M63,34h1v15h-1z M66,34h2v15h-2z M70,34h1v15h-1z"/>
    </g>
  </svg>
</svg>
<script>
  const TEXTS = {
    "fr-FR": ["Vos cartes de fidélité.", "Rien d'autre.", "Hors-ligne · Sans compte · Libre"],
    "en-US": ["Your loyalty cards.", "Nothing else.", "Offline · No account · Open source"],
  };
  const root = document.documentElement;
  const texts = TEXTS[new URLSearchParams(location.search).get("locale")];
  if (!texts) {
    root.dataset.ready = "unknown-locale";
  } else {
    ["line1", "line2", "subtitle"].forEach((id, i) => { document.getElementById(id).textContent = texts[i]; });
    // render.sh refuses the image unless the bundled font really loaded: a fallback font would pass silently.
    Promise.all([document.fonts.load("900 54px Nunito"), document.fonts.load("700 24px Nunito")]).then(
      sets => { root.dataset.ready = sets.every(faces => faces.length > 0) ? "ok" : "missing-font"; },
      () => { root.dataset.ready = "missing-font"; });
  }
</script>
</body>
</html>
```

- [ ] **Step 2: Write `branding/screenshot.html`**

```html
<!doctype html>
<!-- Store phone screenshot, 1080 × 1920 (visual identity spec §7.2): caption above a generic phone frame
     that runs off the bottom edge. render.sh opens it as screenshot.html?locale=fr-FR&n=1 (n = 1..5). -->
<html>
<head>
<meta charset="utf-8">
<title>Encarté store screenshot</title>
<style>
  @font-face { font-family: Nunito; src: url("../app/src/main/res/font/nunito.ttf"); font-weight: 200 1000; }
  html, body { margin: 0; width: 1080px; height: 1920px; overflow: hidden; }
  body { position: relative; }
  .caption { position: absolute; top: 150px; left: 0; width: 1080px; margin: 0; text-align: center;
             font: 900 76px/1.12 Nunito; }
  /* 800 px body: a 764 px screen plus an 18 px bezel on each side. */
  .phone { position: absolute; left: 140px; top: 470px; padding: 18px; border-radius: 72px; background: #111111; }
  .phone img { display: block; width: 764px; border-radius: 54px; }
</style>
</head>
<body>
<p class="caption" id="caption"></p>
<div class="phone"><img id="screen" alt=""></div>
<script>
  // Background, caption color and caption per shot; " / " marks the caption's line break.
  const SHOTS = {
    1: { bg: "#2E8C83", fg: "#FFFFFF", "fr-FR": "Toutes vos cartes, / au même endroit", "en-US": "All your cards, / in one place" },
    2: { bg: "#F2A93B", fg: "#1D2440", "fr-FR": "Présentez-la en caisse, / luminosité au max", "en-US": "Show it at checkout, / at full brightness" },
    3: { bg: "#FFF6E8", fg: "#1D2440", "fr-FR": "Scannez une carte / en un instant", "en-US": "Scan a card / in seconds" },
    4: { bg: "#1D2440", fg: "#FFFFFF", "fr-FR": "Sauvegarde chiffrée, / compatible Catima", "en-US": "Encrypted backups, / Catima-compatible" },
    5: { bg: "#2E8C83", fg: "#FFFFFF", "fr-FR": "Hors-ligne. / Aucun compte.", "en-US": "Offline. / No account." },
  };
  const root = document.documentElement;
  const params = new URLSearchParams(location.search);
  const locale = params.get("locale"), n = params.get("n");
  const shot = SHOTS[n];
  if (!shot || !shot[locale]) {
    root.dataset.ready = "unknown-shot";
  } else {
    document.body.style.background = shot.bg;
    const caption = document.getElementById("caption");
    caption.style.color = shot.fg;
    shot[locale].split(" / ").forEach((line, i) => {
      if (i > 0) caption.append(document.createElement("br"));
      caption.append(line);
    });
    const screen = document.getElementById("screen");
    const captured = new Promise((resolve, reject) => { screen.onload = resolve; screen.onerror = reject; });
    screen.src = `screenshots/raw/${locale}/${n}.png`;
    // render.sh refuses the image unless both the bundled font and the raw capture really loaded.
    Promise.all([document.fonts.load("900 76px Nunito"), captured]).then(
      ([faces]) => { root.dataset.ready = faces.length > 0 ? "ok" : "missing-font"; },
      () => { root.dataset.ready = "missing-font-or-capture"; });
  }
</script>
</body>
</html>
```

- [ ] **Step 3: Write `branding/render.sh`**

```bash
#!/usr/bin/env bash
# Regenerates every store PNG in fastlane/metadata/android/*/images from the sources in branding/,
# then checks sizes and the absence of alpha. Dev-only: needs Google Chrome and ImageMagick 7 (see README.md).
set -euo pipefail

cd "$(dirname "$0")/.."
branding="$PWD/branding"
locales=(fr-FR en-US)

profile=$(mktemp -d)
trap 'rm -rf "$profile"' EXIT

fail() { echo "render.sh: $*" >&2; exit 1; }

# A throwaway profile keeps the user's own Chrome session out of it; file access lets templates load the font.
chrome() {
  google-chrome --headless=new --user-data-dir="$profile" --disable-gpu --hide-scrollbars \
    --allow-file-access-from-files --force-device-scale-factor=1 --virtual-time-budget=10000 "$@" 2>/dev/null
}

# Templates set data-ready="ok" only once Nunito and their images loaded; Chrome would otherwise fall back silently.
check_ready() {
  local url=$1 state
  state=$(chrome --dump-dom "$url" | grep -o 'data-ready="[^"]*"' || true)
  [[ $state == 'data-ready="ok"' ]] || fail "$url is not ready (${state:-no data-ready attribute})"
}

# Play and F-Droid want opaque PNGs at exact sizes.
flatten_and_verify() {
  local file=$1 width=$2 height=$3 actual
  magick "$file" -background white -alpha remove -alpha off "PNG24:$file"
  actual=$(magick identify -format '%w %h %[channels]' "$file")
  [[ $actual == "$width $height srgb "* ]] || fail "$file is '$actual', expected ${width}x${height} sRGB without alpha"
}

render() {
  local url=$1 width=$2 height=$3 out=$4
  mkdir -p "$(dirname "$out")"
  chrome --window-size="$width,$height" --screenshot="$out" "$url"
  [[ -s $out ]] || fail "Chrome wrote no image for $url"
  flatten_and_verify "$out" "$width" "$height"
  echo "render.sh: $out"
}

for locale in "${locales[@]}"; do
  images="fastlane/metadata/android/$locale/images"
  render "file://$branding/icon.svg" 512 512 "$images/icon.png"

  url="file://$branding/feature-graphic.html?locale=$locale"
  check_ready "$url"
  render "$url" 1024 500 "$images/featureGraphic.png"

  for n in 1 2 3 4 5; do
    [[ -f $branding/screenshots/raw/$locale/$n.png ]] || fail "missing raw capture screenshots/raw/$locale/$n.png (see branding/README.md)"
    url="file://$branding/screenshot.html?locale=$locale&n=$n"
    check_ready "$url"
    render "$url" 1080 1920 "$images/phoneScreenshots/$n.png"
  done
done
echo "render.sh: all store images regenerated and checked"
```

```bash
chmod +x branding/render.sh
```

- [ ] **Step 4: Prove the font guard fails loudly**

```bash
mv app/src/main/res/font/nunito.ttf /tmp/nunito.ttf.bak
branding/render.sh; echo "exit $?"
mv /tmp/nunito.ttf.bak app/src/main/res/font/nunito.ttf
git status --short app/src/main/res/font
```

Expected: `render.sh: file://…/feature-graphic.html?locale=fr-FR is not ready (data-ready="missing-font")`, `exit 1`; afterwards `git status` shows nothing for the font. The font is restored.

- [ ] **Step 5: Render everything**

Run: `branding/render.sh`
Expected: 14 `render.sh: fastlane/...` lines, then `render.sh: all store images regenerated and checked`, exit 0.

Independent check:

```bash
for f in fastlane/metadata/android/*/images/{icon,featureGraphic}.png fastlane/metadata/android/*/images/phoneScreenshots/*.png; do
  magick identify -format '%[channels] %wx%h  %d/%f\n' "$f"; done
```

Expected: 14 lines, all `srgb  3.0`, sizes 512x512 / 1024x500 / 1080x1920.

- [ ] **Step 6: Look at every image**

Open each of the 14 PNGs. The icon matches the launcher, and the headline doesn't touch the fan. Each caption has 2 lines, the frame top corners are rounded, and the screen sits inside the bezel. Colors match the spec §7.2 table, and the captions are in the right locale.

- [ ] **Step 7: Append the rendering section to `branding/README.md`**

```markdown
## Rendering the store graphics

`branding/render.sh` regenerates all 14 PNGs in `fastlane/metadata/android/<fr-FR|en-US>/images/` (icon 512 × 512,
`featureGraphic.png` 1024 × 500, `phoneScreenshots/1..5.png` 1080 × 1920) from `icon.svg`, `feature-graphic.html`,
`screenshot.html` and the raw captures. Captions, colors and feature-graphic texts live in the tables at the bottom
of the two HTML templates.

It uses headless Google Chrome (with a throwaway profile) and ImageMagick 7. It fails if a template could not load
the bundled Nunito or its capture, if an image has the wrong size, or if it still has an alpha channel.
Neither the app build nor CI runs it.
```

- [ ] **Step 8: User review gate**

Stop and ask the user to review the 14 PNGs (give the paths). Commit only after they approve. If they ask for changes, edit the templates or `render.sh`, re-run `branding/render.sh` and ask again.

- [ ] **Step 9: Commit**

```bash
git add branding/feature-graphic.html branding/screenshot.html branding/render.sh branding/README.md \
  fastlane/metadata/android/fr-FR/images fastlane/metadata/android/en-US/images
git commit -m "feat: render store icon, feature graphic and phone screenshots"
```

---

### Task 8: Release checklist and final verification

**Files:**
- Modify: `docs/release-checklist.md` ("Before the first public release" section)
- Modify: `docs/superpowers/specs/2026-10-04-encarte-visual-identity-design.md:4` (status)

- [ ] **Step 1: Full build**

Run: `./gradlew clean lintDebug testDebugUnitTest assembleRelease verifyNoNetworkPermission`
Expected: BUILD SUCCESSFUL. The last task prints `OK: no network permission among [...]`.

- [ ] **Step 2: Check that the release APK bundles the font and the icons**

Resource shrinking may rename the files inside the APK, so query the resource table rather than the zip listing:

```bash
~/Android/Sdk/build-tools/37.0.0/aapt2 dump resources app/build/outputs/apk/release/app-release-unsigned.apk \
  | grep -E 'font/nunito|drawable/ic_launcher_monochrome|drawable/illustration_card_fan|mipmap/ic_launcher'
```
Expected: 4 resource lines. A missing line means the shrinker dropped a resource: stop and report.

- [ ] **Step 3: Update the release checklist**

In `docs/release-checklist.md`, section "Before the first public release":
- replace `- [ ] Final app icon (replace the placeholder adaptive icon).` with `- [x] Final app icon (replace the placeholder adaptive icon).`;
- replace `- [ ] Store screenshots in \`fastlane/metadata/android/*/images/phoneScreenshots/\`.` with `- [x] Store screenshots in \`fastlane/metadata/android/*/images/phoneScreenshots/\` (regenerate with \`branding/render.sh\`).`;
- add right after it: `- [x] Feature graphic and store icon in \`fastlane/metadata/android/*/images/\` (same script).`

In the spec header, change `- **Status:** Draft, pending review` to `- **Status:** Approved, implemented 2026-10-04`.

- [ ] **Step 4: Commit**

```bash
git add docs/release-checklist.md docs/superpowers/specs/2026-10-04-encarte-visual-identity-design.md
git commit -m "docs: tick the icon, screenshots and feature graphic in the release checklist"
```

- [ ] **Step 5: Hand over the on-device checks**

The spec's S23 checks can't run here. List them for the user in the final message: launcher icon under the device's mask; themed icons on; splash; empty state in light and dark themes; Nunito on the list, display, edit, settings and lock screens; 8 dp corners on tiles, FAB and buttons; 6 dp on the search field.
