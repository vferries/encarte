package io.github.vferries.encarte.core.barcode

import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix

/** Message never includes the encoded value: it may be a card number. */
class BarcodeEncodingException(message: String, cause: Throwable) : Exception(message, cause)

object BarcodeEncoder {
    /**
     * Encodes at natural size: one pixel per module, one pixel tall for 1D formats.
     * Callers scale by an integer factor so every bar keeps the same width.
     */
    fun encode(value: String, format: BarcodeFormat): BitMatrix {
        try {
            return MultiFormatWriter().encode(value, format.toZxing(), 0, 0, hintsFor(value, format))
        } catch (e: WriterException) {
            throw BarcodeEncodingException("Cannot encode ${format.name}", e)
        } catch (e: IllegalArgumentException) {
            throw BarcodeEncodingException("Invalid content for ${format.name}", e)
        }
    }

    private fun hintsFor(value: String, format: BarcodeFormat): Map<EncodeHintType, Any> = buildMap {
        if (format.needsUtf8(value)) {
            put(EncodeHintType.CHARACTER_SET, Charsets.UTF_8.name())
            // Data Matrix only honours CHARACTER_SET in compact mode.
            if (format == BarcodeFormat.DATA_MATRIX) put(EncodeHintType.DATA_MATRIX_COMPACT, true)
        }
    }
}

fun BarcodeFormat.toZxing(): com.google.zxing.BarcodeFormat = com.google.zxing.BarcodeFormat.valueOf(name)
