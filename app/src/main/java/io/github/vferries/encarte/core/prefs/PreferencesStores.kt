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
import java.io.File

private const val TAG = "PreferencesStores"

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
