package io.github.vferries.encarte.core.barcode

import io.github.vferries.encarte.core.barcode.BarcodeError.EMPTY
import io.github.vferries.encarte.core.barcode.BarcodeError.INVALID_CHARACTERS
import io.github.vferries.encarte.core.barcode.BarcodeError.INVALID_CHECK_DIGIT
import io.github.vferries.encarte.core.barcode.BarcodeError.INVALID_LENGTH
import io.github.vferries.encarte.core.barcode.BarcodeFormat.AZTEC
import io.github.vferries.encarte.core.barcode.BarcodeFormat.CODABAR
import io.github.vferries.encarte.core.barcode.BarcodeFormat.CODE_128
import io.github.vferries.encarte.core.barcode.BarcodeFormat.CODE_39
import io.github.vferries.encarte.core.barcode.BarcodeFormat.CODE_93
import io.github.vferries.encarte.core.barcode.BarcodeFormat.EAN_13
import io.github.vferries.encarte.core.barcode.BarcodeFormat.EAN_8
import io.github.vferries.encarte.core.barcode.BarcodeFormat.ITF
import io.github.vferries.encarte.core.barcode.BarcodeFormat.QR_CODE
import io.github.vferries.encarte.core.barcode.BarcodeFormat.UPC_A
import io.github.vferries.encarte.core.barcode.BarcodeFormat.UPC_E
import io.github.vferries.encarte.core.barcode.BarcodeValidator.validate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeValidatorTest {
    @Test
    fun emptyIsRejectedForEveryFormat() {
        BarcodeFormat.entries.forEach { assertEquals(it.name, EMPTY, validate("", it)) }
    }

    @Test
    fun ean13() {
        assertNull(validate("4006381333931", EAN_13))
        assertNull(validate("400638133393", EAN_13))
        assertEquals(INVALID_CHECK_DIGIT, validate("4006381333932", EAN_13))
        assertEquals(INVALID_CHARACTERS, validate("40063813339A", EAN_13))
        assertEquals(INVALID_LENGTH, validate("40063813", EAN_13))
    }

    @Test
    fun ean8() {
        assertNull(validate("73513537", EAN_8))
        assertNull(validate("7351353", EAN_8))
        assertEquals(INVALID_CHECK_DIGIT, validate("73513538", EAN_8))
    }

    @Test
    fun upcA() {
        assertNull(validate("036000291452", UPC_A))
        assertNull(validate("03600029145", UPC_A))
        assertEquals(INVALID_CHECK_DIGIT, validate("036000291453", UPC_A))
        assertEquals(INVALID_LENGTH, validate("0036000291452", UPC_A))
    }

    @Test
    fun upcE() {
        assertNull(validate("01234565", UPC_E))
        assertNull(validate("0123456", UPC_E))
        assertEquals(INVALID_CHECK_DIGIT, validate("01234566", UPC_E))
        assertEquals(INVALID_CHARACTERS, validate("21234565", UPC_E))
        assertEquals(INVALID_LENGTH, validate("012345", UPC_E))
    }

    @Test
    fun itf() {
        assertNull(validate("12345678", ITF))
        assertEquals(INVALID_LENGTH, validate("1234567", ITF))
        assertEquals(INVALID_CHARACTERS, validate("12A4", ITF))
    }

    @Test
    fun code128() {
        assertNull(validate("Loyalty#42-x", CODE_128))
        assertEquals(INVALID_CHARACTERS, validate("Carte é", CODE_128))
        assertEquals(INVALID_LENGTH, validate("A".repeat(81), CODE_128))
    }

    @Test
    fun code39AndCode93AcceptFullAsciiWithinEncodedLength() {
        for (format in listOf(CODE_39, CODE_93)) {
            assertNull(validate("CARD 123-ABC", format))
            assertNull(validate("card123", format))
            assertEquals(INVALID_CHARACTERS, validate("é", format))
            // Lowercase letters take two symbols each in extended mode: 41 of them exceed 80.
            assertEquals(INVALID_LENGTH, validate("a".repeat(41), format))
        }
    }

    @Test
    fun codabar() {
        assertNull(validate("A123456B", CODABAR))
        assertNull(validate("a123456b", CODABAR))
        assertNull(validate("123456", CODABAR))
        assertNull(validate("T12-34N", CODABAR))
        assertEquals(INVALID_CHARACTERS, validate("A123T", CODABAR))
        assertEquals(INVALID_CHARACTERS, validate("12a4", CODABAR))
    }

    @Test
    fun twoDimensionalFormatsAcceptAnyTextUpToLimit() {
        assertNull(validate("Käseschnitte €", QR_CODE))
        assertNull(validate("x".repeat(300), AZTEC))
        assertEquals(INVALID_LENGTH, validate("x".repeat(301), QR_CODE))
    }

    @Test
    fun formatNamesMatchZxingAndUtf8OnlyForNonAscii2d() {
        assertEquals(QR_CODE, BarcodeFormat.fromName("QR_CODE"))
        assertNull(BarcodeFormat.fromName("RSS_14"))
        assertTrue(QR_CODE.needsUtf8("Käse"))
        assertFalse(QR_CODE.needsUtf8("Kase"))
        assertFalse(CODE_128.needsUtf8("Käse"))
    }
}
