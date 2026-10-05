package io.github.vferries.encarte.cards.list

import io.github.vferries.encarte.core.prefs.SortOrder
import io.github.vferries.encarte.testing.testCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
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
        assertEquals(CardSections(emptyList(), emptyList(), emptyList()), cards.toSections("nothing", SortOrder.NAME, collator))
    }

    @Test
    fun expirySortPutsDatedCardsFirstSoonestFirstThenUndatedByName() {
        val dated = listOf(
            testCard("Zara", cardNumber = "1", expiresOn = LocalDate.of(2027, 1, 1)),
            testCard("Fnac", cardNumber = "2"),
            testCard("Auchan", cardNumber = "3", expiresOn = LocalDate.of(2026, 1, 1)),
            testCard("Boulanger", cardNumber = "4"),
            testCard("Cora", cardNumber = "5", expiresOn = LocalDate.of(2027, 1, 1)),
        )

        val sections = dated.toSections("", SortOrder.EXPIRY, collator)

        assertEquals(listOf("Auchan", "Cora", "Zara", "Boulanger", "Fnac"), sections.others.map { it.storeName })
    }

    @Test
    fun archivedCardsOnlyAppearInTheArchivedSectionEvenFavorites() {
        val withArchived = cards +
            testCard("Darty", cardNumber = "666", isFavorite = true, isArchived = true) +
            testCard("Boulanger", cardNumber = "777", isArchived = true)

        val sections = withArchived.toSections("", SortOrder.NAME, collator)

        assertEquals(listOf("Fnac"), sections.favorites.map { it.storeName })
        assertEquals(listOf("auchan", "Écomarché", "Zara"), sections.others.map { it.storeName })
        assertEquals(listOf("Boulanger", "Darty"), sections.archived.map { it.storeName })
    }

    @Test
    fun searchAlsoFindsArchivedCards() {
        val withArchived = cards + testCard("Darty", cardNumber = "666", isArchived = true)

        val sections = withArchived.toSections("dar", SortOrder.NAME, collator)

        assertTrue(sections.favorites.isEmpty() && sections.others.isEmpty())
        assertEquals(listOf("Darty"), sections.archived.map { it.storeName })
    }
}
