package io.github.vferries.encarte.backup

import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.core.barcode.BarcodeValidator
import io.github.vferries.encarte.core.color.CardPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The store screenshots show branding/demo/cards.csv: it must import cleanly and every code must display. */
class DemoCardsTest {
    private val cards = CatimaCsv.read(
        File(
            checkNotNull(System.getProperty("encarte.demoCards")) {
                "run through Gradle: encarte.demoCards is set in app/build.gradle.kts"
            },
        ).readText(),
    ).cards

    @Test
    fun holdsTheEightFictionalStores() {
        assertEquals(
            listOf(
                "Boulangerie Martin", "Librairie du Coin", "Fromagerie Dupont", "Cinéma Lumière",
                "Jardinerie des Lilas", "Pharmacie du Centre", "Atelier Vélo", "Café des Arts",
            ),
            cards.map { it.store },
        )
    }

    @Test
    fun onlyTheFirstTwoAreStarred() {
        assertEquals(setOf("Boulangerie Martin", "Librairie du Coin"), cards.filter { it.starred }.map { it.store }.toSet())
    }

    @Test
    fun everyCodeIsValidForItsFormat() {
        val formats = cards.map { card ->
            val format = BarcodeFormat.fromName(card.barcodeType.orEmpty())
            assertNotNull("${card.store}: unknown barcode type ${card.barcodeType}", format)
            assertNull("${card.store}: ${card.cardId}", BarcodeValidator.validate(card.cardId, format!!))
            format
        }
        assertEquals(setOf(BarcodeFormat.EAN_13, BarcodeFormat.CODE_128, BarcodeFormat.QR_CODE), formats.toSet())
    }

    @Test
    fun headerColorsAreDistinctPaletteSwatches() {
        val colors = cards.map { it.headerColor }
        assertEquals(colors.size, colors.toSet().size)
        colors.forEach { assertTrue("$it is not a palette swatch", it in CardPalette.swatches) }
    }
}
