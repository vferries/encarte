package io.github.vferries.encarte.core.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.repository() = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(tmp.root, "settings.preferences_pb")
        }
    )

    @Test
    fun defaults() = runTest {
        val settings = repository()

        assertEquals(SortOrder.NAME, settings.sortOrder.first())
        assertFalse(settings.lockEnabled.first())
        assertTrue(settings.nfcBlockEnabled.first())
    }

    @Test
    fun persistsChanges() = runTest {
        val settings = repository()

        settings.setSortOrder(SortOrder.RECENTLY_USED)
        settings.setLockEnabled(true)
        settings.setNfcBlockEnabled(false)

        assertEquals(SortOrder.RECENTLY_USED, settings.sortOrder.first())
        assertTrue(settings.lockEnabled.first())
        assertFalse(settings.nfcBlockEnabled.first())
    }
}
