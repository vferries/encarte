package io.github.vferries.encarte.core.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

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

    @Test
    fun aReadErrorGivesTheDefaultsUntilTheStoreCanBeReadAgain() = runTest {
        val store = FlakyStore(listOf(Read.FAIL, Read.FAIL, Read.VALUE), now = { testScheduler.currentTime })

        val values = store.dataOrDefaults(tag = "Test", what = "the test values").toList()

        assertEquals(listOf(emptyPreferences(), emptyPreferences(), STORED), values)
    }

    @Test
    fun theRetriesWaitLongerEachTimeUpToAMinute() = runTest {
        val store = FlakyStore(List(9) { Read.FAIL } + Read.VALUE, now = { testScheduler.currentTime })

        store.dataOrDefaults(tag = "Test", what = "the test values").toList()

        val waits = store.readTimes.zipWithNext { a, b -> b - a }
        assertEquals(listOf(1_000L, 2_000, 4_000, 8_000, 16_000, 32_000, 60_000, 60_000, 60_000), waits)
    }

    @Test
    fun aSuccessfulReadStartsTheWaitsOver() = runTest {
        val reads = listOf(Read.FAIL, Read.FAIL, Read.VALUE_THEN_FAIL, Read.FAIL, Read.VALUE)
        val store = FlakyStore(reads, now = { testScheduler.currentTime })

        store.dataOrDefaults(tag = "Test", what = "the test values").toList()

        val waits = store.readTimes.zipWithNext { a, b -> b - a }
        assertEquals(listOf(1_000L, 2_000, 1_000, 2_000), waits)
    }

    @Test
    fun anErrorThatIsNotAReadErrorIsNotHidden() = runTest {
        val store = FlakyStore(listOf(Read.FAIL), now = { testScheduler.currentTime }) { IllegalStateException("bug") }

        val error = runCatching { store.dataOrDefaults(tag = "Test", what = "the test values").first() }
            .exceptionOrNull()

        assertEquals("bug", (error as? IllegalStateException)?.message)
    }

    private enum class Read { FAIL, VALUE, VALUE_THEN_FAIL }

    /** Plays one [Read] per subscription, failing with [failure]. Records when each read started. */
    private class FlakyStore(
        reads: List<Read>,
        private val now: () -> Long,
        private val failure: () -> Throwable = { IOException("disk") },
    ) : DataStore<Preferences> {
        private val script = ArrayDeque(reads)
        val readTimes = mutableListOf<Long>()

        override val data: Flow<Preferences> = flow {
            readTimes += now()
            val read = script.removeFirst()
            if (read != Read.FAIL) emit(STORED)
            if (read != Read.VALUE) throw failure()
        }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            throw UnsupportedOperationException("read-only test store")
    }

    private companion object {
        val STORED: Preferences =
            emptyPreferences().toMutablePreferences().apply { this[booleanPreferencesKey("flag")] = true }
    }
}
