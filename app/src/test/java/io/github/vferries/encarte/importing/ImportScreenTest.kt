package io.github.vferries.encarte.importing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun saysTheFileIsBeingRead() {
        composeRule.setContent { ImportScreen() }

        composeRule.onNodeWithText("Reading the file…").assertIsDisplayed()
    }
}
