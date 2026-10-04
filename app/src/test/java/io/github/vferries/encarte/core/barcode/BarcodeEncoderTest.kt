package io.github.vferries.encarte.core.barcode

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BarcodeEncoderTest {
    /** format, value to encode, text ZXing's reader returns. */
    private val samples = listOf(
        Triple(BarcodeFormat.QR_CODE, "LOYALTY-123456", "LOYALTY-123456"),
        Triple(BarcodeFormat.QR_CODE, "Käseschnitte €", "Käseschnitte €"),
        Triple(BarcodeFormat.AZTEC, "1234567890ABC", "1234567890ABC"),
        Triple(BarcodeFormat.AZTEC, "Käseschnitte €", "Käseschnitte €"),
        Triple(BarcodeFormat.DATA_MATRIX, "CARD12345", "CARD12345"),
        Triple(BarcodeFormat.DATA_MATRIX, "Käseschnitte €", "Käseschnitte €"),
        Triple(BarcodeFormat.PDF_417, "CARD-1234-5678", "CARD-1234-5678"),
        Triple(BarcodeFormat.PDF_417, "Käseschnitte €", "Käseschnitte €"),
        Triple(BarcodeFormat.EAN_13, "4006381333931", "4006381333931"),
        Triple(BarcodeFormat.EAN_13, "400638133393", "4006381333931"),
        Triple(BarcodeFormat.EAN_8, "73513537", "73513537"),
        Triple(BarcodeFormat.UPC_A, "036000291452", "036000291452"),
        Triple(BarcodeFormat.UPC_E, "01234565", "01234565"),
        Triple(BarcodeFormat.UPC_E, "0123456", "01234565"),
        Triple(BarcodeFormat.CODE_128, "Loyalty#42-x", "Loyalty#42-x"),
        Triple(BarcodeFormat.CODE_39, "CARD 123-ABC", "CARD 123-ABC"),
        Triple(BarcodeFormat.CODE_93, "card-93", "card-93"),
        // ZXing's reader strips Codabar start/stop characters.
        Triple(BarcodeFormat.CODABAR, "A123456B", "123456"),
        Triple(BarcodeFormat.CODABAR, "123456", "123456"),
        Triple(BarcodeFormat.ITF, "12345678", "12345678"),
    )

    @Test
    fun everySampleRoundTripsThroughZxingReaders() {
        for ((format, value, expected) in samples) {
            assertEquals("$format $value", expected, decode(BarcodeEncoder.encode(value, format), format))
        }
    }

    @Test
    fun everyFormatHasASample() {
        assertEquals(BarcodeFormat.entries.toSet(), samples.map { it.first }.toSet())
    }

    @Test
    fun oneDimensionalCodesAreOnePixelTall() {
        assertEquals(1, BarcodeEncoder.encode("4006381333931", BarcodeFormat.EAN_13).height)
    }

    @Test
    fun qrCodesAreSquare() {
        val matrix = BarcodeEncoder.encode("LOYALTY-123456", BarcodeFormat.QR_CODE)
        assertEquals(matrix.width, matrix.height)
    }

    @Test
    fun invalidContentThrowsTypedException() {
        assertThrows(BarcodeEncodingException::class.java) {
            BarcodeEncoder.encode("4006381333932", BarcodeFormat.EAN_13)
        }
        assertThrows(BarcodeEncodingException::class.java) {
            BarcodeEncoder.encode("x".repeat(5000), BarcodeFormat.QR_CODE)
        }
    }

    @Test
    fun mapsEveryFormatToZxing() {
        BarcodeFormat.entries.forEach { assertEquals(it.name, it.toZxing().name) }
    }

    private fun decode(matrix: BitMatrix, format: BarcodeFormat): String {
        val scale = 4
        val border = 40
        val twoDimensional = format.isTwoDimensional
        val height = if (twoDimensional) matrix.height * scale else 60
        val width = matrix.width * scale
        val fullWidth = width + 2 * border
        val fullHeight = height + 2 * border
        val pixels = IntArray(fullWidth * fullHeight) { 0xFFFFFFFF.toInt() }
        for (y in 0 until height) for (x in 0 until width) {
            val moduleY = if (twoDimensional) y / scale else 0
            if (matrix.get(x / scale, moduleY)) pixels[(y + border) * fullWidth + x + border] = 0xFF000000.toInt()
        }
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(format.toZxing()),
            DecodeHintType.TRY_HARDER to true,
        )
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(fullWidth, fullHeight, pixels)))
        return MultiFormatReader().decode(bitmap, hints).text
    }
}
