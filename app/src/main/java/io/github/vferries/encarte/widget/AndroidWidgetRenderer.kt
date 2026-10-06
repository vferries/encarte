package io.github.vferries.encarte.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import io.github.vferries.encarte.launcher.WidgetContent
import io.github.vferries.encarte.launcher.WidgetRenderer

class AndroidWidgetRenderer(private val context: Context) : WidgetRenderer {
    // Null on devices without app widget support (some Android Go builds): then there is nothing to draw.
    private val manager: AppWidgetManager? = AppWidgetManager.getInstance(context)

    override fun widgetIds(): IntArray =
        manager?.getAppWidgetIds(ComponentName(context, CardsWidgetProvider::class.java)) ?: IntArray(0)

    override fun render(appWidgetId: Int, content: WidgetContent) {
        manager?.updateAppWidget(appWidgetId, WidgetViews.build(context, appWidgetId, content))
    }
}
