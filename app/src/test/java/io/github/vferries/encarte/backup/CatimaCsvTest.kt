package io.github.vferries.encarte.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CatimaCsvTest {
    private fun fixture(name: String) = javaClass.getResource("/catima/$name")!!.readText()

    private fun List<CatimaCard>.byStore(store: String) = single { it.store == store }

    @Test
    fun readsCatimaV2Fixture() {
        val cards = CatimaCsv.read(fixture("catima_v2.csv"))

        assertEquals(8, cards.size)
        assertEquals("Multiline note about grocery store\n\nwith blank line", cards.byStore("Grocery Store").note)
        with(cards.byStore("Shoe Store")) {
            assertEquals(BigDecimal("12.50"), balance)
            assertEquals("EUR", balanceType)
            assertNull(headerColor)
            assertEquals("-5317", barcodeId)
        }
        with(cards.byStore("cheese")) {
            assertEquals("Käseschnitte", cardId)
            assertEquals("AZTEC", barcodeType)
            assertEquals("UTF-8", barcodeEncoding)
        }
        with(cards.byStore("Pharmacy")) {
            assertTrue(starred)
            assertEquals(1766674633L, lastUsed)
            assertEquals(-10902850, headerColor)
            assertNull(barcodeType)
        }
        with(cards.byStore("Card 1")) {
            assertEquals(1601510400L, validFrom)
            assertEquals(1618053234L, expiry)
            assertEquals("5432", barcodeId)
            assertEquals("QR_CODE", barcodeType)
        }
        assertEquals(0L, cards.byStore("Department Store").lastUsed)
    }

    @Test
    fun readsV1Variants() {
        assertTrue(CatimaCsv.read(fixture("catima_v1_starred_field.csv")).single().starred)
        assertNull(CatimaCsv.read(fixture("catima_v1_no_colors.csv")).single().headerColor)
        assertNull(CatimaCsv.read(fixture("catima_v1_empty_colors.csv")).single().headerColor)
        assertFalse(CatimaCsv.read(fixture("catima_v1_invalid_starred_field.csv")).single().starred)
        assertFalse(CatimaCsv.read(fixture("catima_v1_invalid_starred_field_2.csv")).single().starred)
        assertNull(CatimaCsv.read(fixture("catima_v1_no_barcode_type.csv")).single().barcodeType)
        with(CatimaCsv.read(fixture("catima_v1_invalid_colors.csv")).single()) {
            assertNull(headerColor)
            assertEquals("type", barcodeType)
            assertEquals("12345", cardId)
        }
    }

    @Test
    fun rejectsNewerVersion() {
        val error = assertThrows(UnsupportedCatimaVersionException::class.java) {
            CatimaCsv.read("3\r\n\r\n_id\r\n")
        }
        assertEquals(3, error.version)
    }

    @Test
    fun rejectsRowWithoutCardId() {
        assertThrows(CatimaFormatException::class.java) { CatimaCsv.read("_id,store,cardid\n1,Shop,\n") }
    }

    @Test
    fun rejectsCsvThatIsNotACatimaExport() {
        assertThrows(CatimaFormatException::class.java) { CatimaCsv.read("name,phone\nBob,123\n") }
        assertThrows(CatimaFormatException::class.java) { CatimaCsv.read("\u0000\u0001\u0002") }
    }

    @Test
    fun rejectsMalformedCsv() {
        assertThrows(CatimaFormatException::class.java) { CatimaCsv.read("_id,store,cardid\n1,\"unterminated") }
    }

    @Test
    fun writeUsesCatimaLayout() {
        val text = CatimaCsv.write(listOf(CatimaCard(id = 1, store = "Shop", cardId = "42")))

        assertTrue(text.startsWith("2\r\n\r\n_id\r\n\r\n_id,store,note,validfrom,expiry,balance,"))
        assertTrue(text.endsWith("\r\n\r\ncardId,groupId\r\n"))
    }

    @Test
    fun writeThenReadRoundTrips() {
        val cards = listOf(
            CatimaCard(
                id = 1, store = "Grocery", note = "line 1\n\nline 3", cardId = "42", barcodeId = "X42",
                barcodeType = "CODE_128", headerColor = -10902850, starred = true, lastUsed = 1766674633,
            ),
            CatimaCard(id = 2, store = "Käse", cardId = "Käseschnitte", barcodeType = "QR_CODE", barcodeEncoding = "UTF-8"),
        )

        assertEquals(cards, CatimaCsv.read(CatimaCsv.write(cards)))
    }
}
