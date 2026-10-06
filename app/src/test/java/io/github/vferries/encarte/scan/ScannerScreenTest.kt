package io.github.vferries.encarte.scan

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.lock.LocalContentCovered
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
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

    private var covered by mutableStateOf(true)
    private var permissionRequests = 0
    private var cameraStarts = 0
    private var cameraStops = 0

    // As behind the lock, which the "Add a card" shortcut can open the scanner under.
    private fun setCameraEffects(permission: CameraPermission) = composeRule.setContent {
        CompositionLocalProvider(LocalContentCovered provides covered) {
            CameraEffects(
                permission = permission,
                requestPermission = { permissionRequests++ },
                runCamera = {
                    cameraStarts++
                    try {
                        awaitCancellation()
                    } finally {
                        cameraStops++
                    }
                },
            )
        }
    }

    @Test
    fun thePermissionIsAskedOnlyOnceUnlocked() {
        setCameraEffects(CameraPermission.NOT_GRANTED)
        composeRule.runOnIdle { assertEquals(0, permissionRequests) }

        covered = false
        composeRule.runOnIdle { assertEquals(1, permissionRequests) }

        // Without a lock, coming back to the app does not ask again: neither does an unlock.
        covered = true
        composeRule.waitForIdle()
        covered = false
        composeRule.runOnIdle { assertEquals(1, permissionRequests) }
    }

    @Test
    fun theCameraRunsOnlyWhileUnlocked() {
        setCameraEffects(CameraPermission.GRANTED)
        composeRule.runOnIdle { assertEquals(0, cameraStarts) }

        covered = false
        composeRule.runOnIdle {
            assertEquals(1, cameraStarts)
            assertEquals(0, cameraStops)
        }

        covered = true
        composeRule.runOnIdle { assertEquals(1, cameraStops) }
        assertEquals(0, permissionRequests)
    }

    @Test
    fun torchToggleOnlyWithFlash() {
        setScreen(ScannerUiState(hasTorch = true), CameraPermission.GRANTED)

        composeRule.onNodeWithContentDescription("Turn on flashlight").assertIsDisplayed()
    }
}
