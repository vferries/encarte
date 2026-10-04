package io.github.vferries.encarte.core.data

import android.util.Log
import androidx.room3.ColumnTypeConverter
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import java.time.Instant

private const val TAG = "Converters"

class Converters {
    @ColumnTypeConverter
    fun instantToEpochMillis(instant: Instant): Long = instant.toEpochMilli()

    @ColumnTypeConverter
    fun epochMillisToInstant(epochMillis: Long): Instant = Instant.ofEpochMilli(epochMillis)

    @ColumnTypeConverter
    fun barcodeFormatToName(format: BarcodeFormat): String = format.name

    /** Unknown names (written by a newer version) degrade to "no barcode" instead of crashing. */
    @ColumnTypeConverter
    fun nameToBarcodeFormat(name: String): BarcodeFormat? =
        BarcodeFormat.fromName(name) ?: null.also { Log.w(TAG, "Unknown barcode format in database: $name") }
}
