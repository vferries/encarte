package io.github.vferries.encarte.backup

import android.util.Log
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.QuoteMode
import java.io.StringReader
import java.io.UncheckedIOException
import java.math.BigDecimal

/** One row of Catima's cards table, with Catima's units: dates in epoch millis, lastUsed in Unix seconds. */
data class CatimaCard(
    val id: Int,
    val store: String,
    val note: String = "",
    val validFrom: Long? = null,
    val expiry: Long? = null,
    val balance: BigDecimal = BigDecimal.ZERO,
    val balanceType: String? = null,
    val cardId: String,
    val barcodeId: String? = null,
    val barcodeType: String? = null,
    val barcodeEncoding: String? = null,
    val headerColor: Int? = null,
    val starred: Boolean = false,
    val lastUsed: Long = 0,
    val archived: Boolean = false,
)

open class CatimaFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

class UnsupportedCatimaVersionException(val version: Int) :
    CatimaFormatException("Unsupported Catima export version $version")

object CatimaCsv {
    const val FILE_NAME = "catima.csv"

    private const val TAG = "CatimaCsv"
    private const val MAX_SUPPORTED_VERSION = 2
    private val cardColumns = listOf(
        "_id", "store", "note", "validfrom", "expiry", "balance", "balancetype", "cardid", "barcodeid",
        "barcodetype", "barcodeencoding", "headercolor", "starstatus", "lastused", "archive",
    )

    /** ALL_NON_NULL makes a blank line parse as a single null value, unlike a quoted "" (an empty value). */
    private val readFormat = CSVFormat.RFC4180.builder().setQuoteMode(QuoteMode.ALL_NON_NULL).get()

    fun read(text: String): List<CatimaCard> {
        val content = text.removePrefix("﻿")
        val version = parseVersion(content)
        if (version > MAX_SUPPORTED_VERSION) throw UnsupportedCatimaVersionException(version)
        val sections = sections(parseRecords(content))
        // v1: the whole file is the cards table. v2: version, groups, cards, card-group mappings.
        val cardsSection = if (version == 1) sections.firstOrNull() else sections.getOrNull(2)
        return cardsSection?.let(::toCards) ?: throw CatimaFormatException("No cards section")
    }

    fun write(cards: List<CatimaCard>): String {
        val out = StringBuilder()
        CSVFormat.RFC4180.print(out).use { printer ->
            printer.printRecord("2")
            printer.println()
            // Groups are not supported: header only.
            printer.printRecord("_id")
            printer.println()
            printer.printRecord(cardColumns)
            for (card in cards) {
                printer.printRecord(
                    card.id,
                    card.store,
                    card.note,
                    card.validFrom ?: "",
                    card.expiry ?: "",
                    card.balance.toPlainString(),
                    card.balanceType.orEmpty(),
                    card.cardId,
                    card.barcodeId.orEmpty(),
                    card.barcodeType.orEmpty(),
                    card.barcodeEncoding.orEmpty(),
                    card.headerColor ?: "",
                    if (card.starred) 1 else 0,
                    card.lastUsed,
                    if (card.archived) 1 else 0,
                )
            }
            printer.println()
            printer.printRecord("cardId", "groupId")
        }
        return out.toString()
    }

    /** Same rule as Catima: leading digits up to the first whitespace, otherwise version 1. */
    private fun parseVersion(text: String): Int {
        val digits = text.takeWhile { it.isDigit() }
        if (digits.isEmpty() || digits.length > 5) return 1
        val next = text.getOrNull(digits.length)
        return if (next == null || next.isWhitespace()) digits.toInt() else 1
    }

    private fun parseRecords(text: String): List<List<String?>> = try {
        readFormat.parse(StringReader(text)).use { parser -> parser.map { record -> record.toList() } }
    } catch (e: UncheckedIOException) {
        Log.w(TAG, "Malformed CSV: ${e.cause?.javaClass?.simpleName}")
        throw CatimaFormatException("Malformed CSV", e)
    }

    private fun sections(records: List<List<String?>>): List<List<List<String>>> {
        val sections = mutableListOf(mutableListOf<List<String>>())
        for (record in records) {
            if (record.size == 1 && record[0] == null) {
                if (sections.last().isNotEmpty()) sections += mutableListOf<List<String>>()
            } else {
                sections.last() += record.map { it.orEmpty() }
            }
        }
        return sections.filter { it.isNotEmpty() }
    }

    private fun toCards(section: List<List<String>>): List<CatimaCard> {
        val header = section.first()
        if ("_id" !in header || "cardid" !in header) throw CatimaFormatException("Not a Catima cards table")
        return section.drop(1).mapIndexed { index, values ->
            toCard(header.zip(values).toMap(), row = index + 1)
        }
    }

    private fun toCard(fields: Map<String, String>, row: Int): CatimaCard {
        val id = fields["_id"]?.toIntOrNull() ?: throw CatimaFormatException("Row $row: invalid _id")
        val cardId = fields["cardid"].orEmpty()
        if (cardId.isEmpty()) throw CatimaFormatException("Row $row: missing cardid")
        return CatimaCard(
            id = id,
            store = fields["store"].orEmpty(),
            note = fields["note"].orEmpty(),
            validFrom = fields["validfrom"]?.toLongOrNull(),
            expiry = fields["expiry"]?.toLongOrNull(),
            balance = fields["balance"]?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
            balanceType = fields["balancetype"]?.takeIf { it.isNotEmpty() },
            cardId = cardId,
            barcodeId = fields["barcodeid"]?.takeIf { it.isNotEmpty() },
            barcodeType = fields["barcodetype"]?.takeIf { it.isNotEmpty() },
            barcodeEncoding = fields["barcodeencoding"]?.takeIf { it.isNotEmpty() },
            // Like Catima: unparseable optional fields fall back to their defaults.
            headerColor = fields["headercolor"]?.toIntOrNull(),
            starred = fields["starstatus"]?.toIntOrNull() == 1,
            lastUsed = fields["lastused"]?.toLongOrNull() ?: 0L,
            archived = fields["archive"]?.toIntOrNull() == 1,
        )
    }
}
