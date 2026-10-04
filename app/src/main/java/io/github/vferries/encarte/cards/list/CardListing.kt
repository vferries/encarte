package io.github.vferries.encarte.cards.list

import io.github.vferries.encarte.core.data.Card
import io.github.vferries.encarte.core.prefs.SortOrder
import io.github.vferries.encarte.core.text.normalizedForMatching
import java.text.Collator
import java.util.Locale

data class CardSections(val favorites: List<Card>, val others: List<Card>)

/** Primary strength: "Écomarché", "ecomarche" and "ECOMARCHE" sort together. */
fun cardCollator(locale: Locale = Locale.getDefault()): Collator =
    Collator.getInstance(locale).apply { strength = Collator.PRIMARY }

fun List<Card>.toSections(query: String, order: SortOrder, collator: Collator): CardSections {
    val normalizedQuery = query.normalizedForMatching()
    val matching = if (normalizedQuery.isEmpty()) this else filter { card ->
        card.storeName.normalizedForMatching().contains(normalizedQuery) ||
            card.cardNumber.normalizedForMatching().contains(normalizedQuery)
    }
    val byName = compareBy(collator) { card: Card -> card.storeName }
    val comparator = when (order) {
        SortOrder.NAME -> byName
        // compareValues treats null as smallest, so descending order puts never-used cards last.
        SortOrder.RECENTLY_USED -> compareByDescending<Card> { it.lastUsedAt }.then(byName)
    }
    val sorted = matching.sortedWith(comparator)
    return CardSections(favorites = sorted.filter { it.isFavorite }, others = sorted.filterNot { it.isFavorite })
}
