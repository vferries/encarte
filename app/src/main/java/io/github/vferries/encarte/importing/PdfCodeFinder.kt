package io.github.vferries.encarte.importing

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.graphics.createBitmap
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.scan.BarcodeScanner
import io.github.vferries.encarte.scan.ScannedCode
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "PdfCodeFinder"
private const val MAX_PAGES = 10
private const val MAX_SYMBOLS_PER_PAGE = 8
private const val LONG_SIDE_PX = 2_400f
/** About 64 MB of ARGB pixels: only a poster-sized page comes near it. */
private const val MAX_PAGE_PIXELS = 4_096.0 * 4_096.0

/** The largest value Encarté can draw (a QR code); longer ones would also bloat the back stack's saved state. */
private const val MAX_VALUE_LENGTH = 300

/** A code found in a file; [page] is 1-based. The "Choose a code" key carries a list of them. */
@Serializable
data class FoundCode(val value: String, val format: BarcodeFormat?, val page: Int)

sealed interface PdfScan {
    /** Possibly empty: the PDF was read but holds no code. */
    data class Codes(val codes: List<FoundCode>) : PdfScan

    /** Password-protected or damaged. */
    data object Unreadable : PdfScan
}

/** The codes of [pages] (the first page first), without the codes too long to keep, each format and value kept once, where it first appears. */
internal fun foundCodes(pages: List<List<ScannedCode>>): List<FoundCode> {
    val all = pages.flatMapIndexed { index, codes -> codes.map { FoundCode(it.value, it.format, page = index + 1) } }
    val (kept, tooLong) = all.partition { it.value.length <= MAX_VALUE_LENGTH }
    if (tooLong.isNotEmpty()) Log.w(TAG, "Dropped ${tooLong.size} code(s) longer than $MAX_VALUE_LENGTH characters")
    return kept.distinctBy { it.format to it.value }
}

/**
 * Long side at 2 400 px, the scale kept between 1 and 4: an A4 page gets about 2.85×, where 1× misses small 2D codes.
 * A poster-sized page, which 1× would not fit in memory, is drawn smaller.
 */
internal fun renderScale(widthPoints: Int, heightPoints: Int): Float {
    val scale = (LONG_SIDE_PX / max(widthPoints, heightPoints)).coerceIn(1f, 4f)
    val fitting = sqrt(MAX_PAGE_PIXELS / (widthPoints.toDouble() * heightPoints)).toFloat()
    return min(scale, fitting)
}

/** Finds the codes of a PDF's first 10 pages. Blocking: call it off the main thread. */
class PdfCodeFinder {
    fun find(file: File): PdfScan = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val scanner = BarcodeScanner()
                val pages = (0 until min(renderer.pageCount, MAX_PAGES)).map { index -> scanPage(renderer, index, scanner) }
                PdfScan.Codes(foundCodes(pages))
            }
        }
    } catch (e: SecurityException) {
        Log.w(TAG, "Password-protected PDF", e)
        PdfScan.Unreadable
    } catch (e: IOException) {
        Log.w(TAG, "Damaged PDF", e)
        PdfScan.Unreadable
    } catch (e: IllegalStateException) {
        // Some damaged files make the renderer throw this instead of an IOException.
        Log.w(TAG, "Unreadable PDF", e)
        PdfScan.Unreadable
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Unreadable PDF", e)
        PdfScan.Unreadable
    }

    private fun scanPage(renderer: PdfRenderer, index: Int, scanner: BarcodeScanner): List<ScannedCode> {
        // PdfRenderer allows only one open page at a time.
        val page = renderer.openPage(index)
        try {
            if (page.width <= 0 || page.height <= 0) {
                Log.w(TAG, "Page ${index + 1} has no size: skipped")
                return emptyList()
            }
            val scale = renderScale(page.width, page.height)
            val bitmap = createBitmap(max(1, (page.width * scale).roundToInt()), max(1, (page.height * scale).roundToInt()))
            try {
                // Where a page draws nothing, it is transparent: scanners need white.
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return scanner.scanAll(bitmap, MAX_SYMBOLS_PER_PAGE)
            } finally {
                bitmap.recycle()
            }
        } finally {
            page.close()
        }
    }
}
