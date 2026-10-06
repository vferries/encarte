package io.github.vferries.encarte.importing

import android.util.Log
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.core.barcode.BarcodeValidator
import io.github.vferries.encarte.core.color.CardPalette
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.Clock
import java.time.DateTimeException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.zip.ZipInputStream

private const val TAG = "PassReader"
private const val PASS_JSON = "pass.json"
private const val MAX_ENTRY_BYTES = 512 * 1024
private const val MAX_ENTRIES = 64
/** Everything the ZIP inflates, kept or skipped: a ZIP bomb is a few KB that expands to gigabytes. */
private const val MAX_TOTAL_INFLATED = 64L * 1024 * 1024
/** kotlinx.serialization recurses on nesting: a few thousand brackets overflow the stack. */
private const val MAX_JSON_DEPTH = 32
// The draft travels in the navigation back stack (a Bundle): its texts must stay small.
private const val MAX_STORE_NAME = 100
private const val MAX_NOTE = 2000
private const val MAX_CARD_NUMBER = 100
private val DATE_YEARS = 1900..9999
private val STRINGS_ENTRY = Regex("""^([^/]+)\.lproj/pass\.strings$""")
private val RGB_COLOR = Regex("""^rgb\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*\)$""")
private val HEX_COLOR = Regex("""^#([0-9a-fA-F]{6})$""")

/** The pass barcode formats Encarté can draw. */
private val PASS_FORMATS = mapOf(
    "PKBarcodeFormatQR" to BarcodeFormat.QR_CODE,
    "PKBarcodeFormatPDF417" to BarcodeFormat.PDF_417,
    "PKBarcodeFormatAztec" to BarcodeFormat.AZTEC,
    "PKBarcodeFormatCode128" to BarcodeFormat.CODE_128,
)

/**
 * Reads a Wallet pass (a .pkpass, which is a ZIP) into a card draft. Only pass.json and the lproj pass.strings are
 * read, in memory and bounded. The signature and manifest.json are not checked: the import is offline and the user
 * chose the file. Images, `voided`, dates, locations and the other fields are ignored: they would go stale.
 */
class PassReader(
    /** The app's current language, e.g. "fr": its lproj table translates the pass's texts. */
    private val language: () -> String,
    /** Its zone turns an expiration date-time into a date. */
    private val clock: Clock,
) {
    private class PassFiles(val passJson: ByteArray?, val strings: Map<String, ByteArray>)

    private class LimitException(reason: String) : IOException(reason)

    private data class PassBarcode(val format: BarcodeFormat, val message: String, val altText: String?)

    /** Null when [input] is not a recognised pass; the reason is logged. */
    fun read(input: InputStream): CardDraft? {
        val files = try {
            readFiles(input)
        } catch (e: IOException) {
            // The message can quote an entry name from the file: only the class is logged.
            Log.w(TAG, "Unreadable pass: ${e.javaClass.simpleName}")
            return null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Unreadable pass entry name: ${e.javaClass.simpleName}")
            return null
        }
        val passJson = files.passJson ?: return null.also { Log.w(TAG, "No $PASS_JSON in the file") }
        val pass = parseJson(passJson) ?: return null
        val version = (pass["formatVersion"] as? JsonPrimitive)?.intOrNull
        if (version != 1) {
            Log.w(TAG, "Unsupported pass formatVersion")
            return null
        }
        return toDraft(pass, localizationTable(files.strings))
    }

    private fun readFiles(input: InputStream): PassFiles {
        var passJson: ByteArray? = null
        val strings = mutableMapOf<String, ByteArray>()
        val budget = Budget()
        // Latin-1 decodes any entry name, so a non-UTF-8 name cannot throw; names flagged UTF-8 stay UTF-8.
        ZipInputStream(input, Charsets.ISO_8859_1).use { zip ->
            repeat(MAX_ENTRIES) {
                val entry = zip.nextEntry ?: return PassFiles(passJson, strings)
                val lproj = STRINGS_ENTRY.matchEntire(entry.name)?.groupValues?.get(1)
                when {
                    entry.name == PASS_JSON && passJson == null -> passJson = readBounded(zip, budget)
                    lproj != null && strings[lproj] == null && isRelevant(lproj) -> strings[lproj] = readBounded(zip, budget)
                    else -> {
                        if (entry.name == PASS_JSON || lproj != null) Log.w(TAG, "Duplicate or unused entry ignored")
                        drain(zip, budget)
                    }
                }
            }
            if (zip.nextEntry != null) Log.w(TAG, "More than $MAX_ENTRIES entries: the others are ignored")
        }
        return PassFiles(passJson, strings)
    }

    private class Budget(var left: Long = MAX_TOTAL_INFLATED)

    /** Only the app's language and English can be used: the other tables are not buffered. */
    private fun isRelevant(folder: String): Boolean =
        folderLanguage(folder).let { it.equals(language(), ignoreCase = true) || it.equals("en", ignoreCase = true) }

    private fun folderLanguage(folder: String) = folder.substringBefore('-').substringBefore('_')

    /** Bounded while reading: an entry's declared size can lie. */
    private fun readBounded(input: InputStream, budget: Budget): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return out.toByteArray()
            spend(budget, read)
            if (out.size() + read > MAX_ENTRY_BYTES) throw LimitException("entry larger than $MAX_ENTRY_BYTES bytes")
            out.write(buffer, 0, read)
        }
    }

    /** nextEntry would inflate a skipped entry silently, so it is read here, against the budget. */
    private fun drain(input: InputStream, budget: Budget) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return
            spend(budget, read)
        }
    }

    private fun spend(budget: Budget, bytes: Int) {
        budget.left -= bytes
        if (budget.left < 0) throw LimitException("more than $MAX_TOTAL_INFLATED inflated bytes")
    }

    private fun parseJson(bytes: ByteArray): JsonObject? {
        val text = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        if (nestingDepthExceeded(text)) {
            Log.w(TAG, "$PASS_JSON is nested deeper than $MAX_JSON_DEPTH levels")
            return null
        }
        return try {
            Json.parseToJsonElement(text) as? JsonObject ?: null.also { Log.w(TAG, "$PASS_JSON is not a JSON object") }
        } catch (e: SerializationException) {
            // The message quotes the file: only the class is logged.
            Log.w(TAG, "Invalid $PASS_JSON: ${e.javaClass.simpleName}")
            null
        }
    }

    /** A linear scan that ignores brackets inside strings. */
    private fun nestingDepthExceeded(text: String): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (c in text) {
            when {
                escaped -> escaped = false
                inString -> if (c == '\\') escaped = true else if (c == '"') inString = false
                c == '"' -> inString = true
                c == '[' || c == '{' -> if (++depth > MAX_JSON_DEPTH) return true
                c == ']' || c == '}' -> depth--
            }
        }
        return false
    }

    /** The app's language folder (fr, then fr-CA…), then English, then none. */
    private fun localizationTable(strings: Map<String, ByteArray>): Map<String, String> {
        val folders = strings.keys.sorted()
        fun folderFor(language: String): String? =
            folders.firstOrNull { it.equals(language, ignoreCase = true) }
                ?: folders.firstOrNull { folderLanguage(it).equals(language, ignoreCase = true) }
        val folder = folderFor(language()) ?: folderFor("en") ?: return emptyMap()
        return PassStrings.parse(PassStrings.decode(strings.getValue(folder)))
    }

    private fun toDraft(pass: JsonObject, table: Map<String, String>): CardDraft {
        fun text(name: String): String? =
            pass.string(name)?.let { table[it] ?: it }?.trim()?.takeIf { it.isNotEmpty() }

        val storeName = capped(text("logoText") ?: text("organizationName").orEmpty(), MAX_STORE_NAME, "store name")
        val note = capped(text("description").orEmpty(), MAX_NOTE, "note")
        val color = pass.string("backgroundColor")?.let(::parseColor)
        val expiresOn = pass.string("expirationDate")?.let(::parseDate)
        val barcode = barcodeEntries(pass).firstNotNullOfOrNull(::toBarcode)
        if (barcode == null) {
            Log.i(TAG, "No usable barcode in the pass: the user enters the number")
            return CardDraft(
                storeName = storeName, color = color, expiresOnEpochDay = expiresOn?.toEpochDay(), note = note,
                notice = DraftNotice.PASS_WITHOUT_BARCODE,
            )
        }
        val cardNumber = barcode.altText ?: barcode.message
        return CardDraft(
            storeName = storeName,
            cardNumber = cardNumber,
            barcodeValue = barcode.message.takeIf { it != cardNumber },
            barcodeFormat = barcode.format,
            color = color,
            expiresOnEpochDay = expiresOn?.toEpochDay(),
            note = note,
        )
    }

    private fun capped(text: String, max: Int, what: String): String {
        if (text.length <= max) return text
        Log.w(TAG, "The pass $what is longer than $max characters: truncated")
        return text.take(max)
    }

    /** `barcodes` (iOS 9+) first, then the legacy `barcode`. */
    private fun barcodeEntries(pass: JsonObject): List<JsonObject> {
        val modern = pass["barcodes"]
        if (modern != null && modern !is JsonArray) Log.w(TAG, "barcodes is not an array")
        val entries = (modern as? JsonArray).orEmpty().mapNotNull {
            (it as? JsonObject).also { entry -> if (entry == null) Log.w(TAG, "A barcodes entry is not an object") }
        }
        val legacy = pass["barcode"]
        if (legacy != null && legacy !is JsonObject) Log.w(TAG, "barcode is not an object")
        return entries + listOfNotNull(legacy as? JsonObject)
    }

    private fun toBarcode(entry: JsonObject): PassBarcode? {
        val formatName = entry.string("format")
        val format = PASS_FORMATS[formatName]
        if (format == null) {
            Log.i(TAG, "A pass barcode format is not supported")
            return null
        }
        // messageEncoding is not used: Encarté encodes the value as it does for any card.
        val message = entry.string("message").orEmpty().trim()
        val error = BarcodeValidator.validate(message, format)
        if (error != null) {
            Log.w(TAG, "A pass $format barcode is rejected: $error")
            return null
        }
        val altText = entry.string("altText")?.trim()?.takeIf { it.isNotEmpty() }
        if (altText != null && altText.length > MAX_CARD_NUMBER) Log.w(TAG, "A pass altText is too long: ignored")
        return PassBarcode(format, message, altText?.takeIf { it.length <= MAX_CARD_NUMBER })
    }

    /** `rgb(r, g, b)` or `#RRGGBB`, made opaque; anything else is no color. */
    private fun parseColor(text: String): Int? {
        val value = text.trim()
        RGB_COLOR.matchEntire(value)?.let { match ->
            val channels = match.groupValues.drop(1).map { it.toInt() }
            if (channels.all { it in 0..255 }) {
                return CardPalette.opaque((channels[0] shl 16) or (channels[1] shl 8) or channels[2])
            }
        }
        HEX_COLOR.matchEntire(value)?.let { return CardPalette.opaque(it.groupValues[1].toInt(16)) }
        Log.w(TAG, "Unparseable pass color")
        return null
    }

    /** A date-time with an offset becomes a date in the device's zone; a plain ISO date is taken as is. */
    private fun parseDate(text: String): LocalDate? {
        val value = text.trim()
        val date = try {
            if ('T' in value) OffsetDateTime.parse(value).atZoneSameInstant(clock.zone).toLocalDate() else LocalDate.parse(value)
        } catch (e: DateTimeException) {
            // Out-of-range values throw DateTimeException itself, not only its parse subclass.
            Log.w(TAG, "Unparseable pass expiration date: ${e.javaClass.simpleName}")
            return null
        }
        if (date.year !in DATE_YEARS) {
            Log.w(TAG, "Pass expiration year outside $DATE_YEARS: ignored")
            return null
        }
        return date
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
