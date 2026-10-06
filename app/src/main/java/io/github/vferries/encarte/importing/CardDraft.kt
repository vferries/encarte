package io.github.vferries.encarte.importing

import io.github.vferries.encarte.core.barcode.BarcodeFormat
import kotlinx.serialization.Serializable

/**
 * What an import hands to the card editor. It travels inside a navigation key, so it stays small and serializable:
 * the expiry is an epoch day because LocalDate has no serializer.
 */
@Serializable
data class CardDraft(
    val storeName: String = "",
    val cardNumber: String = "",
    /** The encoded value when it differs from [cardNumber]. */
    val barcodeValue: String? = null,
    val barcodeFormat: BarcodeFormat? = null,
    /** Null: the editor follows the store name (brand color, else palette color), as for any new card. */
    val color: Int? = null,
    val expiresOnEpochDay: Long? = null,
    val note: String = "",
    val notice: DraftNotice? = null,
)

enum class DraftNotice { PASS_WITHOUT_BARCODE }
