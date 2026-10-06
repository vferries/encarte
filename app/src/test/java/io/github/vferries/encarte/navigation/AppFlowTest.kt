package io.github.vferries.encarte.navigation

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.EncarteApp
import io.github.vferries.encarte.MainActivity
import io.github.vferries.encarte.testing.testCard
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun waitFor(matcher: SemanticsMatcher) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForText(text: String) = waitFor(hasText(text))

    @Test
    fun addCardManuallyThenFindItInTheList() {
        waitForText("No cards yet")
        composeRule.onAllNodesWithText("Add a card").onFirst().performClick()

        // Robolectric denies the camera permission: the manual path must still be available.
        waitForText("Enter manually")
        composeRule.onNodeWithText("Enter manually").performClick()

        waitForText("New card")
        composeRule.onNodeWithText("Store").performTextInput("Fnac")
        composeRule.onNodeWithText("Card number").performTextInput("A-42")
        composeRule.onNodeWithText("Save").performClick()

        // After creating a card, the display replaces the editor ("Edit" only exists on the display).
        waitFor(hasContentDescription("Edit"))
        composeRule.onNodeWithText("A-42").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()

        // Back lands on the list (the sort menu only exists there), not on the editor.
        waitFor(hasContentDescription("Sort"))
        composeRule.onNodeWithText("Fnac").assertIsDisplayed()
    }

    @Test
    fun settingsAreReachableFromTheList() {
        waitForText("No cards yet")

        composeRule.onNodeWithContentDescription("Settings").performClick()

        waitForText("Export cards")
        composeRule.onNodeWithText("Lock the app").assertIsDisplayed()
    }

    @Test
    fun repeatedBackPressesNeverPopTheListRoot() {
        waitForText("No cards yet")
        composeRule.onNodeWithContentDescription("Settings").performClick()
        waitForText("Export cards")

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        waitForText("No cards yet")
        composeRule.onNodeWithText("No cards yet").assertIsDisplayed()
    }

    @Test
    fun archivingReturnsToTheListWhichOffersUndo() {
        waitForText("No cards yet")
        composeRule.onAllNodesWithText("Add a card").onFirst().performClick()
        waitForText("Enter manually")
        composeRule.onNodeWithText("Enter manually").performClick()
        waitForText("New card")
        composeRule.onNodeWithText("Store").performTextInput("Fnac")
        composeRule.onNodeWithText("Card number").performTextInput("A-42")
        composeRule.onNodeWithText("Save").performClick()
        waitFor(hasContentDescription("More options"))

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Archive").performClick()

        waitForText("Card archived")
        composeRule.onNodeWithText("Archived (1)").assertIsDisplayed()
        composeRule.onNodeWithText("Fnac").assertDoesNotExist()

        composeRule.onNodeWithText("Undo").performClick()

        waitForText("Fnac")
        composeRule.onNodeWithText("Archived (1)").assertDoesNotExist()
    }

    @Test
    fun aCardAddedWhileAGroupIsSelectedJoinsThatGroup() {
        val container = (composeRule.activity.application as EncarteApp).container
        runBlocking {
            container.cardRepository.save(testCard("Auchan"))
            container.groupRepository.create("Courses")
        }
        waitForText("Courses")
        composeRule.onNodeWithText("Courses").performClick()
        waitForText("No cards in \"Courses\"")

        composeRule.onNodeWithContentDescription("Add a card").performClick()
        waitForText("Enter manually")
        composeRule.onNodeWithText("Enter manually").performClick()
        waitForText("New card")
        composeRule.onNodeWithText("Store").performTextInput("Fnac")
        composeRule.onNodeWithText("Card number").performTextInput("A-42")
        composeRule.onNodeWithText("Save").performClick()
        waitFor(hasContentDescription("Edit"))
        composeRule.onNodeWithContentDescription("Back").performClick()

        // Still filtered on "Courses": the new card is there, the other one is not.
        waitForText("Fnac")
        composeRule.onNodeWithText("Auchan").assertDoesNotExist()
    }
}
