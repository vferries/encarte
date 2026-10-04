package io.github.vferries.encarte.cards.list

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.core.data.CardRepository
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
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class CardListViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

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
        return CardListViewModel(cards, settings, cardCollator(Locale.FRANCE)) to settings
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
}
