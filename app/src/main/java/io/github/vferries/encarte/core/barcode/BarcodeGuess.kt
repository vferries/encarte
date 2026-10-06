package io.github.vferries.encarte.core.barcode

/**
 * The most likely barcode type of a bare card number, for imports that carry none (FidMe). A retail-length number
 * with a valid GS1 check digit is an EAN or a UPC; any other printable ASCII prints as Code 128; anything else
 * stays a number only. The user is told to check: this is a guess.
 */
fun guessBarcodeFormat(number: String): BarcodeFormat? = when {
    number.isGtin(13) -> BarcodeFormat.EAN_13
    number.isGtin(8) -> BarcodeFormat.EAN_8
    number.isGtin(12) -> BarcodeFormat.UPC_A
    BarcodeValidator.validate(number, BarcodeFormat.CODE_128) == null -> BarcodeFormat.CODE_128
    else -> null
}

private fun String.isGtin(digits: Int): Boolean = length == digits && all { it in '0'..'9' } && Gtin.hasValidCheckDigit(this)
