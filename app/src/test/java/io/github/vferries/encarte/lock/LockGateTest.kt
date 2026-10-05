package io.github.vferries.encarte.lock

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.core.ui.EncarteAlertDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LockGateTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var prompts = 0
    private var unavailable = 0

    private fun setGate(state: LockState, deviceSecure: Boolean = true) = composeRule.setContent {
        LockGate(state, deviceSecure, onUnlockRequest = { prompts++ }, onLockUnavailable = { unavailable++ }) {
            Text("Secret content")
        }
    }

    @Test
    fun unlockedShowsContent() {
        setGate(LockState.UNLOCKED)

        composeRule.onNodeWithText("Secret content").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, prompts) }
    }

    @Test
    fun lockedHidesContentAndPrompts() {
        setGate(LockState.LOCKED)

        composeRule.onNodeWithText("Encarté is locked").assertIsDisplayed()
        composeRule.onNodeWithText("Secret content").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, prompts) }
    }

    @Test
    fun loadingHidesContentWithoutPrompting() {
        setGate(LockState.LOADING)

        composeRule.onNodeWithText("Secret content").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, prompts) }
    }

    @Test
    fun dialogsAreHiddenWhileLockedAndComeBackAfterUnlocking() {
        var state by mutableStateOf(LockState.UNLOCKED)
        composeRule.setContent {
            LockGate(state, deviceSecure = true, onUnlockRequest = { prompts++ }, onLockUnavailable = {}) {
                // The app's dialogs go through the coverable wrappers: a dialog is its own window, above the lock screen.
                EncarteAlertDialog(onDismissRequest = {}, confirmButton = {}, text = { Text("Secret dialog") })
            }
        }
        composeRule.onNodeWithText("Secret dialog").assertIsDisplayed()

        state = LockState.LOCKED
        composeRule.onNodeWithText("Secret dialog").assertDoesNotExist()

        state = LockState.UNLOCKED
        composeRule.onNodeWithText("Secret dialog").assertIsDisplayed()
    }

    @Test
    fun backDoesNotReachTheContentWhileLocked() {
        var state by mutableStateOf(LockState.UNLOCKED)
        var contentBacks = 0
        composeRule.setContent {
            LockGate(state, deviceSecure = true, onUnlockRequest = { prompts++ }, onLockUnavailable = {}) {
                BackHandler { contentBacks++ }
            }
        }
        val pressBack = { composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() } }

        pressBack()
        composeRule.runOnIdle { assertEquals(1, contentBacks) }

        state = LockState.LOCKED
        composeRule.waitForIdle()
        pressBack()
        composeRule.runOnIdle { assertEquals(1, contentBacks) }
    }

    @Test
    fun lockedWithoutScreenLockReportsInsteadOfPrompting() {
        setGate(LockState.LOCKED, deviceSecure = false)

        composeRule.runOnIdle {
            assertEquals(0, prompts)
            assertEquals(1, unavailable)
        }
    }
}
