package io.github.vferries.encarte.groups

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.cards.list.cardCollator
import io.github.vferries.encarte.core.data.CardRepository
import io.github.vferries.encarte.core.data.GroupNameResult
import io.github.vferries.encarte.core.data.GroupRepository
import io.github.vferries.encarte.core.data.ImageStore
import io.github.vferries.encarte.testing.MainDispatcherRule
import io.github.vferries.encarte.testing.inMemoryDatabase
import io.github.vferries.encarte.testing.testCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class GroupCardsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val db = inMemoryDatabase()
    private val cards by lazy {
        CardRepository(db, ImageStore(File(tmp.root, "images"), File(tmp.root, "staging")), Clock.systemUTC())
    }
    private val groups = GroupRepository(db)

    @After
    fun tearDown() = db.close()

    private suspend fun group(name: String) = (groups.create(name) as GroupNameResult.Saved).id

    private fun viewModel(groupId: Long) = GroupCardsViewModel(groupId, cards, groups, cardCollator(Locale.FRANCE))

    @Test
    fun rowsListEveryCardByNameWithArchivedCardsLast() = runTest {
        cards.save(testCard("Zara"))
        cards.save(testCard("Darty", isArchived = true))
        val ecomarche = cards.save(testCard("écomarché"))
        val courses = group("Courses")
        groups.setMembership(ecomarche, courses, member = true)

        val state = viewModel(courses).uiState.first { it.rows.size == 3 }

        assertEquals("Courses", state.group?.name)
        assertEquals(listOf("écomarché", "Zara", "Darty"), state.rows.map { it.card.storeName })
        assertEquals(listOf(true, false, false), state.rows.map { it.isMember })
    }

    @Test
    fun aTickIsWrittenImmediately() = runTest {
        val fnac = cards.save(testCard("Fnac"))
        val courses = group("Courses")
        val vm = viewModel(courses)

        vm.setMember(fnac, true)

        vm.uiState.first { it.rows.singleOrNull()?.isMember == true }
        assertEquals(setOf(courses), groups.groupIdsOf(fnac))
    }

    @Test
    fun theSearchFiltersTheRows() = runTest {
        cards.save(testCard("Fnac"))
        cards.save(testCard("Zara"))
        val vm = viewModel(group("Courses"))

        vm.query.setTextAndPlaceCursorAtEnd("zar")
        Snapshot.sendApplyNotifications()

        assertEquals(listOf("Zara"), vm.uiState.first { it.rows.size == 1 }.rows.map { it.card.storeName })
    }

    @Test
    fun aDeletedGroupLeavesNoGroup() = runTest {
        val courses = group("Courses")
        val vm = viewModel(courses)
        vm.uiState.first { it.group != null }

        groups.delete(courses)

        assertNull(vm.uiState.first { !it.isLoading && it.group == null }.group)
    }

    @Test
    fun aTickRacingTheGroupDeletionDoesNotCrash() = runTest {
        val fnac = cards.save(testCard("Fnac"))
        val courses = group("Courses")
        val vm = viewModel(courses)
        groups.delete(courses)

        // The foreign key fails: the exception must be logged, not thrown into viewModelScope.
        vm.setMember(fnac, true)

        assertEquals(emptySet<Long>(), groups.groupIdsOf(fnac))
        assertNull(vm.uiState.first { !it.isLoading && it.group == null }.group)
    }
}
