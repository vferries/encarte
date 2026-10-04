package io.github.vferries.encarte.lock

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LockGateTest {
    @get:Rule
    val composeRule = createComposeRule()

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
    fun lockedWithoutScreenLockReportsInsteadOfPrompting() {
        setGate(LockState.LOCKED, deviceSecure = false)

        composeRule.runOnIdle {
            assertEquals(0, prompts)
            assertEquals(1, unavailable)
        }
    }
}
