package io.github.vferries.encarte.scan

import io.github.vferries.encarte.core.barcode.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import zxingcpp.BarcodeReader.Format

class ScanMappingTest {
    @Test
    fun directFormats() {
        assertEquals(ScannedCode("LOYALTY-1", BarcodeFormat.QR_CODE), ScanMapping.map(Format.QR_CODE, "LOYALTY-1"))
        assertEquals(BarcodeFormat.AZTEC, ScanMapping.map(Format.AZTEC_CODE, "A1").format)
        assertEquals(BarcodeFormat.CODE_39, ScanMapping.map(Format.CODE_39_EXT, "card1").format)
        assertEquals(BarcodeFormat.ITF, ScanMapping.map(Format.ITF_14, "12345678901231").format)
        assertEquals(BarcodeFormat.PDF_417, ScanMapping.map(Format.COMPACT_PDF_417, "X").format)
    }

    @Test
    fun upcAReportedAsEan13StaysEan13() {
        assertEquals(
            ScannedCode("0036000291452", BarcodeFormat.EAN_13),
            ScanMapping.map(Format.EAN_13, "0036000291452"),
        )
    }

    @Test
    fun upcAReportedAsUpcAKeepsTwelveDigits() {
        assertEquals(ScannedCode("036000291452", BarcodeFormat.UPC_A), ScanMapping.map(Format.UPC_A, "036000291452"))
    }

    @Test
    fun upcEExpandedTextIsCompressedBackToEightDigits() {
        assertEquals(ScannedCode("01234565", BarcodeFormat.UPC_E), ScanMapping.map(Format.UPC_E, "0012345000065"))
        assertEquals(ScannedCode("01234565", BarcodeFormat.UPC_E), ScanMapping.map(Format.UPC_E, "01234565"))
    }

    @Test
    fun eanUpcFamilyIsResolvedByLength() {
        assertEquals(BarcodeFormat.EAN_8, ScanMapping.map(Format.EAN_UPC, "73513537").format)
        assertEquals(BarcodeFormat.EAN_13, ScanMapping.map(Format.EAN_UPC, "4006381333931").format)
        assertEquals(BarcodeFormat.UPC_A, ScanMapping.map(Format.EAN_UPC, "036000291452").format)
    }

    @Test
    fun undrawableFormatsKeepTheValueWithoutFormat() {
        assertEquals(ScannedCode("0101234567890128", null), ScanMapping.map(Format.DATA_BAR, "0101234567890128"))
        assertEquals(null, ScanMapping.map(Format.MAXI_CODE, "x").format)
    }

    @Test
    fun valueZxingWouldRejectBecomesNumberOnly() {
        // GS1-128 content carries a group separator, which ZXing's Code 128 writer cannot take as-is.
        assertEquals(null, ScanMapping.map(Format.CODE_128, "\u001D0101234567890128").format)
    }
}
