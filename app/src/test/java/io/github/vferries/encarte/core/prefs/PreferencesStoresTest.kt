package io.github.vferries.encarte.core.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        val store = preferencesStore(scope = backgroundScope) { file }
        val key = booleanPreferencesKey("flag")

        assertEquals(emptyPreferences(), store.data.first())
        store.edit { it[key] = true }

        assertTrue(store.data.first()[key] == true)
    }
}
