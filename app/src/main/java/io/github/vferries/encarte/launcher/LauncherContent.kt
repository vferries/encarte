package io.github.vferries.encarte.launcher

import io.github.vferries.encarte.cards.list.toSections
import io.github.vferries.encarte.core.data.Card
import io.github.vferries.encarte.core.data.CardGroup
import io.github.vferries.encarte.core.prefs.SortOrder
import java.text.Collator

/** What the home screen shows of a card: never its number or barcode. */
data class LauncherCard(val id: Long, val storeName: String, val color: Int)

fun Card.toLauncherCard() = LauncherCard(id, storeName, color)

/** Favorites first, then the most recently used (never-used last), then by name. Archived cards never show. */
fun shortcutCards(cards: List<Card>, collator: Collator, limit: Int): List<LauncherCard> =
    cards.filterNot { it.isArchived }
        .sortedWith(
            compareByDescending<Card> { it.isFavorite }
                .thenByDescending { it.lastUsedAt }
                .then(compareBy(collator) { it.storeName })
        )
        .take(limit.coerceAtLeast(0))
        .map { it.toLauncherCard() }

sealed interface WidgetSource {
    data object Favorites : WidgetSource
    data class Group(val groupId: Long) : WidgetSource
}

sealed interface WidgetTitle {
    data object Favorites : WidgetTitle
    data class Group(val name: String) : WidgetTitle
}

sealed interface WidgetContent {
    /** The app lock is on: no card or group name may show on the home screen. */
    data object Locked : WidgetContent

    /** [cards] is empty for an empty source: the widget then says so. */
    data class Shown(val title: WidgetTitle, val cards: List<LauncherCard>) : WidgetContent
}

/** In the list's order. A widget without a source, or whose group was deleted, shows the favorites. */
fun widgetContent(
    source: WidgetSource?,
    cards: List<Card>,
    groups: List<CardGroup>,
    memberships: Map<Long, Set<Long>>,
    order: SortOrder,
    collator: Collator,
    locked: Boolean,
): WidgetContent {
    if (locked) return WidgetContent.Locked
    val group = (source as? WidgetSource.Group)?.let { wanted -> groups.firstOrNull { it.id == wanted.groupId } }
    if (group == null) {
        val favorites = cards.toSections("", order, collator).favorites
        return WidgetContent.Shown(WidgetTitle.Favorites, favorites.map { it.toLauncherCard() })
    }
    val members = memberships.filterValues { group.id in it }.keys
    val sections = cards.toSections("", order, collator, members)
    return WidgetContent.Shown(WidgetTitle.Group(group.name), (sections.favorites + sections.others).map { it.toLauncherCard() })
}
