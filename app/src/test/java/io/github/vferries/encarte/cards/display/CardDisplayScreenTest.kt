package io.github.vferries.encarte.cards.display

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.testing.testCard
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardDisplayScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsBarcodeAndNumber() {
        val card = testCard("Fnac", cardNumber = "4006381333931", barcodeFormat = BarcodeFormat.EAN_13)
        composeRule.setContent {
            CardDisplayScreen(CardDisplayUiState(isLoading = false, card = card), {}, {}, {}, {})
        }

        composeRule.onNodeWithTag("barcode").assertIsDisplayed()
        composeRule.onNodeWithText("4006381333931").assertIsDisplayed()
    }

    @Test
    fun withoutBarcodeShowsNumberOnly() {
        composeRule.setContent {
            CardDisplayScreen(CardDisplayUiState(isLoading = false, card = testCard(cardNumber = "A-42")), {}, {}, {}, {})
        }

        composeRule.onNodeWithText("A-42").assertIsDisplayed()
        composeRule.onNodeWithTag("barcode").assertDoesNotExist()
    }

    @Test
    fun deleteAsksForConfirmation() {
        var deleted = false
        composeRule.setContent {
            CardDisplayScreen(
                CardDisplayUiState(isLoading = false, card = testCard("Fnac")),
                onBack = {}, onEdit = {}, onToggleFavorite = {}, onDelete = { deleted = true },
            )
        }

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete this card?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()

        assertTrue(deleted)
    }

    @Test
    fun missingCardShowsMessage() {
        composeRule.setContent { CardDisplayScreen(CardDisplayUiState(isLoading = false), {}, {}, {}, {}) }

        composeRule.onNodeWithText("This card no longer exists.").assertIsDisplayed()
    }
}
