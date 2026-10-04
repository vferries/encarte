package io.github.vferries.encarte.core.barcode

enum class BarcodeError { EMPTY, INVALID_CHARACTERS, INVALID_LENGTH, INVALID_CHECK_DIGIT }

/** Mirrors ZXing's writer rules so that every value accepted here can be encoded. */
object BarcodeValidator {
    private const val MAX_1D_LENGTH = 80
    // Generous for loyalty cards, and well under the byte capacity of every 2D format, even in UTF-8.
    private const val MAX_2D_LENGTH = 300
    // Characters Code 39/93 encode as a single symbol; any other printable ASCII takes two in extended mode.
    private const val SINGLE_SYMBOL_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. "
    private const val CODABAR_START_STOP = "ABCD"
    private const val CODABAR_ALT_START_STOP = "TN*E"
    private const val CODABAR_BODY = "0123456789-$:/.+"

    fun validate(value: String, format: BarcodeFormat): BarcodeError? {
        if (value.isEmpty()) return BarcodeError.EMPTY
        return when (format) {
            BarcodeFormat.EAN_13 -> gtin(value, dataLength = 12)
            BarcodeFormat.EAN_8 -> gtin(value, dataLength = 7)
            BarcodeFormat.UPC_A -> gtin(value, dataLength = 11)
            BarcodeFormat.UPC_E -> upcE(value)
            BarcodeFormat.ITF -> when {
                !value.isDigits() -> BarcodeError.INVALID_CHARACTERS
                value.length % 2 != 0 || value.length > MAX_1D_LENGTH -> BarcodeError.INVALID_LENGTH
                else -> null
            }
            BarcodeFormat.CODE_128 -> when {
                !value.isPrintableAscii() -> BarcodeError.INVALID_CHARACTERS
                value.length > MAX_1D_LENGTH -> BarcodeError.INVALID_LENGTH
                else -> null
            }
            BarcodeFormat.CODE_39, BarcodeFormat.CODE_93 -> when {
                !value.isPrintableAscii() -> BarcodeError.INVALID_CHARACTERS
                value.sumOf { if (it in SINGLE_SYMBOL_CHARS) 1 else 2 } > MAX_1D_LENGTH -> BarcodeError.INVALID_LENGTH
                else -> null
            }
            BarcodeFormat.CODABAR -> codabar(value)
            BarcodeFormat.QR_CODE, BarcodeFormat.AZTEC, BarcodeFormat.DATA_MATRIX, BarcodeFormat.PDF_417 ->
                if (value.length > MAX_2D_LENGTH) BarcodeError.INVALID_LENGTH else null
        }
    }

    private fun gtin(value: String, dataLength: Int): BarcodeError? = when {
        !value.isDigits() -> BarcodeError.INVALID_CHARACTERS
        value.length == dataLength -> null
        value.length == dataLength + 1 ->
            if (Gtin.hasValidCheckDigit(value)) null else BarcodeError.INVALID_CHECK_DIGIT
        else -> BarcodeError.INVALID_LENGTH
    }

    private fun upcE(value: String): BarcodeError? = when {
        !value.isDigits() -> BarcodeError.INVALID_CHARACTERS
        value.length != 7 && value.length != 8 -> BarcodeError.INVALID_LENGTH
        value[0] != '0' && value[0] != '1' -> BarcodeError.INVALID_CHARACTERS
        value.length == 8 && Gtin.checkDigit(Gtin.expandUpcE(value.take(7))) != value[7].digitToInt() ->
            BarcodeError.INVALID_CHECK_DIGIT
        else -> null
    }

    private fun codabar(value: String): BarcodeError? {
        val upper = value.uppercase()
        val first = upper.first()
        val last = upper.last()
        val guarded = upper.length >= 2 &&
            ((first in CODABAR_START_STOP && last in CODABAR_START_STOP) ||
                (first in CODABAR_ALT_START_STOP && last in CODABAR_ALT_START_STOP))
        val body = if (guarded) upper.substring(1, upper.length - 1) else upper
        return when {
            !body.all { it in CODABAR_BODY } -> BarcodeError.INVALID_CHARACTERS
            value.length > MAX_1D_LENGTH -> BarcodeError.INVALID_LENGTH
            else -> null
        }
    }

    private fun String.isDigits() = all { it in '0'..'9' }

    private fun String.isPrintableAscii() = all { it in ' '..'~' }
}
