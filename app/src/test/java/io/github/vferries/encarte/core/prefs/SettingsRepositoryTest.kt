package io.github.vferries.encarte.core.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
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

    @Test
    fun theLockComesBackOnceTheSettingsCanBeReadAgain() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(tmp.root, "settings.preferences_pb")
        }
        SettingsRepository(dataStore).setLockEnabled(true)
        val settings = SettingsRepository(dataStore.failingFirstRead())

        assertEquals(listOf(false, true), settings.lockEnabled.take(2).toList())
    }

    private fun DataStore<Preferences>.failingFirstRead(): DataStore<Preferences> {
        val real = this
        var failed = false
        return object : DataStore<Preferences> by real {
            override val data: Flow<Preferences> = flow {
                if (!failed) {
                    failed = true
                    throw IOException("disk")
                }
                emitAll(real.data)
            }
        }
    }
}
