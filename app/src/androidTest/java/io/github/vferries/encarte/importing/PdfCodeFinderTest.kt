package io.github.vferries.encarte.importing

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** PdfRenderer and zxing-cpp only run on a device. */
@RunWith(AndroidJUnit4::class)
class PdfCodeFinderTest {
    private val dir = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "pdf-tests").apply { mkdirs() }
    private val finder = PdfCodeFinder()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun file(bytes: ByteArray) = File(dir, "test-${System.nanoTime()}.pdf").apply { writeBytes(bytes) }

    @Test
    fun findsTheCodeOfEachPageWithItsPage() {
        val pdf = TestPdfs.withCodes(
            listOf(BarcodeFormat.QR_CODE to "LOYALTY-QR-1"),
            listOf(BarcodeFormat.EAN_13 to "4006381333931"),
        )

        assertEquals(
            PdfScan.Codes(
                listOf(
                    FoundCode("LOYALTY-QR-1", BarcodeFormat.QR_CODE, page = 1),
                    FoundCode("4006381333931", BarcodeFormat.EAN_13, page = 2),
                )
            ),
            finder.find(file(pdf)),
        )
    }

    @Test
    fun aCodeRepeatedOnALaterPageIsFoundOnce() {
        val pdf = TestPdfs.withCodes(
            listOf(BarcodeFormat.QR_CODE to "LOYALTY-QR-1"),
            listOf(BarcodeFormat.QR_CODE to "LOYALTY-QR-1", BarcodeFormat.CODE_128 to "C-2049-77"),
        )

        val codes = (finder.find(file(pdf)) as PdfScan.Codes).codes

        assertEquals(
            setOf(FoundCode("LOYALTY-QR-1", BarcodeFormat.QR_CODE, 1), FoundCode("C-2049-77", BarcodeFormat.CODE_128, 2)),
            codes.toSet(),
        )
        assertEquals(2, codes.size)
    }

    @Test
    fun pagesAfterTheTenthAreNotRead() {
        val pages = List(10) { emptyList<Pair<BarcodeFormat, String>>() } + listOf(listOf(BarcodeFormat.QR_CODE to "PAGE-11"))

        assertEquals(PdfScan.Codes(emptyList()), finder.find(file(TestPdfs.withCodes(*pages.toTypedArray()))))
    }

    @Test
    fun aPasswordProtectedPdfIsUnreadable() {
        val pdf = file(TestPdfs.passwordProtected())
        // The fixture itself: Android refuses to open it without its password.
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            assertThrows(SecurityException::class.java) { PdfRenderer(descriptor).close() }
        }

        assertEquals(PdfScan.Unreadable, finder.find(pdf))
    }

    @Test
    fun aDamagedPdfIsUnreadable() {
        assertEquals(PdfScan.Unreadable, finder.find(file("%PDF-1.7\nnot really a PDF".toByteArray())))
    }
}
