package io.github.vferries.encarte.scan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScannerScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var settingsOpened = false
    private var manual = false
    private var requested = false

    private fun setScreen(state: ScannerUiState, permission: CameraPermission) = composeRule.setContent {
        ScannerScreen(
            state = state,
            permission = permission,
            onBack = {},
            onRequestPermission = { requested = true },
            onOpenSettings = { settingsOpened = true },
            onToggleTorch = {},
            onManualEntry = { manual = true },
            onPickImage = {},
        )
    }

    @Test
    fun notGrantedShowsRationaleAndRequests() {
        setScreen(ScannerUiState(), CameraPermission.NOT_GRANTED)

        composeRule.onNodeWithText("Allow camera").performClick()

        assertTrue(requested)
    }

    @Test
    fun permanentlyDeniedLinksToSettings() {
        setScreen(ScannerUiState(), CameraPermission.PERMANENTLY_DENIED)

        composeRule.onNodeWithText("Open app settings").performClick()

        assertTrue(settingsOpened)
    }

    @Test
    fun alternativesAreAlwaysAvailable() {
        setScreen(ScannerUiState(imageNotDecoded = true), CameraPermission.GRANTED)

        composeRule.onNodeWithText("No barcode found in this image.").assertIsDisplayed()
        composeRule.onNodeWithText("From an image").assertIsDisplayed()
        composeRule.onNodeWithText("Enter manually").performClick()

        assertTrue(manual)
    }

    @Test
    fun torchToggleOnlyWithFlash() {
        setScreen(ScannerUiState(hasTorch = true), CameraPermission.GRANTED)

        composeRule.onNodeWithContentDescription("Turn on flashlight").assertIsDisplayed()
    }
}
