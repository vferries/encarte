package io.github.vferries.encarte.backup

import android.util.Log
import org.apache.commons.csv.CSVFormat
import java.io.StringReader
import java.io.UncheckedIOException
import java.util.Locale

/** One row of FidMe's loyalty_programs.csv. Fields are trimmed; a missing column reads as empty. */
data class FidMeRow(
    val retailer: String,
    val reference: String,
    val program: String = "",
    val firstName: String = "",
    val lastName: String = "",
)

class FidMeFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * FidMe's export as Catima's importer and test fixture describe it (2021): loyalty_programs.csv in a ZIP,
 * `;`-separated, header `Retailer;Program;Added At;Reference;Firstname;Lastname`. Unverified on a 2026 export
 * (spec §8): a real file that differs is a bug to fix with that file as the new fixture.
 */
object FidMeCsv {
    const val FILE_NAME = "loyalty_programs.csv"

    private const val TAG = "FidMeCsv"
    private val format = CSVFormat.RFC4180.builder().setDelimiter(';').setIgnoreEmptyLines(true).get()

    fun read(text: String): List<FidMeRow> {
        val records = parse(text.removePrefix("\uFEFF"))
        val header = records.firstOrNull().orEmpty().map { it.trim().lowercase(Locale.ROOT) }
        val retailer = header.indexOf("retailer")
        val reference = header.indexOf("reference")
        if (retailer < 0 || reference < 0) {
            Log.w(TAG, "No Retailer or Reference column in a header of ${header.size} column(s)")
            throw FidMeFormatException("Not a FidMe export")
        }
        val program = header.indexOf("program")
        val firstName = header.indexOf("firstname")
        val lastName = header.indexOf("lastname")
        return records.drop(1).map { row ->
            fun field(column: Int): String = row.getOrNull(column)?.trim().orEmpty()
            FidMeRow(
                retailer = field(retailer),
                reference = field(reference),
                program = field(program),
                firstName = field(firstName),
                lastName = field(lastName),
            )
        }
    }

    private fun parse(text: String): List<List<String>> = try {
        format.parse(StringReader(text)).use { parser -> parser.map { record -> record.toList() } }
    } catch (e: UncheckedIOException) {
        Log.w(TAG, "Malformed $FILE_NAME: ${e.cause?.javaClass?.simpleName}")
        throw FidMeFormatException("Malformed $FILE_NAME", e)
    }
}
