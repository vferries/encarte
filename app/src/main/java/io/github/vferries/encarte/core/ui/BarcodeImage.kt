package io.github.vferries.encarte.core.ui

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.zxing.common.BitMatrix
import io.github.vferries.encarte.R
import io.github.vferries.encarte.core.barcode.BarcodeEncoder
import io.github.vferries.encarte.core.barcode.BarcodeEncodingException
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.core.barcode.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

private const val TAG = "BarcodeImage"

/** Always black on white with a quiet zone, whatever the theme: checkout scanners need the contrast. */
@Composable
fun BarcodeImage(code: String, format: BarcodeFormat, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.barcode_description, format.label, code)
    // Null until encoded: only a failed encoding shows the explanation.
    val encoded by produceState<Result<BitMatrix>?>(initialValue = null, code, format) {
        value = withContext(Dispatchers.Default) {
            try {
                Result.success(BarcodeEncoder.encode(code, format))
            } catch (e: BarcodeEncodingException) {
                // The exception chain may contain the value (a card number): log the type only.
                Log.w(TAG, "Cannot render ${format.name}: ${e.cause?.javaClass?.simpleName}")
                Result.failure(e)
            }
        }
    }
    val bitmap = remember(encoded) { encoded?.getOrNull()?.toBitmap()?.asImageBitmap() }
    Box(
        modifier
            .aspectRatio(format.displayAspectRatio)
            .background(Color.White)
            .padding(16.dp)
            .semantics { contentDescription = description }
            .testTag("barcode"),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Canvas(Modifier.fillMaxSize()) {
                val placement = barcodePlacement(
                    bitmap.width, bitmap.height, size.width.toInt(), size.height.toInt(), format.isTwoDimensional,
                )
                drawImage(
                    image = bitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = placement.offset,
                    dstSize = placement.size,
                    filterQuality = FilterQuality.None,
                )
            }
        } else if (encoded?.isFailure == true) {
            Text(
                stringResource(R.string.barcode_unavailable),
                color = Color.Black,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val BarcodeFormat.displayAspectRatio: Float
    get() = when {
        this == BarcodeFormat.PDF_417 -> 2.5f
        isTwoDimensional -> 1f
        else -> 2.2f
    }

internal data class BarcodePlacement(val offset: IntOffset, val size: IntSize)

/**
 * Integer scaling keeps every module the same width, which scanners need.
 * 1D codes stretch vertically; a matrix wider than the space is squeezed rather than cropped.
 */
internal fun barcodePlacement(
    matrixWidth: Int,
    matrixHeight: Int,
    availableWidth: Int,
    availableHeight: Int,
    twoDimensional: Boolean,
): BarcodePlacement {
    val horizontalScale = max(1, availableWidth / matrixWidth)
    val scale = if (twoDimensional) max(1, min(horizontalScale, availableHeight / matrixHeight)) else horizontalScale
    val width = min(matrixWidth * scale, availableWidth)
    val height = if (twoDimensional) min(matrixHeight * scale, availableHeight) else availableHeight
    return BarcodePlacement(
        offset = IntOffset((availableWidth - width) / 2, (availableHeight - height) / 2),
        size = IntSize(width, height),
    )
}
