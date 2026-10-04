package io.github.vferries.encarte.core.barcode

/**
 * Barcode types Encarté can display, i.e. those ZXing can encode.
 * Constant names match ZXing's (and Catima's) on purpose: they are persisted and exported by name.
 */
enum class BarcodeFormat(val label: String, val isTwoDimensional: Boolean) {
    QR_CODE("QR Code", true),
    AZTEC("Aztec", true),
    DATA_MATRIX("Data Matrix", true),
    PDF_417("PDF417", true),
    EAN_13("EAN-13", false),
    EAN_8("EAN-8", false),
    UPC_A("UPC-A", false),
    UPC_E("UPC-E", false),
    CODE_128("Code 128", false),
    CODE_39("Code 39", false),
    CODE_93("Code 93", false),
    CODABAR("Codabar", false),
    ITF("ITF", false);

    companion object {
        fun fromName(name: String): BarcodeFormat? = entries.firstOrNull { it.name == name }
    }
}

/** 2D codes with non-ASCII content must be encoded as UTF-8: ZXing's default charset turns them into '?'. */
fun BarcodeFormat.needsUtf8(value: String): Boolean = isTwoDimensional && value.any { it.code >= 128 }
