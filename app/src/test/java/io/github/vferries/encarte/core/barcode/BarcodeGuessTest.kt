package io.github.vferries.encarte.core.barcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarcodeGuessTest {
    @Test
    fun aValidEan13IsAnEan13() {
        assertEquals(BarcodeFormat.EAN_13, guessBarcodeFormat("4006381333931"))
    }

    @Test
    fun thirteenDigitsWithAWrongCheckDigitAreCode128() {
        assertEquals(BarcodeFormat.CODE_128, guessBarcodeFormat("4006381333932"))
    }

    @Test
    fun validEan8AndUpcAAreRecognised() {
        assertEquals(BarcodeFormat.EAN_8, guessBarcodeFormat("73513537"))
        assertEquals(BarcodeFormat.UPC_A, guessBarcodeFormat("036000291452"))
    }

    @Test
    fun lettersAndDigitsAreCode128() {
        assertEquals(BarcodeFormat.CODE_128, guessBarcodeFormat("FNAC-2049 77"))
    }

    @Test
    fun whatCode128CannotEncodeIsANumberOnly() {
        assertNull(guessBarcodeFormat("Carte-é"))
        assertNull(guessBarcodeFormat(""))
        assertNull(guessBarcodeFormat("9".repeat(81)))
    }
}
