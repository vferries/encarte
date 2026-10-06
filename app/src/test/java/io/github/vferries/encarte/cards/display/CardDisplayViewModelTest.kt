package io.github.vferries.encarte.cards.display

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.core.data.CardRepository
import io.github.vferries.encarte.core.data.ExpiryStatus
import io.github.vferries.encarte.core.data.ImageStore
import io.github.vferries.encarte.core.prefs.SettingsRepository
import io.github.vferries.encarte.testing.MainDispatcherRule
import io.github.vferries.encarte.testing.inMemoryDatabase
import io.github.vferries.encarte.testing.testCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class CardDisplayViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val now = Instant.parse("2026-10-04T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val db = inMemoryDatabase()
    private val cards by lazy {
        CardRepository(
            db,
            ImageStore(File(tmp.root, "images"), File(tmp.root, "staging")),
            clock,
        )
    }

    private fun TestScope.settings() = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "s.preferences_pb") }
    )

    private fun TestScope.displayViewModel(id: Long, settings: SettingsRepository = settings()) =
        CardDisplayViewModel(id, cards, clock, settings)

    @After
    fun tearDown() = db.close()

    @Test
    fun openingMarksCardAsUsed() = runTest {
        val id = cards.save(testCard("Fnac"))

        val vm = displayViewModel(id)

        assertEquals(now, vm.uiState.first { it.card?.lastUsedAt != null }.card!!.lastUsedAt)
    }

    @Test
    fun toggleFavoriteFlipsIt() = runTest {
        val id = cards.save(testCard("Fnac"))
        val vm = displayViewModel(id)
        vm.uiState.first { it.card != null }

        vm.toggleFavorite()

        assertTrue(vm.uiState.first { it.card?.isFavorite == true }.card!!.isFavorite)
    }

    @Test
    fun deleteRemovesCardAndFlagsState() = runTest {
        val id = cards.save(testCard("Fnac"))
        val vm = displayViewModel(id)
        vm.uiState.first { it.card != null }

        vm.delete()

        assertTrue(vm.uiState.first { it.isDeleted }.isDeleted)
        assertNull(cards.get(id))
    }

    @Test
    fun missingCardIsReportedAsNotFound() = runTest {
        val vm = displayViewModel(404)

        val state = vm.uiState.first { !it.isLoading }

        assertNull(state.card)
    }

    @Test
    fun expiryStatusIsComputedForToday() = runTest {
        val id = cards.save(testCard("Fnac", expiresOn = LocalDate.of(2026, 10, 3)))

        val vm = displayViewModel(id)

        assertEquals(ExpiryStatus.Expired, vm.uiState.first { it.card != null }.expiry)
    }

    @Test
    fun archivingFlagsTheScreenToLeave() = runTest {
        val id = cards.save(testCard("Fnac"))
        val vm = displayViewModel(id)
        vm.uiState.first { it.card != null }

        vm.toggleArchived()

        assertTrue(vm.uiState.first { it.justArchived && it.card?.isArchived == true }.justArchived)
    }

    @Test
    fun unarchivingStaysOnTheScreen() = runTest {
        val id = cards.save(testCard("Fnac", isArchived = true))
        val vm = displayViewModel(id)
        vm.uiState.first { it.card != null }

        vm.toggleArchived()

        assertFalse(vm.uiState.first { it.card?.isArchived == false }.justArchived)
    }

    @Test
    fun contactlessBlockingFollowsTheSetting() = runTest {
        val id = cards.save(testCard("Fnac"))
        val settings = settings()
        val vm = displayViewModel(id, settings)
        assertTrue(vm.uiState.first { it.card != null }.blockContactless)

        settings.setNfcBlockEnabled(false)

        assertFalse(vm.uiState.first { !it.blockContactless }.blockContactless)
    }
}
