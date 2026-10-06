package io.github.vferries.encarte.cards.list

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.core.data.CardGroup
import io.github.vferries.encarte.core.data.ExpiryStatus
import io.github.vferries.encarte.core.data.GroupNameResult
import io.github.vferries.encarte.core.prefs.SortOrder
import io.github.vferries.encarte.importing.ImportFailure
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
        onSelectGroup: (Long?) -> Unit = {},
        onCreateGroup: suspend (String) -> GroupNameResult = { GroupNameResult.Saved(1) },
        onRenameGroup: suspend (Long, String) -> GroupNameResult = { id, _ -> GroupNameResult.Saved(id) },
        onDeleteGroup: (Long) -> Unit = {},
        onChooseCards: (Long) -> Unit = {},
        importFailure: ImportFailure? = null,
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
            onSelectGroup = onSelectGroup,
            onCreateGroup = onCreateGroup,
            onRenameGroup = onRenameGroup,
            onDeleteGroup = onDeleteGroup,
            onChooseCards = onChooseCards,
            importFailure = importFailure,
        )
    }

    private val courses = CardGroup(id = 1, name = "Courses")
    private val mode = CardGroup(id = 2, name = "Mode")

    private fun withGroups(
        selected: CardGroup? = null,
        others: List<CardTileModel> = listOf(tile("Fnac", 7)),
        selectedIsEmpty: Boolean = false,
    ) = CardListUiState(
        isLoading = false, hasCards = true, others = others,
        groups = listOf(courses, mode), selectedGroup = selected, selectedGroupIsEmpty = selectedIsEmpty,
    )

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
    // Tall enough for the group chip row above the second section: lazy items below the viewport are not composed.
    @Config(qualifiers = "w411dp-h891dp")
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
    fun blankQueryKeepsTheArchivedSectionCollapsed() {
        setScreen(
            CardListUiState(isLoading = false, hasCards = true, archived = listOf(tile("Darty", 2))),
            query = TextFieldState("   "),
        )

        composeRule.onNodeWithText("Archived (1)").assertIsDisplayed()
        composeRule.onNodeWithText("Darty").assertDoesNotExist()
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

    @Test
    fun aFileThatGaveNoCardSaysWhy() {
        setScreen(CardListUiState(isLoading = false, hasCards = false), importFailure = ImportFailure.NO_CODE_IN_PDF)

        composeRule.onNodeWithText("No barcode found in this PDF.").assertIsDisplayed()
    }

    @Test
    fun withoutGroupsOnlyTheNewGroupChipShows() {
        setScreen(CardListUiState(isLoading = false, hasCards = true, others = listOf(tile("Fnac", 7))))

        composeRule.onNodeWithText("New group").assertIsDisplayed()
        composeRule.onNodeWithText("All").assertDoesNotExist()
    }

    @Test
    fun chipsSelectAGroupOrAll() {
        val selections = mutableListOf<Long?>()
        setScreen(withGroups(selected = courses), onSelectGroup = { selections += it })

        composeRule.onNodeWithText("Courses").assertIsSelected()
        composeRule.onNodeWithText("Mode").performClick()
        composeRule.onNodeWithText("All").performClick()

        assertEquals(listOf(2L, null), selections)
    }

    @Test
    fun aLongPressOffersTheGroupActionsAndIsLabelledForAccessibility() {
        var chosen: Long? = null
        setScreen(withGroups(), onChooseCards = { chosen = it })

        composeRule.onNodeWithText("Courses").assert(
            SemanticsMatcher("long click labelled Group options") {
                it.config.getOrNull(SemanticsActions.OnLongClick)?.label == "Group options"
            }
        )
        composeRule.onNodeWithText("Courses").performTouchInput { longClick() }
        composeRule.onNodeWithText("Rename").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
        composeRule.onNodeWithText("Choose cards").performClick()

        assertEquals(1L, chosen)
    }

    @Test
    fun renameShowsWhyANameIsRefused() {
        val answers = ArrayDeque(listOf(GroupNameResult.Duplicate, GroupNameResult.Blank, GroupNameResult.Saved(1)))
        val names = mutableListOf<String>()
        setScreen(withGroups(), onRenameGroup = { _, name -> names += name; answers.removeFirst() })
        composeRule.onNodeWithText("Courses").performTouchInput { longClick() }
        composeRule.onNodeWithText("Rename").performClick()
        composeRule.onNodeWithText("Rename group").assertIsDisplayed()

        composeRule.onNodeWithText("Group name").performTextReplacement("Mode")
        composeRule.onNodeWithText("Rename").performClick()
        composeRule.onNodeWithText("This group already exists").assertIsDisplayed()

        composeRule.onNodeWithText("Group name").performTextReplacement("")
        composeRule.onNodeWithText("Rename").performClick()
        composeRule.onNodeWithText("Enter a name").assertIsDisplayed()

        composeRule.onNodeWithText("Group name").performTextReplacement("Marché")
        composeRule.onNodeWithText("Rename").performClick()
        composeRule.onNodeWithText("Rename group").assertDoesNotExist()

        assertEquals(listOf("Mode", "", "Marché"), names)
    }

    @Test
    fun deleteAsksForConfirmation() {
        var deleted: Long? = null
        setScreen(withGroups(), onDeleteGroup = { deleted = it })
        composeRule.onNodeWithText("Courses").performTouchInput { longClick() }
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.onNodeWithText("Delete \"Courses\"?").assertIsDisplayed()
        composeRule.onNodeWithText("The cards are not deleted.").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()

        assertEquals(1L, deleted)
    }

    @Test
    fun aNewGroupIsCreatedThenOpened() {
        var created: String? = null
        var chosen: Long? = null
        setScreen(withGroups(), onCreateGroup = { created = it; GroupNameResult.Saved(5) }, onChooseCards = { chosen = it })

        composeRule.onNodeWithText("New group").performClick()
        composeRule.onNodeWithText("Group name").performTextInput("Bricolage")
        composeRule.onNodeWithText("Create").performClick()
        composeRule.waitForIdle()

        assertEquals("Bricolage", created)
        assertEquals(5L, chosen)
    }

    @Test
    fun anEmptyGroupOffersToChooseItsCards() {
        var chosen: Long? = null
        setScreen(withGroups(selected = courses, others = emptyList(), selectedIsEmpty = true), onChooseCards = { chosen = it })

        composeRule.onNodeWithText("No cards in \"Courses\"").assertIsDisplayed()
        composeRule.onNodeWithText("Choose cards").performClick()

        assertEquals(1L, chosen)
    }

    @Test
    fun aSearchOutsideTheGroupSaysNothingMatches() {
        setScreen(withGroups(selected = courses, others = emptyList()), query = TextFieldState("zara"))

        composeRule.onNodeWithText("No card matches your search.").assertIsDisplayed()
        composeRule.onNodeWithText("No cards in \"Courses\"").assertDoesNotExist()
    }
}
