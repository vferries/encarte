package io.github.vferries.encarte.groups

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.cards.list.cardCollator
import io.github.vferries.encarte.core.data.CardGroup
import io.github.vferries.encarte.core.data.CardRepository
import io.github.vferries.encarte.core.data.GroupNameResult
import io.github.vferries.encarte.core.data.GroupRepository
import io.github.vferries.encarte.core.data.ImageStore
import io.github.vferries.encarte.testing.inMemoryDatabase
import io.github.vferries.encarte.testing.testCard
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock

@RunWith(AndroidJUnit4::class)
class GroupCardsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val db = inMemoryDatabase()

    @After
    fun tearDown() = db.close()

    private val state = GroupCardsUiState(
        isLoading = false,
        group = CardGroup(id = 1, name = "Courses"),
        rows = listOf(GroupCardRow(testCard("Fnac", id = 7), isMember = true), GroupCardRow(testCard("Zara", id = 8), isMember = false)),
    )

    @Test
    fun rowsShowAndToggleTheirMembership() {
        val toggles = mutableListOf<Pair<Long, Boolean>>()
        composeRule.setContent {
            GroupCardsScreen(state, TextFieldState(), onToggle = { id, member -> toggles += id to member }, onBack = {})
        }

        composeRule.onNodeWithText("Courses").assertIsDisplayed()
        composeRule.onNodeWithText("Fnac").assertIsOn()
        composeRule.onNodeWithText("Zara").assertIsOff()
        composeRule.onNodeWithText("Zara").performClick()
        composeRule.onNodeWithText("Fnac").performClick()

        assertEquals(listOf(8L to true, 7L to false), toggles)
    }

    @Test
    fun backLeavesTheScreen() {
        var back = false
        composeRule.setContent { GroupCardsScreen(state, TextFieldState(), onToggle = { _, _ -> }, onBack = { back = true }) }

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertTrue(back)
    }

    @Test
    fun theScreenClosesWhenItsGroupIsDeleted() {
        val groups = GroupRepository(db)
        val cards = CardRepository(db, ImageStore(File(tmp.root, "i"), File(tmp.root, "s")), Clock.systemUTC())
        val courses = runBlocking { (groups.create("Courses") as GroupNameResult.Saved).id }
        var closed = false
        composeRule.setContent {
            GroupCardsRoute(GroupCardsViewModel(courses, cards, groups, cardCollator()), onBack = { closed = true })
        }
        // Room delivers on its own threads, which the compose rule doesn't wait for.
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("Courses").fetchSemanticsNodes().isNotEmpty() }

        runBlocking { groups.delete(courses) }

        // The condition must idle the main looper itself: Room's result reaches compose through it.
        composeRule.waitUntil(5_000) {
            composeRule.waitForIdle()
            closed
        }
    }
}
