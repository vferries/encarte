package io.github.vferries.encarte.backup

import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.core.color.CardPalette
import io.github.vferries.encarte.testing.testCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

class CatimaMappingTest {
    private val labels = ImportLabels("Valid from: %1\$s", "Expires: %1\$s", "Balance: %1\$s", "%1\$s points", Locale.US)
    private val now = Instant.parse("2026-10-04T12:00:00Z")

    private fun map(source: CatimaCard) = CatimaMapping.toCard(source, labels, ZoneOffset.UTC, now)

    @Test
    fun mapsCoreFields() {
        val card = map(
            CatimaCard(
                id = 4, store = " Pharmacy ", note = "Note", cardId = "123", barcodeId = "456", barcodeType = "QR_CODE",
                headerColor = -10902850, starred = true, lastUsed = 1766674633,
            )
        )

        assertEquals("Pharmacy", card.storeName)
        assertEquals("123", card.cardNumber)
        assertEquals("456", card.barcodeValue)
        assertEquals(BarcodeFormat.QR_CODE, card.barcodeFormat)
        assertEquals("Note", card.note)
        assertEquals(-10902850, card.color)
        assertTrue(card.isFavorite)
        assertEquals(Instant.ofEpochSecond(1766674633), card.lastUsedAt)
        assertEquals(now, card.createdAt)
    }

    @Test
    fun barcodeIdEqualToCardIdIsDropped() {
        assertNull(map(CatimaCard(id = 1, store = "S", cardId = "42", barcodeId = "42")).barcodeValue)
    }

    @Test
    fun unknownBarcodeTypeMeansNoBarcode() {
        assertNull(map(CatimaCard(id = 1, store = "S", cardId = "42", barcodeType = "MAXICODE")).barcodeFormat)
    }

    @Test
    fun headerColorIsForcedOpaque() {
        assertEquals(0xFF000001.toInt(), map(CatimaCard(id = 1, store = "S", cardId = "42", headerColor = 1)).color)
    }

    @Test
    fun missingHeaderColorFallsBackToPalette() {
        assertEquals(CardPalette.defaultFor("Shop"), map(CatimaCard(id = 1, store = "Shop", cardId = "42")).color)
    }

    @Test
    fun neverUsedHasNoLastUsed() {
        assertNull(map(CatimaCard(id = 1, store = "S", cardId = "42", lastUsed = 0)).lastUsedAt)
    }

    @Test
    fun catimaOnlyFieldsAreAppendedToNote() {
        val card = map(
            CatimaCard(
                id = 1, store = "S", note = "Gold", cardId = "42",
                validFrom = Instant.parse("2025-01-15T10:00:00Z").toEpochMilli(),
                expiry = Instant.parse("2026-12-31T10:00:00Z").toEpochMilli(),
                balance = BigDecimal("12.5"), balanceType = "EUR",
            )
        )

        assertEquals("Gold\nValid from: Jan 15, 2025\nExpires: Dec 31, 2026\nBalance: 12.50 EUR", card.note)
    }

    @Test
    fun pointsBalanceUsesPointsLabel() {
        val card = map(CatimaCard(id = 1, store = "S", cardId = "42", balance = BigDecimal("150")))

        assertEquals("Balance: 150 points", card.note)
    }

    @Test
    fun blankStoreIsRejected() {
        assertThrows(CatimaFormatException::class.java) { map(CatimaCard(id = 1, store = " ", cardId = "42")) }
    }

    @Test
    fun exportMapsBackAndFlagsUtf8For2dNonAscii() {
        val ascii = CatimaMapping.toCatima(testCard("Shop", cardNumber = "42", barcodeFormat = BarcodeFormat.QR_CODE, id = 3))
        assertEquals(3, ascii.id)
        assertEquals("42", ascii.cardId)
        assertEquals("QR_CODE", ascii.barcodeType)
        assertNull(ascii.barcodeEncoding)
        assertEquals(0L, ascii.lastUsed)

        val utf8 = CatimaMapping.toCatima(testCard("Käse", cardNumber = "Käseschnitte", barcodeFormat = BarcodeFormat.AZTEC))
        assertEquals("UTF-8", utf8.barcodeEncoding)
    }

    @Test
    fun duplicateKeyIgnoresCaseAccentsAndSurroundingSpaces() {
        assertEquals(
            testCard("Écomarché", cardNumber = "42").duplicateKey(),
            testCard("ecomarche", cardNumber = " 42 ").duplicateKey(),
        )
    }
}
