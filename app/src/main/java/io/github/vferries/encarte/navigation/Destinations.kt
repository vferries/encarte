package io.github.vferries.encarte.navigation

import androidx.navigation3.runtime.NavKey
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import kotlinx.serialization.Serializable

// Keys are @Serializable so the back stack survives process death (reflective NavKey serializer).

@Serializable
data object CardListKey : NavKey

@Serializable
data class CardDisplayKey(val cardId: Long) : NavKey

@Serializable
data object ScannerKey : NavKey

@Serializable
data class CardEditKey(
    val cardId: Long? = null,
    val barcodeValue: String? = null,
    val barcodeFormat: BarcodeFormat? = null,
    val unsupportedFormat: Boolean = false,
) : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data class GroupCardsKey(val groupId: Long) : NavKey
