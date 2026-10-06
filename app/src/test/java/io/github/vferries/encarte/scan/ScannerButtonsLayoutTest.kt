package io.github.vferries.encarte.scan

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.core.ui.theme.EncarteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real text metrics (native graphics, the app's font): the layout depends on where labels wrap. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScannerButtonsLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    // Robolectric wraps "Image ou fichier" alone between 362 and 368 dp (a 360 dp phone wraps both labels).
    @Test
    @Config(qualifiers = "fr-w364dp-h800dp")
    fun sideBySideButtonsKeepTheSameHeightWhenOneLabelWraps() {
        composeRule.setContent {
            EncarteTheme {
                ScannerScreen(
                    state = ScannerUiState(),
                    permission = CameraPermission.GRANTED,
                    onBack = {},
                    onRequestPermission = {},
                    onOpenSettings = {},
                    onToggleTorch = {},
                    onManualEntry = {},
                    onPickFile = {},
                )
            }
        }

        val manualLabel = composeRule.onNodeWithText("Saisir à la main", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val imageLabel = composeRule.onNodeWithText("Image ou fichier", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("only one label wraps", imageLabel.bottom - imageLabel.top > manualLabel.bottom - manualLabel.top)
        val manual = composeRule.onNodeWithText("Saisir à la main").getUnclippedBoundsInRoot()
        val image = composeRule.onNodeWithText("Image ou fichier").getUnclippedBoundsInRoot()

        assertEquals(image.bottom - image.top, manual.bottom - manual.top)
    }
}
