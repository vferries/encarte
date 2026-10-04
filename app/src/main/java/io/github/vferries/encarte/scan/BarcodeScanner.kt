package io.github.vferries.encarte.scan

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import zxingcpp.BarcodeReader

private const val TAG = "BarcodeScanner"

/** zxing-cpp's reader is not documented as thread-safe: use one instance per thread. */
class BarcodeScanner {
    private val reader = BarcodeReader(
        BarcodeReader.Options(
            // Empty set = every format, so undrawable ones are still recognised and kept as numbers.
            formats = emptySet(),
            tryHarder = true,
            tryRotate = true,
            tryInvert = true,
            tryDownscale = true,
            maxNumberOfSymbols = 1,
            // Plain text re-encodes; the default HRI mode adds GS1 parentheses and escapes.
            textMode = BarcodeReader.TextMode.PLAIN,
        )
    )

    /** Reads one camera frame (YUV_420_888). The caller closes [image]. */
    fun scan(image: ImageProxy): ScannedCode? = firstUsable {
        reader.read(image)
    }

    fun scan(bitmap: Bitmap): ScannedCode? = firstUsable {
        val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        reader.read(argb)
    }

    private inline fun firstUsable(read: () -> List<BarcodeReader.Result>): ScannedCode? {
        val results = try {
            read()
        } catch (e: RuntimeException) {
            // zxing-cpp rethrows native failures as RuntimeException; a bad frame must not kill the scanner.
            Log.w(TAG, "zxing-cpp read failed", e)
            return null
        }
        val result = results.firstOrNull { it.error == null && !it.text.isNullOrEmpty() } ?: return null
        return ScanMapping.map(result.format, result.text!!)
    }
}
