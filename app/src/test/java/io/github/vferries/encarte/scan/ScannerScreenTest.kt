package io.github.vferries.encarte.scan

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.importing.ImportFailure
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
    private var picks = 0
    private var screenState by mutableStateOf(ScannerUiState())

    /** Later changes to [screenState] recompose the screen. */
    private fun setScreen(state: ScannerUiState, permission: CameraPermission) {
        screenState = state
        composeRule.setContent {
            ScannerScreen(
                state = screenState,
                permission = permission,
                onBack = {},
                onRequestPermission = { requested = true },
                onOpenSettings = { settingsOpened = true },
                onToggleTorch = {},
                onManualEntry = { manual = true },
                onPickFile = { picks++ },
            )
        }
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
        setScreen(ScannerUiState(fileError = ImportFailure.NO_CODE_IN_IMAGE), CameraPermission.GRANTED)

        composeRule.onNodeWithText("No barcode found in this image.").assertIsDisplayed()
        composeRule.onNodeWithText("Image or file").performClick()
        composeRule.onNodeWithText("Enter manually").performClick()

        assertTrue(manual)
        assertEquals(1, picks)
    }

    @Test
    fun eachFileErrorShowsUnderTheCamera() {
        val messages = mapOf(
            ImportFailure.UNRECOGNIZED_FILE to "Unrecognized file.",
            ImportFailure.UNRECOGNIZED_PASS to "This file isn't a recognized pass.",
            ImportFailure.PDF_UNREADABLE to "Can't read this PDF (protected or damaged).",
            ImportFailure.NO_CODE_IN_PDF to "No barcode found in this PDF.",
            ImportFailure.NO_CODE_IN_IMAGE to "No barcode found in this image.",
            ImportFailure.CANNOT_OPEN to "Can't open this file.",
            ImportFailure.FILE_GONE to "This file is no longer available.",
        )
        assertEquals(ImportFailure.entries.toSet(), messages.keys)
        setScreen(ScannerUiState(), CameraPermission.GRANTED)

        for ((failure, message) in messages) {
            screenState = ScannerUiState(fileError = failure)
            composeRule.onNodeWithText(message).assertIsDisplayed()
        }
    }

    @Test
    fun aFileBeingReadBlocksASecondPick() {
        setScreen(ScannerUiState(readingFile = true), CameraPermission.GRANTED)

        composeRule.onNodeWithText("Image or file").assertIsNotEnabled()
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
