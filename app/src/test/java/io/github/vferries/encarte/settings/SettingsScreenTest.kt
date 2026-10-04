package io.github.vferries.encarte.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var exported: CharArray? = charArrayOf('x')
    private var exportCalls = 0

    private fun setScreen(state: SettingsUiState = SettingsUiState(), deviceSecure: Boolean = true) =
        composeRule.setContent {
            SettingsScreen(
                state = state,
                deviceSecure = deviceSecure,
                onBack = {},
                onLockChange = {},
                onExportConfirmed = { exported = it; exportCalls++ },
                onImport = {},
                onImportPassword = {},
                onImportCancelled = {},
                onMessageShown = {},
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
}
