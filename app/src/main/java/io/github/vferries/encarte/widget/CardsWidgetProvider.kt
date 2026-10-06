package io.github.vferries.encarte.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.Log
import io.github.vferries.encarte.AppContainer
import io.github.vferries.encarte.EncarteApp
import kotlinx.coroutines.launch
import java.io.IOException

private const val TAG = "CardsWidgetProvider"

class CardsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) =
        runAsync(context) { it.launcherSync.renderWidgets(appWidgetIds) }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) =
        runAsync(context) { it.widgetSources.remove(appWidgetIds) }

    // Restored from a backup or a device transfer: the launcher gave the same widgets new ids.
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) =
        runAsync(context) { it.widgetSources.move(oldWidgetIds, newWidgetIds) }

    /** The work outlives onReceive: goAsync keeps the process alive until it finishes. */
    private fun runAsync(context: Context, block: suspend (AppContainer) -> Unit) {
        val container = (context.applicationContext as EncarteApp).container
        // Null for the second callback of one broadcast (RESTORED calls onRestored, then onUpdate): the first holds it.
        val pending: PendingResult? = goAsync()
        container.appScope.launch {
            try {
                block(container)
            } catch (e: IOException) {
                Log.e(TAG, "Cannot update the widget sources", e)
            } finally {
                pending?.finish()
            }
        }
    }
}
