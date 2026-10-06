package io.github.vferries.encarte.backup

import io.github.vferries.encarte.brands.BrandCatalog
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.core.color.CardPalette
import io.github.vferries.encarte.core.data.Card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class FidMeMappingTest {
    private val now = Instant.parse("2026-10-06T12:00:00Z")
    private val brands = BrandCatalog { """[{"name": "Fnac", "aliases": [], "color": "#E1A925"}]""" }

    private fun card(row: FidMeRow) = FidMeMapping.toCard(row, brands, now)

    @Test
    fun aRowBecomesACardWithAGuessedBarcodeType() {
        assertEquals(
            Card(
                storeName = "Fnac", cardNumber = "4006381333931", barcodeFormat = BarcodeFormat.EAN_13,
                note = "Carte Fnac+\nMarie Dupont", color = 0xFFE1A925.toInt(), createdAt = now,
            ),
            card(FidMeRow("Fnac", "4006381333931", program = "Carte Fnac+", firstName = "Marie", lastName = "Dupont")),
        )
    }

    @Test
    fun aProgramNamedLikeTheStoreIsDropped() {
        assertEquals("", card(FidMeRow("E.Leclerc", "C-1", program = "e leclerc"))!!.note)
        assertEquals("Marie", card(FidMeRow("Fnac", "C-1", program = "FNAC", firstName = "Marie"))!!.note)
    }

    @Test
    fun anUnknownStoreGetsItsPaletteColor() {
        assertEquals(CardPalette.defaultFor("Boulangerie"), card(FidMeRow("Boulangerie", "0042"))!!.color)
    }

    @Test
    fun aCardIsNeitherFavoriteNorArchivedAndHasNoExpiry() {
        val card = card(FidMeRow("Fnac", "C-1"))!!

        assertEquals(false, card.isFavorite)
        assertEquals(false, card.isArchived)
        assertNull(card.expiresOn)
        assertNull(card.lastUsedAt)
    }

    @Test
    fun aRowWithoutNumberOrStoreIsSkipped() {
        assertNull(card(FidMeRow("Décathlon", "")))
        assertNull(card(FidMeRow("", "42")))
    }
}
