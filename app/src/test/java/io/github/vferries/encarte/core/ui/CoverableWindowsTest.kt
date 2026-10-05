package io.github.vferries.encarte.core.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.lock.LocalContentCovered
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoverableWindowsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun alertDialogShowsNothingWhileContentIsCoveredAndComesBack() {
        var covered by mutableStateOf(false)
        composeRule.setContent {
            CompositionLocalProvider(LocalContentCovered provides covered) {
                EncarteAlertDialog(onDismissRequest = {}, confirmButton = {}, text = { Text("Secret dialog") })
            }
        }
        composeRule.onNodeWithText("Secret dialog").assertIsDisplayed()

        covered = true
        composeRule.onNodeWithText("Secret dialog").assertDoesNotExist()

        covered = false
        composeRule.onNodeWithText("Secret dialog").assertIsDisplayed()
    }

    @Test
    fun dropdownMenuShowsNothingWhileContentIsCoveredAndComesBack() {
        var covered by mutableStateOf(false)
        composeRule.setContent {
            CompositionLocalProvider(LocalContentCovered provides covered) {
                EncarteDropdownMenu(expanded = true, onDismissRequest = {}) { Text("Secret item") }
            }
        }
        composeRule.onNodeWithText("Secret item").assertIsDisplayed()

        covered = true
        composeRule.onNodeWithText("Secret item").assertDoesNotExist()

        covered = false
        composeRule.onNodeWithText("Secret item").assertIsDisplayed()
    }
}
