package io.github.vferries.encarte.importing

import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.scan.ScannedCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PdfRenderer does not run on the JVM: PdfCodeFinder itself is tested in androidTest. */
class PdfCodesTest {
    private val qr = ScannedCode("LOYALTY-1", BarcodeFormat.QR_CODE)
    private val ean = ScannedCode("4006381333931", BarcodeFormat.EAN_13)

    @Test
    fun codesKeepTheirPageAndAppearOnceWhereFirstFound() {
        val pages = listOf(listOf(qr), emptyList(), listOf(ean, qr, ScannedCode("LOYALTY-1", null)))

        assertEquals(
            listOf(
                FoundCode("LOYALTY-1", BarcodeFormat.QR_CODE, page = 1),
                FoundCode("4006381333931", BarcodeFormat.EAN_13, page = 3),
                FoundCode("LOYALTY-1", null, page = 3),
            ),
            foundCodes(pages),
        )
    }

    @Test
    fun aValueLongerThanTheLargestDrawableCodeIsDropped() {
        val kept = ScannedCode("1".repeat(300), BarcodeFormat.QR_CODE)
        val dropped = ScannedCode("2".repeat(301), null)

        assertEquals(listOf(FoundCode(kept.value, BarcodeFormat.QR_CODE, page = 1)), foundCodes(listOf(listOf(dropped, kept))))
    }

    @Test
    fun aPageLongSideIsRenderedAt2400PixelsWithinOneToFourTimes() {
        assertEquals(2400f / 842, renderScale(595, 842), 0.001f)
        assertEquals(4f, renderScale(243, 153), 0.001f)
        assertEquals(1f, renderScale(2592, 3600), 0.001f)
    }

    @Test
    fun aPosterSizedPageIsDrawnSmallerToFitInMemory() {
        val scale = renderScale(14_400, 14_400)

        assertTrue(scale < 1f)
        assertTrue(14_400 * scale * 14_400 * scale <= 4_096.0 * 4_096.0 + 1)
    }
}
