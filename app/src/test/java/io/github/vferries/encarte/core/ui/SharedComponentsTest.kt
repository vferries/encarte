package io.github.vferries.encarte.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.core.data.ExpiryStatus
import io.github.vferries.encarte.testing.testCard
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SharedComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun barcodeImageDescribesFormatAndValue() {
        composeRule.setContent { BarcodeImage("4006381333931", BarcodeFormat.EAN_13) }

        composeRule.onNodeWithContentDescription("EAN-13 barcode: 4006381333931").assertIsDisplayed()
    }

    @Test
    fun undisplayableCodeExplainsWhyInsteadOfAnEmptyBox() {
        val message = "This code can't be displayed. Check the card's number and barcode type."
        composeRule.setContent { BarcodeImage("4006381333932", BarcodeFormat.EAN_13) }

        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText(message).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("EAN-13 barcode: 4006381333932").assertIsDisplayed()
    }

    @Test
    fun cardTileWithoutImageShowsStoreNameAndClicks() {
        var clicked = false
        composeRule.setContent { CardTile(testCard("Fnac"), imageFile = null, onClick = { clicked = true }) }

        composeRule.onNodeWithText("Fnac").assertIsDisplayed().performClick()

        assertTrue(clicked)
    }

    @Test
    fun expiryBadgeIsPartOfTheClickableTile() {
        composeRule.setContent {
            CardTile(testCard("Fnac"), imageFile = null, onClick = {}, expiry = ExpiryStatus.Soon(5))
        }

        composeRule.onNodeWithText("Expires in 5 d").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun expiryBadgeTextFollowsTheStatus() {
        var status by mutableStateOf<ExpiryStatus>(ExpiryStatus.Soon(0))
        composeRule.setContent { CardTile(testCard("Fnac"), imageFile = null, onClick = {}, expiry = status) }
        composeRule.onNodeWithText("Expires today").assertIsDisplayed()

        status = ExpiryStatus.Expired
        composeRule.onNodeWithText("Expired").assertIsDisplayed()

        status = ExpiryStatus.Later
        composeRule.onNodeWithText("Expired").assertDoesNotExist()
        composeRule.onNodeWithText("Expires", substring = true).assertDoesNotExist()
    }
}
