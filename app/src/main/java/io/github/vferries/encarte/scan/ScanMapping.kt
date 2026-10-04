package io.github.vferries.encarte.scan

import android.util.Log
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.core.barcode.BarcodeValidator
import io.github.vferries.encarte.core.barcode.Gtin
import zxingcpp.BarcodeReader.Format

private const val TAG = "ScanMapping"

/** A decoded barcode; a null [format] means Encarté can store the value but not draw the code. */
data class ScannedCode(val value: String, val format: BarcodeFormat?)

object ScanMapping {
    fun map(format: Format, text: String): ScannedCode {
        val candidate = when (format) {
            Format.QR_CODE, Format.QR_CODE_MODEL_1, Format.QR_CODE_MODEL_2 -> ScannedCode(text, BarcodeFormat.QR_CODE)
            Format.AZTEC, Format.AZTEC_CODE -> ScannedCode(text, BarcodeFormat.AZTEC)
            Format.DATA_MATRIX -> ScannedCode(text, BarcodeFormat.DATA_MATRIX)
            Format.PDF_417, Format.COMPACT_PDF_417 -> ScannedCode(text, BarcodeFormat.PDF_417)
            Format.EAN_13, Format.ISBN -> ScannedCode(text, BarcodeFormat.EAN_13)
            Format.EAN_8 -> ScannedCode(text, BarcodeFormat.EAN_8)
            Format.UPC_A, Format.EAN_UPC -> byLength(text)
            Format.UPC_E -> upcE(text)
            Format.CODE_128 -> ScannedCode(text, BarcodeFormat.CODE_128)
            Format.CODE_39, Format.CODE_39_STD, Format.CODE_39_EXT -> ScannedCode(text, BarcodeFormat.CODE_39)
            Format.CODE_93 -> ScannedCode(text, BarcodeFormat.CODE_93)
            Format.CODABAR -> ScannedCode(text, BarcodeFormat.CODABAR)
            Format.ITF, Format.ITF_14 -> ScannedCode(text, BarcodeFormat.ITF)
            else -> ScannedCode(text, null)
        }
        val mapped = candidate.format ?: return candidate.also { Log.i(TAG, "Scanned $format: not drawable") }
        if (BarcodeValidator.validate(candidate.value, mapped) != null) {
            Log.i(TAG, "Scanned $format value would not re-encode as $mapped: keeping number only")
            return ScannedCode(text, null)
        }
        return candidate
    }

    private fun byLength(text: String): ScannedCode = when (text.length) {
        13 -> ScannedCode(text, BarcodeFormat.EAN_13)
        12 -> ScannedCode(text, BarcodeFormat.UPC_A)
        8 -> ScannedCode(text, BarcodeFormat.EAN_8)
        else -> ScannedCode(text, null)
    }

    /** zxing-cpp gives UPC-E as its 13-digit expansion; checkout cards print the 8-digit form. */
    private fun upcE(text: String): ScannedCode {
        if (text.length == 8) return ScannedCode(text, BarcodeFormat.UPC_E)
        val upcA = when {
            text.length == 13 && text.startsWith('0') -> text.drop(1)
            text.length == 12 -> text
            else -> return ScannedCode(text, null)
        }
        val compressed = Gtin.compressToUpcE(upcA)
        return if (compressed != null) ScannedCode(compressed, BarcodeFormat.UPC_E) else byLength(text)
    }
}
