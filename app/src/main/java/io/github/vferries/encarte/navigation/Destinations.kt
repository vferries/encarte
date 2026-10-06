package io.github.vferries.encarte.navigation

import androidx.navigation3.runtime.NavKey
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.importing.CardDraft
import io.github.vferries.encarte.importing.FoundCode
import kotlinx.serialization.Serializable

// Keys are @Serializable so the back stack survives process death (reflective NavKey serializer).

@Serializable
data object CardListKey : NavKey

@Serializable
data class CardDisplayKey(val cardId: Long) : NavKey

/** [groupId]: the list showed this group when "Add a card" was tapped; the new card joins it. */
@Serializable
data class ScannerKey(val groupId: Long? = null) : NavKey

@Serializable
data class CardEditKey(
    val cardId: Long? = null,
    val barcodeValue: String? = null,
    val barcodeFormat: BarcodeFormat? = null,
    val unsupportedFormat: Boolean = false,
    val groupId: Long? = null,
    /** A new card pre-filled from an imported file. */
    val draft: CardDraft? = null,
) : NavKey

/**
 * "Choose a code" among the codes found in a PDF. The key carries the codes, not the file: the screen survives
 * process death. [groupId] comes from the scanner, like [ScannerKey.groupId].
 */
@Serializable
data class ImportChoiceKey(val codes: List<FoundCode>, val groupId: Long? = null) : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data class GroupCardsKey(val groupId: Long) : NavKey
