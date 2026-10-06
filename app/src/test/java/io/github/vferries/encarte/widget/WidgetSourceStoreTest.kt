package io.github.vferries.encarte.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.vferries.encarte.launcher.WidgetSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WidgetSourceStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "widgets.preferences_pb") }

    @Test
    fun sourcesAreKeptPerWidget() = runTest {
        val store = WidgetSourceStore(dataStore())

        store.set(1, WidgetSource.Favorites)
        store.set(2, WidgetSource.Group(5))

        assertEquals(mapOf(1 to WidgetSource.Favorites, 2 to WidgetSource.Group(5)), store.sources.first())
    }

    @Test
    fun removedWidgetsLoseTheirSource() = runTest {
        val store = WidgetSourceStore(dataStore())
        store.set(1, WidgetSource.Favorites)
        store.set(2, WidgetSource.Group(5))

        store.remove(intArrayOf(1))

        assertEquals(mapOf(2 to WidgetSource.Group(5)), store.sources.first())
    }

    @Test
    fun restoredWidgetsKeepTheirSourceUnderTheirNewIds() = runTest {
        val store = WidgetSourceStore(dataStore())
        store.set(1, WidgetSource.Group(5))
        store.set(2, WidgetSource.Favorites)

        store.move(intArrayOf(1, 2), intArrayOf(11, 12))

        assertEquals(mapOf(11 to WidgetSource.Group(5), 12 to WidgetSource.Favorites), store.sources.first())
    }

    @Test
    fun unreadableEntriesAreIgnored() = runTest {
        val dataStore = dataStore()
        dataStore.edit {
            it[stringPreferencesKey("3")] = "bogus"
            it[stringPreferencesKey("4")] = "group:x"
            it[stringPreferencesKey("not-an-id")] = "favorites"
        }

        assertEquals(emptyMap<Int, WidgetSource>(), WidgetSourceStore(dataStore).sources.first())
    }
}
