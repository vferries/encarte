package io.github.vferries.encarte.scan

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.common.BitMatrix
import io.github.vferries.encarte.core.barcode.BarcodeEncoder
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** zxing-cpp's native library only runs on a device: encode with ZXing, decode with zxing-cpp. */
@RunWith(AndroidJUnit4::class)
class BarcodeScannerTest {
    private val scanner = BarcodeScanner()

    private val samples = listOf(
        BarcodeFormat.QR_CODE to "LOYALTY-123456",
        BarcodeFormat.QR_CODE to "Käseschnitte €",
        BarcodeFormat.AZTEC to "1234567890ABC",
        BarcodeFormat.DATA_MATRIX to "CARD12345",
        BarcodeFormat.PDF_417 to "CARD-1234-5678",
        BarcodeFormat.EAN_13 to "4006381333931",
        BarcodeFormat.EAN_8 to "73513537",
        BarcodeFormat.UPC_A to "036000291452",
        BarcodeFormat.UPC_E to "01234565",
        BarcodeFormat.CODE_128 to "Loyalty#42-x",
        BarcodeFormat.CODE_39 to "CARD 123-ABC",
        BarcodeFormat.CODE_93 to "CARD-93",
        BarcodeFormat.CODABAR to "123456",
        BarcodeFormat.ITF to "12345678",
    )

    @Test
    fun everyDrawableFormatScansBackToIdenticalBars() {
        for ((format, value) in samples) {
            val original = BarcodeEncoder.encode(value, format)

            val scanned = requireNotNull(scanner.scan(render(original, format))) { "$format not decoded" }
            val scannedFormat = requireNotNull(scanned.format) { "$format mapped to no format" }

            // Same bars, even when the reported format or text differs (UPC-A as EAN-13, Codabar guards).
            assertEquals("$format bars", original, BarcodeEncoder.encode(scanned.value, scannedFormat))
        }
    }

    private fun render(matrix: BitMatrix, format: BarcodeFormat): Bitmap {
        val scale = 4
        val border = 40
        val height = if (format.isTwoDimensional) matrix.height * scale else 120
        val width = matrix.width * scale
        val bitmap = Bitmap.createBitmap(width + 2 * border, height + 2 * border, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        for (y in 0 until height) for (x in 0 until width) {
            val moduleY = if (format.isTwoDimensional) y / scale else 0
            if (matrix.get(x / scale, moduleY)) bitmap.setPixel(x + border, y + border, Color.BLACK)
        }
        return bitmap
    }
}
