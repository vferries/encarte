package io.github.vferries.encarte.backup

import android.content.Context
import android.util.Log
import io.github.vferries.encarte.R
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.core.barcode.needsUtf8
import io.github.vferries.encarte.core.color.CardPalette
import io.github.vferries.encarte.core.data.Card
import io.github.vferries.encarte.core.data.encodedValue
import io.github.vferries.encarte.core.text.normalizedForMatching
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private const val TAG = "CatimaMapping"

/** Localized `%1$s` patterns for the note lines that carry Catima-only fields. */
data class ImportLabels(
    val validFrom: String,
    val balance: String,
    val points: String,
    val locale: Locale,
)

fun importLabels(context: Context) = ImportLabels(
    validFrom = context.getString(R.string.import_note_valid_from),
    balance = context.getString(R.string.import_note_balance),
    points = context.getString(R.string.import_note_points),
    locale = context.resources.configuration.locales[0],
)

/** Same store (whatever the spelling) and same number: the same card. */
fun Card.duplicateKey(): String = storeName.normalizedForMatching() + "\u0000" + cardNumber.trim()

object CatimaMapping {
    fun toCard(source: CatimaCard, labels: ImportLabels, zone: ZoneId, now: Instant): Card {
        val storeName = source.store.trim()
        if (storeName.isEmpty()) throw CatimaFormatException("Card ${source.id} has no store name")
        val cardNumber = source.cardId.trim()
        return Card(
            storeName = storeName,
            cardNumber = cardNumber,
            barcodeValue = source.barcodeId?.takeIf { it != cardNumber },
            barcodeFormat = source.barcodeType?.let { name ->
                BarcodeFormat.fromName(name) ?: null.also { Log.w(TAG, "Unknown Catima barcode type $name: number only") }
            },
            note = noteWithExtras(source, labels, zone),
            color = CardPalette.opaque(source.headerColor ?: CardPalette.defaultFor(storeName)),
            isFavorite = source.starred,
            createdAt = now,
            lastUsedAt = source.lastUsed.takeIf { it > 0 }?.let(Instant::ofEpochSecond),
            expiresOn = source.expiry?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() },
            isArchived = source.archived,
        )
    }

    fun toCatima(card: Card, zone: ZoneId): CatimaCard = CatimaCard(
        id = card.id.toInt(),
        store = card.storeName,
        note = card.note,
        // Catima stores the start of the expiry day, in the device's time zone.
        expiry = card.expiresOn?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
        cardId = card.cardNumber,
        barcodeId = card.barcodeValue,
        barcodeType = card.barcodeFormat?.name,
        // Empty makes Catima fall back to ISO-8859-1, which matches ZXing's default for ASCII content.
        barcodeEncoding = card.barcodeFormat?.takeIf { it.needsUtf8(card.encodedValue) }?.let { "UTF-8" },
        headerColor = card.color,
        starred = card.isFavorite,
        lastUsed = card.lastUsedAt?.epochSecond ?: 0L,
        archived = card.isArchived,
    )

    private fun noteWithExtras(source: CatimaCard, labels: ImportLabels, zone: ZoneId): String {
        val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(labels.locale).withZone(zone)
        val extras = buildList {
            source.validFrom?.let { add(labels.validFrom.format(labels.locale, dates.format(Instant.ofEpochMilli(it)))) }
            if (source.balance.signum() != 0) add(labels.balance.format(labels.locale, balanceText(source, labels)))
        }
        return (listOf(source.note.trim()).filter { it.isNotEmpty() } + extras).joinToString("\n")
    }

    private fun balanceText(source: CatimaCard, labels: ImportLabels): String {
        val currency = source.balanceType
        return if (currency != null) {
            "${source.balance.setScale(2, RoundingMode.HALF_UP).toPlainString()} $currency"
        } else {
            labels.points.format(labels.locale, source.balance.stripTrailingZeros().toPlainString())
        }
    }
}
