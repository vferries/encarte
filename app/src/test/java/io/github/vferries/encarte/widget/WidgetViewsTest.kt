package io.github.vferries.encarte.widget

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.R
import io.github.vferries.encarte.core.color.CardPalette
import io.github.vferries.encarte.launcher.LauncherCard
import io.github.vferries.encarte.launcher.WidgetContent
import io.github.vferries.encarte.launcher.WidgetTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetViewsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun applied(content: WidgetContent): View = WidgetViews.build(context, 7, content).apply(context, FrameLayout(context))

    private fun View.text(id: Int) = findViewById<TextView>(id).text.toString()

    @Test
    fun aLockedWidgetShowsOnlyTheAppName() {
        val view = applied(WidgetContent.Locked)

        assertEquals("Encarté", view.text(R.id.widget_locked_label))
        assertNull(view.findViewById<View>(R.id.widget_grid))
        assertNull(view.findViewById<View>(R.id.widget_title))
    }

    @Test
    fun theFavoritesHaveTheirTitleAndEmptyMessage() {
        val view = applied(WidgetContent.Shown(WidgetTitle.Favorites, emptyList()))

        assertEquals("★ Favorites", view.text(R.id.widget_title))
        assertEquals("No favorite cards", view.text(R.id.widget_empty))
    }

    @Test
    fun aGroupHasItsNameAndEmptyMessage() {
        val view = applied(WidgetContent.Shown(WidgetTitle.Group("Courses"), listOf(LauncherCard(1, "Fnac", 0xFF1976D2.toInt()))))

        assertEquals("Courses", view.text(R.id.widget_title))
        assertEquals("No cards in \"Courses\"", view.text(R.id.widget_empty))
    }

    @Test
    fun aTileShowsItsStoreInAReadableColor() {
        val yellow = WidgetViews.tile(context, LauncherCard(1, "Fnac", 0xFFF9A825.toInt())).apply(context, FrameLayout(context))
        val blue = WidgetViews.tile(context, LauncherCard(2, "Zara", 0xFF1976D2.toInt())).apply(context, FrameLayout(context))

        assertEquals("Fnac", yellow.findViewById<TextView>(R.id.widget_tile_name).text.toString())
        assertEquals(CardPalette.BLACK, yellow.findViewById<TextView>(R.id.widget_tile_name).currentTextColor)
        assertEquals(CardPalette.WHITE, blue.findViewById<TextView>(R.id.widget_tile_name).currentTextColor)
        assertEquals("Fnac", yellow.contentDescription)
    }
}
