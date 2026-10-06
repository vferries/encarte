package io.github.vferries.encarte.core.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PreferencesStoresTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun aCorruptedFileReadsAsDefaultsAndAcceptsWrites() = runTest {
        val file = File(tmp.root, "settings.preferences_pb").apply { writeBytes(ByteArray(64) { 0xFF.toByte() }) }
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = preferencesStore(scope = firstScope) { file }
        val key = booleanPreferencesKey("flag")

        assertEquals(emptyPreferences(), store.data.first())
        store.edit { it[key] = true }

        assertEquals(true, store.data.first()[key])
        // DataStore allows one active instance per file: a fresh store proves the value reached the disk.
        firstScope.cancel()
        firstScope.coroutineContext[Job]!!.join()
        assertEquals(true, preferencesStore(scope = backgroundScope) { file }.data.first()[key])
    }
}
