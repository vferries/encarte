package io.github.vferries.encarte.launcher

import io.github.vferries.encarte.cards.list.cardCollator
import io.github.vferries.encarte.core.data.CardGroup
import io.github.vferries.encarte.core.prefs.SortOrder
import io.github.vferries.encarte.testing.testCard
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.Locale

class LauncherContentTest {
    private val collator = cardCollator(Locale.FRANCE)
    private val courses = CardGroup(id = 10, name = "Courses")

    private fun names(content: WidgetContent) = (content as WidgetContent.Shown).cards.map { it.storeName }

    @Test
    fun shortcutsRankFavoritesThenRecentlyUsedThenName() {
        val cards = listOf(
            testCard("Zara", id = 1),
            testCard("Auchan", id = 2),
            testCard("Fnac", id = 3, lastUsedAt = Instant.ofEpochSecond(10)),
            testCard("Darty", id = 4, lastUsedAt = Instant.ofEpochSecond(20)),
            testCard("Ikea", id = 5, isFavorite = true),
        )

        assertEquals(listOf("Ikea", "Darty", "Fnac", "Auchan", "Zara"), shortcutCards(cards, collator, limit = 10).map { it.storeName })
        assertEquals(listOf("Ikea", "Darty", "Fnac"), shortcutCards(cards, collator, limit = 3).map { it.storeName })
    }

    @Test
    fun archivedCardsNeverBecomeShortcuts() {
        val cards = listOf(testCard("Darty", id = 1, isFavorite = true, isArchived = true), testCard("Fnac", id = 2))

        assertEquals(listOf(LauncherCard(2, "Fnac", 0xFF1976D2.toInt())), shortcutCards(cards, collator, limit = 3))
    }

    @Test
    fun aLauncherWithoutRoomGetsNoShortcut() {
        assertEquals(emptyList<LauncherCard>(), shortcutCards(listOf(testCard("Fnac")), collator, limit = -1))
    }

    @Test
    fun aLockedWidgetShowsNothing() {
        val content = widgetContent(
            WidgetSource.Favorites, listOf(testCard("Fnac", isFavorite = true)), emptyList(), emptyMap(),
            SortOrder.NAME, collator, locked = true,
        )

        assertEquals(WidgetContent.Locked, content)
    }

    @Test
    fun theFavoritesWidgetFollowsTheListOrderWithoutArchivedCards() {
        val cards = listOf(
            testCard("Zara", id = 1, isFavorite = true, lastUsedAt = Instant.ofEpochSecond(30)),
            testCard("Auchan", id = 2, isFavorite = true),
            testCard("Darty", id = 3, isFavorite = true, isArchived = true),
            testCard("Fnac", id = 4),
        )

        val content = widgetContent(WidgetSource.Favorites, cards, emptyList(), emptyMap(), SortOrder.RECENTLY_USED, collator, locked = false)

        assertEquals(WidgetTitle.Favorites, (content as WidgetContent.Shown).title)
        assertEquals(listOf("Zara", "Auchan"), names(content))
    }

    @Test
    fun aGroupWidgetShowsItsFavoritesFirst() {
        val cards = listOf(testCard("Auchan", id = 1), testCard("Zara", id = 2, isFavorite = true), testCard("Fnac", id = 3))
        val memberships = mapOf(1L to setOf(10L), 2L to setOf(10L, 11L))

        val content = widgetContent(WidgetSource.Group(10), cards, listOf(courses), memberships, SortOrder.NAME, collator, locked = false)

        assertEquals(WidgetTitle.Group("Courses"), (content as WidgetContent.Shown).title)
        assertEquals(listOf("Zara", "Auchan"), names(content))
    }

    @Test
    fun noSourceOrADeletedGroupFallsBackToTheFavorites() {
        val cards = listOf(testCard("Fnac", id = 1, isFavorite = true), testCard("Zara", id = 2))

        for (source in listOf(null, WidgetSource.Group(99))) {
            val content = widgetContent(source, cards, listOf(courses), emptyMap(), SortOrder.NAME, collator, locked = false)
            assertEquals(WidgetTitle.Favorites, (content as WidgetContent.Shown).title)
            assertEquals(listOf("Fnac"), names(content))
        }
    }

    @Test
    fun aGroupWithOnlyArchivedCardsIsEmpty() {
        val cards = listOf(testCard("Darty", id = 1, isArchived = true))

        val content = widgetContent(WidgetSource.Group(10), cards, listOf(courses), mapOf(1L to setOf(10L)), SortOrder.NAME, collator, locked = false)

        assertEquals(WidgetContent.Shown(WidgetTitle.Group("Courses"), emptyList()), content)
    }
}
