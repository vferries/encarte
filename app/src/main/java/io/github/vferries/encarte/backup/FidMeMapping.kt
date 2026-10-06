package io.github.vferries.encarte.backup

import io.github.vferries.encarte.brands.BrandCatalog
import io.github.vferries.encarte.core.barcode.guessBarcodeFormat
import io.github.vferries.encarte.core.color.CardPalette
import io.github.vferries.encarte.core.data.Card
import io.github.vferries.encarte.core.text.normalizedForMatching
import java.time.Instant

object FidMeMapping {
    /** Null for a row without a number (FidMe blanks the number of expired cards) or without a store name. */
    fun toCard(row: FidMeRow, brands: BrandCatalog, now: Instant): Card? {
        if (row.retailer.isEmpty() || row.reference.isEmpty()) return null
        return Card(
            storeName = row.retailer,
            cardNumber = row.reference,
            // FidMe exports no barcode type: the import dialog tells the user to check the guess.
            barcodeFormat = guessBarcodeFormat(row.reference),
            note = note(row),
            color = brands.match(row.retailer)?.argb ?: CardPalette.defaultFor(row.retailer),
            createdAt = now,
        )
    }

    private fun note(row: FidMeRow): String {
        // FidMe often repeats the store as the program name: only a different one is worth a line.
        val program = row.program.takeIf {
            it.isNotEmpty() && it.normalizedForMatching() != row.retailer.normalizedForMatching()
        }
        val holder = listOf(row.firstName, row.lastName).filter { it.isNotEmpty() }.joinToString(" ")
        return listOfNotNull(program, holder.takeIf { it.isNotEmpty() }).joinToString("\n")
    }
}
