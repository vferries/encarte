package io.github.vferries.encarte.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.EncarteApp
import io.github.vferries.encarte.launcher.WidgetSource
import io.github.vferries.encarte.testing.eventually
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class CardsWidgetProviderTest {
    private val app = ApplicationProvider.getApplicationContext<EncarteApp>()
    private val sources = app.container.widgetSources

    private fun broadcast(action: String, configure: Intent.() -> Unit) {
        app.sendBroadcast(Intent(action).setClass(app, CardsWidgetProvider::class.java).apply(configure))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun currentSources() = runBlocking { sources.sources.first() }

    @Test
    fun aRemovedWidgetForgetsItsSource() = runTest {
        sources.set(7, WidgetSource.Favorites)
        sources.set(8, WidgetSource.Group(3))

        // The system sends one id per DELETED broadcast, and AppWidgetProvider only reads that singular extra.
        broadcast(AppWidgetManager.ACTION_APPWIDGET_DELETED) { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 7) }

        eventually { currentSources() == mapOf(8 to WidgetSource.Group(3)) }
    }

    @Test
    fun aRestoredWidgetKeepsItsSourceUnderItsNewId() = runTest {
        sources.set(7, WidgetSource.Group(3))

        broadcast(AppWidgetManager.ACTION_APPWIDGET_RESTORED) {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_OLD_IDS, intArrayOf(7))
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(12))
        }

        eventually { currentSources() == mapOf(12 to WidgetSource.Group(3)) }
    }
}
