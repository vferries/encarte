package io.github.vferries.encarte.core.color

import io.github.vferries.encarte.core.text.normalizedForMatching
import kotlin.math.pow

object CardPalette {
    const val BLACK: Int = 0xFF000000.toInt()
    const val WHITE: Int = 0xFFFFFFFF.toInt()

    /** Swatches offered in the card editor; also the pool for deterministic default colors. */
    val swatches: List<Int> = listOf(
        0xFFD32F2F, // red
        0xFFC2185B, // pink
        0xFF7B1FA2, // purple
        0xFF512DA8, // deep purple
        0xFF1976D2, // blue
        0xFF0288D1, // light blue
        0xFF00796B, // teal
        0xFF388E3C, // green
        0xFFF9A825, // yellow
        0xFFF57C00, // orange
        0xFF5D4037, // brown
        0xFF455A64, // blue grey
    ).map { it.toInt() }

    /** Same store name (whatever the spelling) always gets the same color. */
    fun defaultFor(storeName: String): Int =
        swatches[Math.floorMod(storeName.normalizedForMatching().hashCode(), swatches.size)]

    /** Black or white, whichever reads better on [argb] (WCAG relative luminance). */
    fun contentColorFor(argb: Int): Int = if (relativeLuminance(argb) > 0.179) BLACK else WHITE

    /** Imported colors may lack an alpha channel (Catima stores plain ints); tiles must never be transparent. */
    fun opaque(argb: Int): Int = argb or BLACK

    private fun relativeLuminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
