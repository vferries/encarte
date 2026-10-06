package io.github.vferries.encarte.widget

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.vferries.encarte.core.prefs.dataOrDefaults
import io.github.vferries.encarte.launcher.WidgetSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val TAG = "WidgetSourceStore"
private const val FAVORITES = "favorites"
private const val GROUP_PREFIX = "group:"

/** Each widget's source, keyed by appWidgetId. A widget without an entry shows the favorites. */
class WidgetSourceStore(private val dataStore: DataStore<Preferences>) {

    val sources: Flow<Map<Int, WidgetSource>> = dataStore.dataOrDefaults(TAG, what = "the widget sources")
        .map { prefs ->
            prefs.asMap().entries.mapNotNull { (key, value) ->
                val appWidgetId = key.name.toIntOrNull()
                val source = (value as? String)?.let(::decode)
                if (appWidgetId == null || source == null) {
                    Log.w(TAG, "Ignoring widget source ${key.name}=$value")
                    null
                } else {
                    appWidgetId to source
                }
            }.toMap()
        }
        .distinctUntilChanged()

    suspend fun set(appWidgetId: Int, source: WidgetSource) {
        dataStore.edit { it[key(appWidgetId)] = encode(source) }
    }

    suspend fun remove(appWidgetIds: IntArray) {
        dataStore.edit { prefs -> appWidgetIds.forEach { prefs.remove(key(it)) } }
    }

    /** After a restore, the launcher gives the same widgets new ids. */
    suspend fun move(oldIds: IntArray, newIds: IntArray) {
        dataStore.edit { prefs ->
            val values = oldIds.map { prefs[key(it)] }
            oldIds.forEach { prefs.remove(key(it)) }
            newIds.zip(values).forEach { (appWidgetId, value) -> if (value != null) prefs[key(appWidgetId)] = value }
        }
    }

    private fun key(appWidgetId: Int) = stringPreferencesKey(appWidgetId.toString())

    private fun encode(source: WidgetSource): String = when (source) {
        WidgetSource.Favorites -> FAVORITES
        is WidgetSource.Group -> GROUP_PREFIX + source.groupId
    }

    private fun decode(value: String): WidgetSource? = when {
        value == FAVORITES -> WidgetSource.Favorites
        value.startsWith(GROUP_PREFIX) -> value.removePrefix(GROUP_PREFIX).toLongOrNull()?.let { WidgetSource.Group(it) }
        else -> null
    }
}
