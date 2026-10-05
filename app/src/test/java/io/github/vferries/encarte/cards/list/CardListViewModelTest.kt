package io.github.vferries.encarte.cards.list

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.core.data.CardRepository
import io.github.vferries.encarte.core.data.ExpiryStatus
import io.github.vferries.encarte.core.data.ImageStore
import io.github.vferries.encarte.core.prefs.SettingsRepository
import io.github.vferries.encarte.core.prefs.SortOrder
import io.github.vferries.encarte.testing.MainDispatcherRule
import io.github.vferries.encarte.testing.inMemoryDatabase
import io.github.vferries.encarte.testing.testCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class CardListViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val clock = Clock.fixed(Instant.parse("2026-10-05T10:00:00Z"), ZoneOffset.UTC)
    private val db = inMemoryDatabase()
    private val cards by lazy {
        CardRepository(db.cardDao(), ImageStore(File(tmp.root, "images"), File(tmp.root, "staging")), Clock.systemUTC())
    }

    @After
    fun tearDown() = db.close()

    private fun TestScope.viewModel(): Pair<CardListViewModel, SettingsRepository> {
        val settings = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "s.preferences_pb") }
        )
        return CardListViewModel(cards, settings, cardCollator(Locale.FRANCE), clock) to settings
    }

    @Test
    fun emptyDatabaseReportsNoCards() = runTest {
        val (vm, _) = viewModel()

        val state = vm.uiState.first { !it.isLoading }

        assertFalse(state.hasCards)
    }

    @Test
    fun groupsFavoritesAndFiltersWithQuery() = runTest {
        cards.save(testCard("Fnac", isFavorite = true))
        cards.save(testCard("Auchan"))
        cards.save(testCard("Zara"))
        val (vm, _) = viewModel()

        val all = vm.uiState.first { it.others.size == 2 }
        assertEquals(listOf("Fnac"), all.favorites.map { it.card.storeName })
        assertEquals(listOf("Auchan", "Zara"), all.others.map { it.card.storeName })

        vm.query.setTextAndPlaceCursorAtEnd("zar")
        Snapshot.sendApplyNotifications()

        val filtered = vm.uiState.first { it.others.size == 1 }
        assertEquals("Zara", filtered.others.single().card.storeName)
        assertEquals(emptyList<CardTileModel>(), filtered.favorites)
    }

    @Test
    fun sortOrderIsPersisted() = runTest {
        val (vm, settings) = viewModel()

        vm.setSortOrder(SortOrder.RECENTLY_USED)

        assertEquals(SortOrder.RECENTLY_USED, settings.sortOrder.first { it == SortOrder.RECENTLY_USED })
        assertEquals(SortOrder.RECENTLY_USED, vm.uiState.first { it.sortOrder == SortOrder.RECENTLY_USED }.sortOrder)
    }

    @Test
    fun archivedCardsAreListedApart() = runTest {
        cards.save(testCard("Darty", isFavorite = true, isArchived = true))
        cards.save(testCard("Fnac"))
        val (vm, _) = viewModel()

        val state = vm.uiState.first { !it.isLoading && it.hasCards }

        assertEquals(listOf("Darty"), state.archived.map { it.card.storeName })
        assertEquals(emptyList<CardTileModel>(), state.favorites)
        assertEquals(listOf("Fnac"), state.others.map { it.card.storeName })
    }

    @Test
    fun tilesCarryTheirExpiryStatusForToday() = runTest {
        cards.save(testCard("Fnac", expiresOn = LocalDate.of(2026, 10, 10)))
        val (vm, _) = viewModel()

        val tile = vm.uiState.first { !it.isLoading && it.hasCards }.others.single()

        assertEquals(ExpiryStatus.Soon(5), tile.expiry)
    }

    @Test
    fun unarchiveBringsAFavoriteBackUnderFavorites() = runTest {
        val id = cards.save(testCard("Darty", isFavorite = true, isArchived = true))
        val (vm, _) = viewModel()
        vm.uiState.first { it.archived.isNotEmpty() }

        vm.unarchive(id)

        val state = vm.uiState.first { it.archived.isEmpty() && it.hasCards }
        assertEquals(listOf("Darty"), state.favorites.map { it.card.storeName })
    }
}
