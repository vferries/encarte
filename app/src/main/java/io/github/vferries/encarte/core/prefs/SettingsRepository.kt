package io.github.vferries.encarte.core.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val TAG = "SettingsRepository"

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private val preferences: Flow<Preferences> = dataStore.dataOrDefaults(TAG, what = "the settings")

    val sortOrder: Flow<SortOrder> = preferences
        .map { prefs -> prefs[SORT_ORDER]?.let { name -> SortOrder.entries.firstOrNull { it.name == name } } ?: SortOrder.NAME }
        .distinctUntilChanged()

    val lockEnabled: Flow<Boolean> = preferences
        .map { it[LOCK_ENABLED] ?: false }
        .distinctUntilChanged()

    val nfcBlockEnabled: Flow<Boolean> = preferences
        .map { it[NFC_BLOCK_ENABLED] ?: true }
        .distinctUntilChanged()

    suspend fun setSortOrder(order: SortOrder) {
        dataStore.edit { it[SORT_ORDER] = order.name }
    }

    suspend fun setLockEnabled(enabled: Boolean) {
        dataStore.edit { it[LOCK_ENABLED] = enabled }
    }

    suspend fun setNfcBlockEnabled(enabled: Boolean) {
        dataStore.edit { it[NFC_BLOCK_ENABLED] = enabled }
    }

    private companion object {
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val LOCK_ENABLED = booleanPreferencesKey("lock_enabled")
        val NFC_BLOCK_ENABLED = booleanPreferencesKey("nfc_block_enabled")
    }
}
