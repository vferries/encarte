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
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.zip.ZipInputStream

private const val TAG = "PassReader"
private const val PASS_JSON = "pass.json"
private const val MAX_ENTRY_BYTES = 512 * 1024
private const val MAX_ENTRIES = 64
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

    private class EntryTooLargeException(name: String) : IOException("$name is larger than $MAX_ENTRY_BYTES bytes")

    private data class PassBarcode(val format: BarcodeFormat, val message: String, val altText: String?)

    /** Null when [input] is not a recognised pass; the reason is logged. */
    fun read(input: InputStream): CardDraft? {
        val files = try {
            readFiles(input)
        } catch (e: IOException) {
            Log.w(TAG, "Unreadable pass: ${e.message}")
            return null
        }
        val passJson = files.passJson ?: return null.also { Log.w(TAG, "No $PASS_JSON in the file") }
        val pass = parseJson(passJson) ?: return null
        val version = (pass["formatVersion"] as? JsonPrimitive)?.intOrNull
        if (version != 1) {
            Log.w(TAG, "Unsupported pass formatVersion $version")
            return null
        }
        return toDraft(pass, localizationTable(files.strings))
    }

    private fun readFiles(input: InputStream): PassFiles {
        var passJson: ByteArray? = null
        val strings = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            for (examined in 1..MAX_ENTRIES) {
                val entry = zip.nextEntry ?: return PassFiles(passJson, strings)
                val lproj = STRINGS_ENTRY.matchEntire(entry.name)?.groupValues?.get(1)
                when {
                    entry.isDirectory -> Unit
                    entry.name == PASS_JSON -> passJson = readBounded(zip, entry.name)
                    lproj != null -> strings[lproj] = readBounded(zip, entry.name)
                }
            }
            if (zip.nextEntry != null) Log.w(TAG, "More than $MAX_ENTRIES entries: the others are ignored")
        }
        return PassFiles(passJson, strings)
    }

    /** Bounded while reading: an entry's declared size can lie. */
    private fun readBounded(input: InputStream, name: String): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return out.toByteArray()
            if (out.size() + read > MAX_ENTRY_BYTES) throw EntryTooLargeException(name)
            out.write(buffer, 0, read)
        }
    }

    private fun parseJson(bytes: ByteArray): JsonObject? = try {
        Json.parseToJsonElement(String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")) as? JsonObject
            ?: null.also { Log.w(TAG, "$PASS_JSON is not a JSON object") }
    } catch (e: SerializationException) {
        Log.w(TAG, "Invalid $PASS_JSON: ${e.message}")
        null
    }

    /** The app's language folder (fr, then fr-CA…), then English, then none. */
    private fun localizationTable(strings: Map<String, ByteArray>): Map<String, String> {
        val folders = strings.keys.sorted()
        fun folderFor(language: String): String? =
            folders.firstOrNull { it.equals(language, ignoreCase = true) }
                ?: folders.firstOrNull { it.substringBefore('-').substringBefore('_').equals(language, ignoreCase = true) }
        val folder = folderFor(language()) ?: folderFor("en") ?: return emptyMap()
        return PassStrings.parse(PassStrings.decode(strings.getValue(folder)))
    }

    private fun toDraft(pass: JsonObject, table: Map<String, String>): CardDraft {
        fun text(name: String): String? =
            pass.string(name)?.let { table[it] ?: it }?.trim()?.takeIf { it.isNotEmpty() }

        val storeName = text("logoText") ?: text("organizationName").orEmpty()
        val note = text("description").orEmpty()
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

    /** `barcodes` (iOS 9+) first, then the legacy `barcode`. */
    private fun barcodeEntries(pass: JsonObject): List<JsonObject> =
        (pass["barcodes"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>() +
            listOfNotNull(pass["barcode"] as? JsonObject)

    private fun toBarcode(entry: JsonObject): PassBarcode? {
        val formatName = entry.string("format")
        val format = PASS_FORMATS[formatName]
        if (format == null) {
            Log.i(TAG, "Pass barcode format $formatName is not supported")
            return null
        }
        // messageEncoding is not used: Encarté encodes the value as it does for any card.
        val message = entry.string("message").orEmpty()
        val error = BarcodeValidator.validate(message, format)
        if (error != null) {
            Log.w(TAG, "Pass $format barcode rejected: $error")
            return null
        }
        return PassBarcode(format, message, entry.string("altText")?.trim()?.takeIf { it.isNotEmpty() })
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
        Log.w(TAG, "Unparseable pass color $value")
        return null
    }

    /** A date-time with an offset becomes a date in the device's zone; a plain ISO date is taken as is. */
    private fun parseDate(text: String): LocalDate? {
        val value = text.trim()
        return try {
            if ('T' in value) OffsetDateTime.parse(value).atZoneSameInstant(clock.zone).toLocalDate() else LocalDate.parse(value)
        } catch (e: DateTimeParseException) {
            Log.w(TAG, "Unparseable pass expiration date: ${e.message}")
            null
        }
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
