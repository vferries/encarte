package io.github.vferries.encarte.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.PendingIntentCompat
import androidx.core.widget.RemoteViewsCompat
import io.github.vferries.encarte.R
import io.github.vferries.encarte.core.color.CardPalette
import io.github.vferries.encarte.launcher.LauncherCard
import io.github.vferries.encarte.launcher.WidgetContent
import io.github.vferries.encarte.launcher.WidgetTitle
import io.github.vferries.encarte.navigation.LaunchRequests

private const val REQUEST_OPEN_APP = 0
private const val REQUEST_OPEN_CARD = 1

/** The cards widget, drawn by the launcher in its own process: no photo, no number, nothing but names. */
object WidgetViews {
    fun build(context: Context, appWidgetId: Int, content: WidgetContent): RemoteViews = when (content) {
        WidgetContent.Locked -> RemoteViews(context.packageName, R.layout.widget_locked).apply {
            setOnClickPendingIntent(R.id.widget_locked, openApp(context))
        }
        is WidgetContent.Shown -> RemoteViews(context.packageName, R.layout.widget_cards).apply {
            setTextViewText(R.id.widget_title, title(context, content.title))
            setOnClickPendingIntent(R.id.widget_title, openApp(context))
            setTextViewText(R.id.widget_empty, emptyText(context, content.title))
            setOnClickPendingIntent(R.id.widget_empty, openApp(context))
            setEmptyView(R.id.widget_grid, R.id.widget_empty)
            setPendingIntentTemplate(R.id.widget_grid, openCardTemplate(context))
            RemoteViewsCompat.setRemoteAdapter(context, this, appWidgetId, R.id.widget_grid, tiles(context, content.cards))
        }
    }

    internal fun tile(context: Context, card: LauncherCard): RemoteViews {
        val background = CardPalette.opaque(card.color)
        return RemoteViews(context.packageName, R.layout.widget_tile).apply {
            setTextViewText(R.id.widget_tile_name, card.storeName)
            setTextColor(R.id.widget_tile_name, CardPalette.contentColorFor(background))
            setInt(R.id.widget_tile_background, "setColorFilter", background)
            setContentDescription(R.id.widget_tile, card.storeName)
            setOnClickFillInIntent(R.id.widget_tile, Intent().putExtra(LaunchRequests.EXTRA_CARD_ID, card.id))
        }
    }

    private fun tiles(context: Context, cards: List<LauncherCard>): RemoteViewsCompat.RemoteCollectionItems =
        RemoteViewsCompat.RemoteCollectionItems.Builder()
            .setHasStableIds(true)
            .apply { cards.forEach { addItem(it.id, tile(context, it)) } }
            .build()

    private fun title(context: Context, title: WidgetTitle): String = when (title) {
        WidgetTitle.Favorites -> context.getString(R.string.widget_favorites)
        is WidgetTitle.Group -> title.name
    }

    private fun emptyText(context: Context, title: WidgetTitle): String = when (title) {
        WidgetTitle.Favorites -> context.getString(R.string.widget_no_favorites)
        is WidgetTitle.Group -> context.getString(R.string.group_empty, title.name)
    }

    /** The list, as after a fresh launch. Only FLAG_NO_CREATE makes getActivity return null. */
    private fun openApp(context: Context): PendingIntent = checkNotNull(
        PendingIntentCompat.getActivity(
            context, REQUEST_OPEN_APP, LaunchRequests.launch(context, Intent.ACTION_MAIN),
            PendingIntent.FLAG_UPDATE_CURRENT, false,
        )
    )

    /** Mutable: each tile's fill-in intent adds its card id. The intent is explicit, so nothing else can fill it. */
    private fun openCardTemplate(context: Context): PendingIntent = checkNotNull(
        PendingIntentCompat.getActivity(
            context, REQUEST_OPEN_CARD, LaunchRequests.launch(context, LaunchRequests.ACTION_VIEW_CARD),
            PendingIntent.FLAG_UPDATE_CURRENT, true,
        )
    )
}
