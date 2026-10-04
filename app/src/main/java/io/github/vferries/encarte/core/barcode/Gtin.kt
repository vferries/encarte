package io.github.vferries.encarte.core.barcode

/** GS1 check digits and UPC-E (de)compression, shared by validation and scan normalization. */
internal object Gtin {
    fun checkDigit(data: String): Int {
        val sum = data.reversed().withIndex().sumOf { (index, char) ->
            char.digitToInt() * if (index % 2 == 0) 3 else 1
        }
        return (10 - sum % 10) % 10
    }

    fun hasValidCheckDigit(code: String): Boolean =
        code.length >= 2 && checkDigit(code.dropLast(1)) == code.last().digitToInt()

    /** Expands number system + 6 UPC-E digits (7 chars) into the 11 data digits of the matching UPC-A. */
    fun expandUpcE(upcE7: String): String {
        val numberSystem = upcE7[0]
        val d = upcE7.substring(1)
        val body = when (val last = d[5]) {
            '0', '1', '2' -> d.substring(0, 2) + last + "0000" + d.substring(2, 5)
            '3' -> d.substring(0, 3) + "00000" + d.substring(3, 5)
            '4' -> d.substring(0, 4) + "00000" + d[4]
            else -> d.substring(0, 5) + "0000" + last
        }
        return numberSystem + body
    }

    /** Compresses a 12-digit UPC-A into an 8-digit UPC-E, or returns null when it has no UPC-E form. */
    fun compressToUpcE(upcA: String): String? {
        val numberSystem = upcA[0]
        if (numberSystem != '0' && numberSystem != '1') return null
        val manufacturer = upcA.substring(1, 6)
        val product = upcA.substring(6, 11)
        val six = when {
            manufacturer[2] in '0'..'2' && manufacturer.endsWith("00") && product.startsWith("00") ->
                manufacturer.substring(0, 2) + product.substring(2, 5) + manufacturer[2]
            manufacturer.endsWith("00") && product.startsWith("000") ->
                manufacturer.substring(0, 3) + product.substring(3, 5) + "3"
            manufacturer[4] == '0' && product.startsWith("0000") ->
                manufacturer.substring(0, 4) + product[4] + "4"
            product.startsWith("0000") && product[4] in '5'..'9' ->
                manufacturer + product[4]
            else -> return null
        }
        return "$numberSystem$six${upcA[11]}"
    }
}
