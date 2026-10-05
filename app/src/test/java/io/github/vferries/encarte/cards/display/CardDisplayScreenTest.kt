package io.github.vferries.encarte.cards.display

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.core.data.ExpiryStatus
import io.github.vferries.encarte.lock.LocalContentCovered
import io.github.vferries.encarte.testing.testCard
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

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
    fun deleteConfirmationHidesWhileTheLockCoversTheScreen() {
        var covered by mutableStateOf(false)
        composeRule.setContent {
            CompositionLocalProvider(LocalContentCovered provides covered) {
                CardDisplayScreen(CardDisplayUiState(isLoading = false, card = testCard("Fnac")), {}, {}, {}, {})
            }
        }
        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete this card?").assertIsDisplayed()

        covered = true
        composeRule.onNodeWithText("Delete this card?").assertDoesNotExist()

        covered = false
        composeRule.onNodeWithText("Delete this card?").assertIsDisplayed()
    }

    @Test
    fun missingCardShowsMessage() {
        composeRule.setContent { CardDisplayScreen(CardDisplayUiState(isLoading = false), {}, {}, {}, {}) }

        composeRule.onNodeWithText("This card no longer exists.").assertIsDisplayed()
    }

    @Test
    fun expiryDateIsShownBelowTheNumber() {
        val card = testCard("Fnac", cardNumber = "A-42", expiresOn = LocalDate.of(2027, 3, 12))
        composeRule.setContent {
            CardDisplayScreen(CardDisplayUiState(isLoading = false, card = card, expiry = ExpiryStatus.Later), {}, {}, {}, {})
        }

        composeRule.onNodeWithText("Expires on Mar 12, 2027").assertIsDisplayed()
    }

    @Test
    fun pastExpiryIsShownAsExpired() {
        val card = testCard("Fnac", expiresOn = LocalDate.of(2027, 3, 12))
        composeRule.setContent {
            CardDisplayScreen(CardDisplayUiState(isLoading = false, card = card, expiry = ExpiryStatus.Expired), {}, {}, {}, {})
        }

        composeRule.onNodeWithText("Expired on Mar 12, 2027").assertIsDisplayed()
    }

    @Test
    fun noExpiryLineWithoutADate() {
        composeRule.setContent {
            CardDisplayScreen(CardDisplayUiState(isLoading = false, card = testCard("Fnac")), {}, {}, {}, {})
        }

        composeRule.onNodeWithText("Expire", substring = true).assertDoesNotExist()
    }
}
