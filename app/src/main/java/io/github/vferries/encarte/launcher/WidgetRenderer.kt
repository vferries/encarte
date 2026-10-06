package io.github.vferries.encarte.launcher

/** The home screen's widgets, behind an interface so LauncherSync can be tested without a launcher. */
interface WidgetRenderer {
    fun widgetIds(): IntArray

    fun render(appWidgetId: Int, content: WidgetContent)
}
