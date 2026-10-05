package io.github.vferries.encarte.cards.list

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.core.data.ExpiryStatus
import io.github.vferries.encarte.core.prefs.SortOrder
import io.github.vferries.encarte.testing.testCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class CardListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setScreen(
        state: CardListUiState,
        query: TextFieldState = TextFieldState(),
        archivedNotice: Long? = null,
        onOpenCard: (Long) -> Unit = {},
        onImport: () -> Unit = {},
        onSortOrderChange: (SortOrder) -> Unit = {},
        onUndoArchive: (Long) -> Unit = {},
        onArchivedNoticeShown: () -> Unit = {},
    ) = composeRule.setContent {
        CardListScreen(
            state = state,
            query = query,
            archivedNotice = archivedNotice,
            onSortOrderChange = onSortOrderChange,
            onOpenCard = onOpenCard,
            onAddCard = {},
            onOpenSettings = {},
            onImport = onImport,
            onUndoArchive = onUndoArchive,
            onArchivedNoticeShown = onArchivedNoticeShown,
        )
    }

    private fun tile(name: String, id: Long, expiry: ExpiryStatus = ExpiryStatus.None) =
        CardTileModel(testCard(name, id = id), image = null, expiry = expiry)

    @Test
    fun emptyStateOffersImport() {
        var imported = false
        setScreen(CardListUiState(isLoading = false, hasCards = false), onImport = { imported = true })

        composeRule.onNodeWithText("No cards yet").assertIsDisplayed()
        composeRule.onNodeWithText("Import").performClick()

        assertTrue(imported)
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land")
    fun emptyStateKeepsImportOnScreenInLandscape() {
        setScreen(CardListUiState(isLoading = false, hasCards = false))

        composeRule.onNodeWithText("Import").assertIsDisplayed()
    }

    @Test
    fun tilesOpenTheirCard() {
        var opened = -1L
        val state = CardListUiState(
            isLoading = false,
            hasCards = true,
            favorites = listOf(CardTileModel(testCard("Fnac", id = 7), image = null)),
            others = listOf(CardTileModel(testCard("Auchan", id = 8), image = null)),
        )
        setScreen(state, onOpenCard = { opened = it })

        composeRule.onNodeWithText("Favorites").assertIsDisplayed()
        composeRule.onNodeWithText("Auchan").performClick()

        assertEquals(8L, opened)
    }

    @Test
    fun sortMenuChangesOrder() {
        var order: SortOrder? = null
        setScreen(CardListUiState(isLoading = false, hasCards = true), onSortOrderChange = { order = it })

        composeRule.onNodeWithContentDescription("Sort").performClick()
        composeRule.onNodeWithText("Recently used").performClick()

        assertEquals(SortOrder.RECENTLY_USED, order)
    }

    @Test
    fun archivedSectionIsCollapsedUntilOpened() {
        setScreen(CardListUiState(isLoading = false, hasCards = true, archived = listOf(tile("Darty", 2))))

        composeRule.onNodeWithText("Archived (1)").assertIsDisplayed()
        composeRule.onNodeWithText("Darty").assertDoesNotExist()

        composeRule.onNodeWithText("Archived (1)").performClick()

        composeRule.onNodeWithText("Darty").assertIsDisplayed()
    }

    @Test
    fun noArchivedHeaderWithoutArchivedCards() {
        setScreen(CardListUiState(isLoading = false, hasCards = true, others = listOf(tile("Fnac", 1))))

        composeRule.onNodeWithText("Archived", substring = true).assertDoesNotExist()
    }

    @Test
    fun searchShowsArchivedMatchesWithoutOpeningTheSection() {
        setScreen(
            CardListUiState(isLoading = false, hasCards = true, archived = listOf(tile("Darty", 2))),
            query = TextFieldState("dar"),
        )

        composeRule.onNodeWithText("Darty").assertIsDisplayed()
        composeRule.onNodeWithText("No card matches your search.").assertDoesNotExist()
    }

    @Test
    fun tilesShowTheirExpiryBadge() {
        setScreen(CardListUiState(isLoading = false, hasCards = true, others = listOf(tile("Fnac", 1, ExpiryStatus.Soon(5)))))

        composeRule.onNodeWithText("Expires in 5 d").assertIsDisplayed()
    }

    @Test
    fun sortMenuOffersExpiry() {
        var order: SortOrder? = null
        setScreen(CardListUiState(isLoading = false, hasCards = true), onSortOrderChange = { order = it })

        composeRule.onNodeWithContentDescription("Sort").performClick()
        composeRule.onNodeWithText("Expiry date").performClick()

        assertEquals(SortOrder.EXPIRY, order)
    }

    @Test
    fun archivedNoticeOffersUndo() {
        var undone: Long? = null
        var shown = false
        setScreen(
            CardListUiState(isLoading = false, hasCards = true),
            archivedNotice = 7,
            onUndoArchive = { undone = it },
            onArchivedNoticeShown = { shown = true },
        )

        composeRule.onNodeWithText("Card archived").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()

        assertEquals(7L, undone)
        assertTrue(shown)
    }
}
