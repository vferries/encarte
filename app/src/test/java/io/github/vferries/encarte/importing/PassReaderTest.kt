package io.github.vferries.encarte.importing

import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.core.color.CardPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PassReaderTest {
    private var language = "fr"
    private val reader = PassReader({ language }, Clock.fixed(Instant.parse("2026-10-06T12:00:00Z"), ZoneId.of("Europe/Paris")))

    private fun read(passJson: String, vararg others: Pair<String, ByteArray>): CardDraft? =
        reader.read(TestFiles.zip(mapOf("pass.json" to passJson.toByteArray()) + others).inputStream())

    private fun pass(fields: String) =
        """{"formatVersion": 1, "organizationName": "Org"""" + (if (fields.isEmpty()) "" else ", $fields") + "}"

    private fun barcode(format: String, message: String, altText: String? = null) =
        """{"format": "$format", "message": "$message", "messageEncoding": "iso-8859-1"""" +
            (altText?.let { """, "altText": "$it"""" } ?: "") + "}"

    @Test
    fun readsTheSamplePass() {
        val folder = File(javaClass.getResource("/pass/sample/pass.json")!!.toURI()).parentFile!!
        val entries = folder.walk().filter { it.isFile }.associate { it.relativeTo(folder).invariantSeparatorsPath to it.readBytes() }

        assertEquals(
            CardDraft(
                storeName = "Magasin d'exemple",
                cardNumber = "0001 2049",
                barcodeValue = "ENC-0001-2049",
                barcodeFormat = BarcodeFormat.QR_CODE,
                color = 0xFF254F9B.toInt(),
                expiresOnEpochDay = LocalDate.of(2027, 3, 12).toEpochDay(),
                note = "Carte de fidélité",
            ),
            reader.read(TestFiles.zip(entries).inputStream()),
        )
    }

    @Test
    fun barcodesComeBeforeTheLegacyBarcode() {
        val draft = read(pass(""""barcodes": [${barcode("PKBarcodeFormatPDF417", "NEW-1")}], "barcode": ${barcode("PKBarcodeFormatQR", "OLD-1")}"""))!!

        assertEquals(BarcodeFormat.PDF_417, draft.barcodeFormat)
        assertEquals("NEW-1", draft.cardNumber)
    }

    @Test
    fun unsupportedOrInvalidBarcodesAreSkipped() {
        val unsupported = barcode("PKBarcodeFormatEAN13", "4006381333931")
        val notCode128 = barcode("PKBarcodeFormatCode128", "Carte-é")
        val tooLong = barcode("PKBarcodeFormatQR", "Q".repeat(301))
        val draft = read(
            pass(""""barcodes": [$unsupported, $notCode128, $tooLong], "barcode": ${barcode("PKBarcodeFormatAztec", "AZ-7")}""")
        )!!

        assertEquals(BarcodeFormat.AZTEC, draft.barcodeFormat)
        assertEquals("AZ-7", draft.cardNumber)
    }

    @Test
    fun theAltTextIsTheNumberAndTheMessageTheEncodedValue() {
        val withAltText = read(pass(""""barcodes": [${barcode("PKBarcodeFormatCode128", "12345678", altText = " 1234 5678 ")}]"""))!!
        val sameAltText = read(pass(""""barcodes": [${barcode("PKBarcodeFormatCode128", "12345678", altText = "12345678")}]"""))!!
        val blankAltText = read(pass(""""barcodes": [${barcode("PKBarcodeFormatCode128", "12345678", altText = " ")}]"""))!!

        assertEquals("1234 5678" to "12345678", withAltText.cardNumber to withAltText.barcodeValue)
        assertEquals("12345678" to null, sameAltText.cardNumber to sameAltText.barcodeValue)
        assertEquals("12345678" to null, blankAltText.cardNumber to blankAltText.barcodeValue)
    }

    @Test
    fun aPassWithoutUsableBarcodeAsksForTheNumber() {
        val draft = read(pass(""""logoText": "Cinéma", "description": "Séance de 20 h", "barcodes": [${barcode("PKBarcodeFormatEAN13", "1")}]"""))

        assertEquals(
            CardDraft(storeName = "Cinéma", note = "Séance de 20 h", notice = DraftNotice.PASS_WITHOUT_BARCODE),
            draft,
        )
    }

    @Test
    fun theLogoTextComesBeforeTheOrganizationName() {
        assertEquals("Logo", read(pass(""""logoText": " Logo """"))!!.storeName)
        assertEquals("Org", read(pass(""""logoText": "  """"))!!.storeName)
        assertEquals("", read("""{"formatVersion": 1}""")!!.storeName)
    }

    @Test
    fun textsAreTranslatedWithTheAppLanguageThenEnglish() {
        val json = pass(""""logoText": "store", "description": "kind"""")
        val french = "fr.lproj/pass.strings" to "\"store\" = \"Magasin\";".toByteArray()
        val english = "en.lproj/pass.strings" to
            (byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "/* UTF-16 */\n\"store\" = \"Store\";\n\"kind\" = \"Loyalty \\\"card\\\"\";".toByteArray(Charsets.UTF_16LE))

        assertEquals("Magasin" to "kind", read(json, french, english)!!.let { it.storeName to it.note })
        language = "de"
        assertEquals("Store" to "Loyalty \"card\"", read(json, french, english)!!.let { it.storeName to it.note })
        assertEquals("store" to "kind", read(json, french)!!.let { it.storeName to it.note })
    }

    @Test
    fun aRegionalFolderMatchesItsLanguage() {
        val json = pass(""""logoText": "store"""")

        assertEquals("Magasin", read(json, "fr-CA.lproj/pass.strings" to "\"store\" = \"Magasin\";".toByteArray())!!.storeName)
    }

    @Test
    fun colorsAreReadAsRgbOrHexAndMadeOpaque() {
        assertEquals(0xFF254F9B.toInt(), read(pass(""""backgroundColor": "rgb( 37,79 , 155 )""""))!!.color)
        assertEquals(0xFFFF8800.toInt(), read(pass(""""backgroundColor": "#ff8800""""))!!.color)
        assertEquals(CardPalette.WHITE, read(pass(""""backgroundColor": "rgb(255, 255, 255)""""))!!.color)
        assertNull(read(pass(""""backgroundColor": "rgb(300, 0, 0)""""))!!.color)
        assertNull(read(pass(""""backgroundColor": "blue""""))!!.color)
    }

    @Test
    fun anExpirationDateTimeBecomesADateInTheDeviceZone() {
        val draft = read(pass(""""expirationDate": "2027-03-12T23:30:00-05:00""""))!!

        assertEquals(LocalDate.of(2027, 3, 13).toEpochDay(), draft.expiresOnEpochDay)
        // UTC date is the 12th, the Paris date the 13th
        assertEquals(
            LocalDate.of(2027, 3, 13).toEpochDay(),
            read(pass(""""expirationDate": "2027-03-12T23:30:00Z""""))!!.expiresOnEpochDay,
        )
    }

    @Test
    fun aPlainExpirationDateIsKeptAndAnInvalidOneIgnored() {
        assertEquals(LocalDate.of(2027, 3, 12).toEpochDay(), read(pass(""""expirationDate": "2027-03-12""""))!!.expiresOnEpochDay)
        assertNull(read(pass(""""expirationDate": "next week""""))!!.expiresOnEpochDay)
    }

    @Test
    fun onlyFormatVersion1IsAPass() {
        assertNull(read("""{"formatVersion": 2, "organizationName": "Org"}"""))
        assertNull(read("""{"organizationName": "Org"}"""))
    }

    @Test
    fun invalidJsonIsNotAPass() {
        assertNull(read("""{"formatVersion": 1,"""))
        assertNull(read("""[1]"""))
    }

    @Test
    fun aZipWithoutPassJsonOrNotAZipIsNotAPass() {
        assertNull(reader.read(TestFiles.zip(mapOf("other.json" to "{}".toByteArray())).inputStream()))
        assertNull(reader.read("not a zip".byteInputStream()))
    }

    @Test
    fun anOversizedEntryMakesTheFileUnrecognised() {
        val huge = "en.lproj/pass.strings" to ByteArray(512 * 1024 + 1) { 'a'.code.toByte() }

        assertNull(read(pass(""""logoText": "store""""), huge))
    }

    @Test
    fun onlyTheFirst64EntriesAreExamined() {
        val padding = (1..64).associate { "images/$it.png" to byteArrayOf(1) }
        val passLast = TestFiles.zip(padding + ("pass.json" to pass("").toByteArray()))

        assertNull(reader.read(passLast.inputStream()))
    }

    private fun longText(n: Int) = "x".repeat(n)

    @Test
    fun deeplyNestedJsonIsRefusedWithoutOverflowingTheStack() {
        assertNull(read("[".repeat(5000)))
        assertNull(read("""{"formatVersion": 1, "a": """ + "[".repeat(5000)))
        assertNull(read("""{"a":[""".repeat(5000)))
        assertEquals("Org", read(pass(""""x": [[{"y": "[[["}]]"""))!!.storeName)
    }

    @Test
    fun aNonUtf8EntryNameDoesNotMakeThePassUnreadable() {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out, Charsets.ISO_8859_1).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("images/caf\u00e9.png")); zip.write(1); zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("pass.json")); zip.write(pass("").toByteArray()); zip.closeEntry()
        }

        assertEquals("Org", reader.read(out.toByteArray().inputStream())!!.storeName)
    }

    @Test
    fun anOutOfRangeExpirationDateIsIgnored() {
        val draft = read(pass(""""expirationDate": "+999999999-12-31T23:59:59-18:00""""))!!

        assertNull(draft.expiresOnEpochDay)
        assertNull(read(pass(""""expirationDate": "+10000-01-01""""))!!.expiresOnEpochDay)
        assertNull(read(pass(""""expirationDate": "1850-01-01""""))!!.expiresOnEpochDay)
    }

    @Test
    fun longTextsAreTruncatedAndALongAltTextIsIgnored() {
        val draft = read(pass(""""logoText": "${longText(500)}", "description": "${longText(5000)}", "barcodes": [${barcode("PKBarcodeFormatQR", "MSG-1", altText = longText(101))}]"""))!!

        assertEquals(100, draft.storeName.length)
        assertEquals(2000, draft.note.length)
        assertEquals("MSG-1", draft.cardNumber)
        assertNull(draft.barcodeValue)
        assertEquals(longText(100), read(pass(""""barcodes": [${barcode("PKBarcodeFormatQR", "MSG-1", altText = longText(100))}]"""))!!.cardNumber)
    }

    @Test
    fun aBlankMessageIsUnusableAndAPaddedOneIsTrimmed() {
        val draft = read(pass(""""barcodes": [${barcode("PKBarcodeFormatQR", "  ")}, ${barcode("PKBarcodeFormatQR", " AB-1 ")}]"""))!!
        assertEquals("AB-1", draft.cardNumber)

        assertEquals(
            DraftNotice.PASS_WITHOUT_BARCODE,
            read(pass(""""barcodes": [${barcode("PKBarcodeFormatQR", "  ")}]"""))!!.notice,
        )
    }

    @Test
    fun aZipBombInASkippedEntryIsRefusedQuickly() {
        val zeros = "images/big.png" to ByteArray(80 * 1024 * 1024)

        assertNull(read(pass(""""logoText": "x""""), zeros))
    }

    @Test
    fun aTableForAnotherLanguageIsNotUsedButDrained() {
        language = "fr"
        val german = "de.lproj/pass.strings" to "\"store\" = \"Laden\";".toByteArray()

        assertEquals("store", read(pass(""""logoText": "store""""), german)!!.storeName)
    }

    @Test
    fun aDuplicatePassJsonKeepsTheFirst() {
        // ZipOutputStream refuses a duplicate name: the second is renamed in the bytes, with the same length.
        val zip = TestFiles.zip(
            linkedMapOf(
                "pass.json" to """{"formatVersion": 1, "organizationName": "First"}""".toByteArray(),
                "pass.jsoX" to """{"formatVersion": 1, "organizationName": "Second"}""".toByteArray(),
            )
        )
        val renamed = String(zip, Charsets.ISO_8859_1).replace("pass.jsoX", "pass.json").toByteArray(Charsets.ISO_8859_1)

        assertEquals("First", reader.read(renamed.inputStream())!!.storeName)
    }

    @Test
    fun malformedBarcodeFieldsAreSkipped() {
        assertEquals("Org", read(pass(""""barcodes": 5, "barcode": [1]"""))!!.storeName)
        val draft = read(pass(""""barcodes": [7, ${barcode("PKBarcodeFormatQR", "OK-1")}]"""))!!
        assertEquals("OK-1", draft.cardNumber)
    }

    @Test
    fun aPassWithoutBarcodeKeepsItsColorAndExpiry() {
        val draft = read(pass(""""backgroundColor": "#ff8800", "expirationDate": "2027-03-12""""))!!

        assertEquals(0xFFFF8800.toInt(), draft.color)
        assertEquals(LocalDate.of(2027, 3, 12).toEpochDay(), draft.expiresOnEpochDay)
        assertEquals(DraftNotice.PASS_WITHOUT_BARCODE, draft.notice)
    }

    @Test
    fun aRegionalFolderComesBeforeEnglish() {
        val regional = "fr-CA.lproj/pass.strings" to "\"store\" = \"Magasin CA\";".toByteArray()
        val english = "en.lproj/pass.strings" to "\"store\" = \"Store\";".toByteArray()

        assertEquals("Magasin CA", read(pass(""""logoText": "store""""), english, regional)!!.storeName)
    }

    @Test
    fun aByteOrderMarkBeforePassJsonIsAccepted() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val zip = TestFiles.zip(mapOf("pass.json" to bom + pass("").toByteArray()))

        assertEquals("Org", reader.read(zip.inputStream())!!.storeName)
    }

    @Test
    fun aTruncatedZipIsNotAPass() {
        val zip = TestFiles.zip(mapOf("pass.json" to pass("").toByteArray(), "other" to ByteArray(2000) { it.toByte() }))

        assertNull(reader.read(zip.copyOf(zip.size / 2).inputStream()))
    }

    @Test
    fun entrySizeAndCountBoundaries() {
        val exact = "en.lproj/pass.strings" to ByteArray(512 * 1024) { 'a'.code.toByte() }
        assertEquals("Org", read(pass(""), exact)!!.storeName)

        fun withPaddingBefore(count: Int) =
            TestFiles.zip((1..count).associate { "images/$it.png" to byteArrayOf(1) } + ("pass.json" to pass("").toByteArray()))
        assertEquals("Org", reader.read(withPaddingBefore(63).inputStream())!!.storeName)
        assertNull(reader.read(withPaddingBefore(64).inputStream()))
    }
}
