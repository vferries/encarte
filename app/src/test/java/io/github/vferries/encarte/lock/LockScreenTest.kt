package io.github.vferries.encarte.lock

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LockScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun promptsOnAppearAndOnButton() {
        var prompts = 0
        composeRule.setContent { LockScreen(onUnlock = { prompts++ }) }

        composeRule.onNodeWithText("Encarté is locked").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, prompts) }

        composeRule.onNodeWithText("Unlock").performClick()

        composeRule.runOnIdle { assertEquals(2, prompts) }
    }
}
