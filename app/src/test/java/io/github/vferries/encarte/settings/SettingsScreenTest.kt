package io.github.vferries.encarte.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var exported: CharArray? = charArrayOf('x')
    private var exportCalls = 0
    private var messagesShown = 0

    private fun setScreen(
        state: SettingsUiState = SettingsUiState(),
        deviceSecure: Boolean = true,
        nfcSupported: Boolean = false,
        onNfcBlockChange: (Boolean) -> Unit = {},
    ) = composeRule.setContent {
        SettingsScreen(
            state = state,
            deviceSecure = deviceSecure,
            nfcSupported = nfcSupported,
            onBack = {},
            onLockChange = {},
            onNfcBlockChange = onNfcBlockChange,
            onExportConfirmed = { exported = it; exportCalls++ },
            onImport = {},
            onImportPassword = {},
            onImportCancelled = {},
            onMessageShown = { messagesShown++ },
            onOpenSource = {},
        )
    }

    @Test
    fun lockIsUnavailableWithoutScreenLock() {
        setScreen(deviceSecure = false)

        composeRule.onNodeWithText("Set a screen lock on this device first").assertIsDisplayed()
        composeRule.onNode(isToggleable()).assertIsNotEnabled()
    }

    @Test
    fun exportWithoutPassword() {
        setScreen()

        composeRule.onNodeWithText("Export cards").performClick()
        composeRule.onNodeWithText("No password").performClick()

        assertEquals(1, exportCalls)
        assertEquals(null, exported)
    }

    @Test
    fun mismatchedPasswordsBlockExport() {
        setScreen()
        composeRule.onNodeWithText("Export cards").performClick()

        composeRule.onNodeWithText("Password").performTextInput("abc")
        composeRule.onNodeWithText("Confirm password").performTextInput("abd")

        composeRule.onNodeWithText("Passwords don't match").assertIsDisplayed()
        composeRule.onNodeWithText("Export").assertIsNotEnabled()
    }

    @Test
    fun retryPromptExplainsWrongPassword() {
        setScreen(SettingsUiState(passwordPrompt = PasswordPrompt.RETRY))

        composeRule.onNodeWithText("Wrong password, try again.").assertIsDisplayed()
    }

    @Test
    fun licensesDialogLists() {
        setScreen()

        composeRule.onNodeWithText("Open source licenses").performScrollTo().performClick()

        composeRule.onNodeWithText("zxing-cpp — Apache-2.0", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Nunito — SIL Open Font License 1.1", substring = true).assertIsDisplayed()
    }

    @Test
    fun checkoutSectionOnlyWithNfc() {
        setScreen(nfcSupported = false)

        composeRule.onNodeWithText("At checkout").assertDoesNotExist()
    }

    @Test
    fun contactlessBlockingIsOnByDefaultAndCanBeTurnedOff() {
        var changedTo: Boolean? = null
        setScreen(nfcSupported = true, onNfcBlockChange = { changedTo = it })

        composeRule.onNodeWithText("At checkout").assertIsDisplayed()
        composeRule.onNodeWithText("Block contactless payment").assertIsOn().performClick()

        assertEquals(false, changedTo)
    }

    @Test
    fun cancelClosesTheExportDialogWithoutExporting() {
        setScreen()
        composeRule.onNodeWithText("Export cards").performClick()

        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("No password").assertDoesNotExist()
        assertEquals(0, exportCalls)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun exportDialogButtonsWrapInsideTheDialogOnNarrowScreens() {
        setScreen()
        composeRule.onNodeWithText("Export cards").performClick()

        val dialog = composeRule.onNode(isDialog()).getUnclippedBoundsInRoot()
        for (label in listOf("Cancel", "No password", "Export")) {
            val button = composeRule.onNodeWithText(label).getUnclippedBoundsInRoot()
            assertTrue("$label inside the dialog", button.left >= dialog.left && button.right <= dialog.right)
        }
    }

    @Test
    fun theImportSubtitleNamesFidMe() {
        setScreen()

        composeRule.onNodeWithText("From an Encarté or Catima backup, or a FidMe export").assertIsDisplayed()
    }

    @Test
    fun aFidMeImportExplainsItsCountsAndTheGuessedTypes() {
        val result = BackupMessage.FidMeImported(imported = 12, skippedDuplicates = 2, skippedWithoutNumber = 3)
        setScreen(SettingsUiState(message = result))

        composeRule.onNodeWithText("FidMe import").assertIsDisplayed()
        composeRule.onNodeWithText("12 cards imported").assertIsDisplayed()
        composeRule.onNodeWithText("2 duplicates skipped").assertIsDisplayed()
        composeRule.onNodeWithText("3 skipped without a number (expired in FidMe)").assertIsDisplayed()
        composeRule.onNodeWithText("FidMe doesn't export barcode types: check them at checkout or rescan the card.")
            .assertIsDisplayed()

        composeRule.onNodeWithText("Close").performClick()
        composeRule.runOnIdle { assertEquals(1, messagesShown) }
    }

    @Test
    fun aFidMeImportWithoutSkippedRowsOnlyCountsTheImport() {
        val result = BackupMessage.FidMeImported(imported = 1, skippedDuplicates = 0, skippedWithoutNumber = 0)
        setScreen(SettingsUiState(message = result))

        composeRule.onNodeWithText("1 card imported").assertIsDisplayed()
        composeRule.onNodeWithText("skipped", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("FidMe doesn't export barcode types: check them at checkout or rescan the card.")
            .assertIsDisplayed()
    }

    @Test
    fun aCatimaImportStaysASnackbar() {
        setScreen(SettingsUiState(message = BackupMessage.Imported(imported = 2, skipped = 1)))

        composeRule.onNodeWithText("2 cards imported, 1 duplicate skipped").assertIsDisplayed()
        composeRule.onNodeWithText("FidMe import").assertDoesNotExist()
    }
}
