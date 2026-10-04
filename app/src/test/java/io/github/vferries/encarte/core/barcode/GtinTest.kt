package io.github.vferries.encarte.core.barcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GtinTest {
    @Test
    fun computesCheckDigits() {
        assertEquals(1, Gtin.checkDigit("400638133393"))
        assertEquals(7, Gtin.checkDigit("7351353"))
        assertEquals(2, Gtin.checkDigit("03600029145"))
    }

    @Test
    fun validatesCheckDigits() {
        assertTrue(Gtin.hasValidCheckDigit("4006381333931"))
        assertFalse(Gtin.hasValidCheckDigit("4006381333932"))
    }

    @Test
    fun expandsUpcE() {
        assertEquals("01234500006", Gtin.expandUpcE("0123456"))
        assertEquals("01200000345", Gtin.expandUpcE("0123450"))
        assertEquals("01230000045", Gtin.expandUpcE("0123453"))
        assertEquals("01234000005", Gtin.expandUpcE("0123454"))
    }

    @Test
    fun compressesUpcABackToUpcE() {
        assertEquals("01234565", Gtin.compressToUpcE("012345000065"))
    }

    @Test
    fun compressionRoundTripsThroughExpansion() {
        // Several UPC-E spellings can encode the same UPC-A; any one is fine as long as it expands back.
        for (upcE7 in listOf("0123456", "0123450", "1631706", "0987653", "0555554")) {
            val upcA11 = Gtin.expandUpcE(upcE7)
            val upcA = upcA11 + Gtin.checkDigit(upcA11)
            val compressed = Gtin.compressToUpcE(upcA)!!
            assertEquals(upcA11, Gtin.expandUpcE(compressed.take(7)))
            assertEquals(upcA.last(), compressed.last())
        }
    }

    @Test
    fun nonCompressibleUpcAReturnsNull() {
        assertNull(Gtin.compressToUpcE("036000291452"))
        assertNull(Gtin.compressToUpcE("212345000065"))
    }
}
