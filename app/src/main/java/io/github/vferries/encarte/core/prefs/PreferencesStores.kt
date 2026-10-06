package io.github.vferries.encarte.core.prefs

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import java.io.File
import java.io.IOException

private const val TAG = "PreferencesStores"
private const val FIRST_RETRY_DELAY_MS = 1_000L
private const val MAX_RETRY_DELAY_MS = 60_000L

/**
 * Opens a Preferences store that starts over from the defaults when its file is corrupted: without a handler every
 * read fails and every write throws until the file is deleted by hand.
 */
fun preferencesStore(
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    file: () -> File,
): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    corruptionHandler = ReplaceFileCorruptionHandler { e ->
        Log.e(TAG, "Corrupted preferences file replaced by the defaults", e)
        emptyPreferences()
    },
    scope = scope,
    produceFile = file,
)

/**
 * The store's values, or the defaults until a first value could be read. A read error ends DataStore's flow, so the
 * store is read again after a growing delay: without that, the app lock and the home screen would keep the defaults
 * until the process restarts. A value read starts the delays over. An edit made during a delay shows at the next read.
 */
fun DataStore<Preferences>.dataOrDefaults(tag: String, what: String): Flow<Preferences> = flow {
    var retryDelayMs = FIRST_RETRY_DELAY_MS
    var hasValue = false
    emitAll(
        data
            .onEach {
                hasValue = true
                retryDelayMs = FIRST_RETRY_DELAY_MS
            }
            .retryWhen { e, _ ->
                if (e !is IOException) return@retryWhen false
                Log.e(tag, "Cannot read $what, reading again in $retryDelayMs ms", e)
                // A value already read beats the defaults: falling back to them would turn the app lock off.
                if (!hasValue) emit(emptyPreferences())
                delay(retryDelayMs)
                retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                true
            },
    )
}
