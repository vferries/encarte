package io.github.vferries.encarte.scan

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import zxingcpp.BarcodeReader

private const val TAG = "BarcodeScanner"

private fun readerOptions(maxSymbols: Int) = BarcodeReader.Options(
    // Empty set = every format, so undrawable ones are still recognised and kept as numbers.
    formats = emptySet(),
    tryHarder = true,
    tryRotate = true,
    tryInvert = true,
    tryDownscale = true,
    maxNumberOfSymbols = maxSymbols,
    // Plain text re-encodes; the default HRI mode adds GS1 parentheses and escapes.
    textMode = BarcodeReader.TextMode.PLAIN,
)

/** zxing-cpp's reader is not documented as thread-safe: use one instance per thread. */
class BarcodeScanner {
    private val reader = BarcodeReader(readerOptions(maxSymbols = 1))

    /** Reads one camera frame (YUV_420_888). The caller closes [image]. */
    fun scan(image: ImageProxy): ScannedCode? = usableCodes {
        reader.read(image)
    }.firstOrNull()

    fun scan(bitmap: Bitmap): ScannedCode? = usableCodes {
        reader.read(bitmap.argb())
    }.firstOrNull()

    /** Every usable code in [bitmap], up to [maxSymbols]: a PDF page can hold several. */
    fun scanAll(bitmap: Bitmap, maxSymbols: Int): List<ScannedCode> = usableCodes {
        // A reader per call: it only holds options, and the native library is already loaded.
        BarcodeReader(readerOptions(maxSymbols)).read(bitmap.argb())
    }

    private fun Bitmap.argb(): Bitmap = if (config == Bitmap.Config.ARGB_8888) this else copy(Bitmap.Config.ARGB_8888, false)

    private inline fun usableCodes(read: () -> List<BarcodeReader.Result>): List<ScannedCode> {
        val results = try {
            read()
        } catch (e: RuntimeException) {
            // zxing-cpp rethrows native failures as RuntimeException; a bad frame must not kill the scanner.
            Log.w(TAG, "zxing-cpp read failed", e)
            return emptyList()
        }
        return results.filter { it.error == null && !it.text.isNullOrEmpty() }.map { ScanMapping.map(it.format, it.text!!) }
    }
}
