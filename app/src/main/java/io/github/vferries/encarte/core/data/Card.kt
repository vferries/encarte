package io.github.vferries.encarte.core.data

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import java.time.Instant

@Entity(tableName = "cards")
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storeName: String,
    /** Required: Catima rejects cards without one, and exports must stay importable there. */
    val cardNumber: String,
    /** Encoded value when it differs from [cardNumber]; null means the barcode encodes [cardNumber]. */
    val barcodeValue: String? = null,
    /** Null means no barcode: only the number is displayed. */
    val barcodeFormat: BarcodeFormat? = null,
    val note: String = "",
    val color: Int,
    val isFavorite: Boolean = false,
    /** File names inside the image store, not paths. */
    val frontImage: String? = null,
    val backImage: String? = null,
    /** Assigned by CardRepository on insert. */
    val createdAt: Instant,
    val lastUsedAt: Instant? = null,
)

val Card.encodedValue: String get() = barcodeValue ?: cardNumber

enum class CardSide { FRONT, BACK }
