package io.github.vferries.encarte.testing

import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.core.data.Card
import java.time.Instant

fun testCard(
    storeName: String = "Store",
    cardNumber: String = "123456",
    barcodeFormat: BarcodeFormat? = null,
    isFavorite: Boolean = false,
    frontImage: String? = null,
    backImage: String? = null,
    createdAt: Instant = Instant.ofEpochMilli(1_700_000_000_000),
    lastUsedAt: Instant? = null,
    id: Long = 0,
) = Card(
    id = id,
    storeName = storeName,
    cardNumber = cardNumber,
    barcodeFormat = barcodeFormat,
    color = 0xFF1976D2.toInt(),
    isFavorite = isFavorite,
    frontImage = frontImage,
    backImage = backImage,
    createdAt = createdAt,
    lastUsedAt = lastUsedAt,
)
