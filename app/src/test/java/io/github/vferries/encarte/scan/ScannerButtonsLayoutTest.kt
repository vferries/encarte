package io.github.vferries.encarte.scan

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.core.ui.theme.EncarteTheme
import org.junit.Assert.assertEquals
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

    // Robolectric wraps "Depuis une image" alone at 380 dp, as a 360 dp phone does.
    @Test
    @Config(qualifiers = "fr-w380dp-h800dp")
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
                    onPickImage = {},
                )
            }
        }

        val manual = composeRule.onNodeWithText("Saisir à la main").getUnclippedBoundsInRoot()
        val image = composeRule.onNodeWithText("Depuis une image").getUnclippedBoundsInRoot()

        assertEquals(image.bottom - image.top, manual.bottom - manual.top)
    }
}
