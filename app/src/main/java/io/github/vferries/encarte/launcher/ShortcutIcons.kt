package io.github.vferries.encarte.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.content.res.ResourcesCompat
import io.github.vferries.encarte.R
import io.github.vferries.encarte.core.color.CardPalette
import kotlin.math.roundToInt

/** 108 dp, as adaptive icons are: the launcher masks it, so the letter stays inside the 66 dp safe zone. */
fun cardIconBitmap(context: Context, card: LauncherCard): Bitmap {
    val size = (108 * context.resources.displayMetrics.density).roundToInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val background = CardPalette.opaque(card.color)
    canvas.drawColor(background)
    val initial = initialOf(card.storeName) ?: return bitmap
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CardPalette.contentColorFor(background)
        textAlign = Paint.Align.CENTER
        textSize = size * 0.33f
        typeface = ResourcesCompat.getFont(context, R.font.nunito)
    }
    val bounds = Rect()
    paint.getTextBounds(initial, 0, initial.length, bounds)
    canvas.drawText(initial, size / 2f, size / 2f - bounds.exactCenterY(), paint)
    return bitmap
}

/** The first code point, so an emoji or an accented letter stays whole. */
internal fun initialOf(name: String): String? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return null
    return String(Character.toChars(trimmed.codePointAt(0))).uppercase()
}
