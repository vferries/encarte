package io.github.vferries.encarte.importing

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImportChoiceScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val qr = FoundCode("LOYALTY-QR-1", BarcodeFormat.QR_CODE, page = 1)
    private val ean = FoundCode("4006381333931", BarcodeFormat.EAN_13, page = 2)
    private val maxiCode = FoundCode("MAXI-7", null, page = 2)
    private var chosen: FoundCode? = null
    private var backs = 0

    private fun setScreen(codes: List<FoundCode>) = composeRule.setContent {
        ImportChoiceScreen(codes, onBack = { backs++ }, onChoose = { chosen = it })
    }

    @Test
    fun eachCodeShowsDrawnWithItsValuePageAndFormat() {
        setScreen(listOf(qr, ean))

        composeRule.onNodeWithText("Choose a code").assertIsDisplayed()
        composeRule.onNodeWithText("LOYALTY-QR-1").assertIsDisplayed()
        composeRule.onNodeWithText("Page 1 · QR Code").assertIsDisplayed()
        composeRule.onNodeWithText("Page 2 · EAN-13").assertIsDisplayed()
        // Each row is one clickable node: the drawn codes are only in the unmerged tree.
        composeRule.onAllNodesWithTag("barcode", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun aCodeEncarteCannotDrawShowsItsValueAlone() {
        setScreen(listOf(qr, maxiCode))

        composeRule.onNodeWithText("MAXI-7").assertIsDisplayed()
        composeRule.onNodeWithText("Page 2").assertIsDisplayed()
        composeRule.onAllNodesWithTag("barcode", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun tappingARowChoosesItsCode() {
        setScreen(listOf(qr, ean))

        composeRule.onNodeWithText("4006381333931").performClick()

        composeRule.runOnIdle { assertEquals(ean, chosen) }
    }

    @Test
    fun theBackArrowLeaves() {
        setScreen(listOf(qr, ean))

        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.runOnIdle { assertTrue(backs == 1 && chosen == null) }
    }
}
