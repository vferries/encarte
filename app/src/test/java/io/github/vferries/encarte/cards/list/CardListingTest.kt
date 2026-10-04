package io.github.vferries.encarte.cards.list

import io.github.vferries.encarte.core.prefs.SortOrder
import io.github.vferries.encarte.testing.testCard
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.Locale

class CardListingTest {
    private val collator = cardCollator(Locale.FRANCE)

    private val cards = listOf(
        testCard("Zara", cardNumber = "111", lastUsedAt = Instant.ofEpochSecond(10)),
        testCard("Écomarché", cardNumber = "222"),
        testCard("Fnac", cardNumber = "333", isFavorite = true, lastUsedAt = Instant.ofEpochSecond(5)),
        testCard("auchan", cardNumber = "4444 5555", lastUsedAt = Instant.ofEpochSecond(20)),
    )

    @Test
    fun sortsByNameIgnoringCaseAndAccentsWithFavoritesSeparated() {
        val sections = cards.toSections("", SortOrder.NAME, collator)

        assertEquals(listOf("Fnac"), sections.favorites.map { it.storeName })
        assertEquals(listOf("auchan", "Écomarché", "Zara"), sections.others.map { it.storeName })
    }

    @Test
    fun recentlyUsedPutsNeverUsedLast() {
        val sections = cards.toSections("", SortOrder.RECENTLY_USED, collator)

        assertEquals(listOf("auchan", "Zara", "Écomarché"), sections.others.map { it.storeName })
    }

    @Test
    fun searchMatchesNameWithoutAccentsAndNumberWithoutSpaces() {
        assertEquals(listOf("Écomarché"), cards.toSections("ecom", SortOrder.NAME, collator).others.map { it.storeName })
        assertEquals(listOf("auchan"), cards.toSections("45555", SortOrder.NAME, collator).others.map { it.storeName })
        assertEquals(CardSections(emptyList(), emptyList()), cards.toSections("nothing", SortOrder.NAME, collator))
    }
}
