package io.github.vferries.encarte.core.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.math.max

private const val TAG = "Images"

/**
 * Decodes an image subsampled (powers of two) so its longest side stays close to [maxSide].
 * [open] is called twice (bounds, then pixels). Returns null when the data is not an image.
 */
fun decodeSampledBitmap(maxSide: Int, open: () -> InputStream): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open().use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxSide) sampleSize *= 2
    return open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize }) }
}

fun decodeSampledBitmap(file: File, maxSide: Int): Bitmap? = try {
    decodeSampledBitmap(maxSide) { file.inputStream() }
} catch (e: IOException) {
    Log.w(TAG, "Cannot read image ${file.name}", e)
    null
}

@Composable
fun rememberImageBitmap(file: File?, maxSide: Int): ImageBitmap? {
    val image by produceState<ImageBitmap?>(initialValue = null, file, maxSide) {
        value = file?.let { source ->
            withContext(Dispatchers.IO) {
                decodeSampledBitmap(source, maxSide)?.asImageBitmap()
                    ?: null.also { Log.w(TAG, "Cannot decode image ${source.name}") }
            }
        }
    }
    return image
}
