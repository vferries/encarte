package io.github.vferries.encarte

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.hypot

/** Renders the brand vectors for real (native graphics) to check what a launcher or screen will show. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrandDrawablesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun render(id: Int, widthPx: Int, heightPx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val drawable = context.getDrawable(id)!!
        drawable.setBounds(0, 0, widthPx, heightPx)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun assertInsideSafeZone(icon: Bitmap) {
        val center = ICON_PX / 2f
        var painted = 0
        for (y in 0 until ICON_PX) for (x in 0 until ICON_PX) {
            if (Color.alpha(icon.getPixel(x, y)) == 0) continue
            painted++
            val distance = hypot(x + 0.5f - center, y + 0.5f - center)
            assertTrue("pixel ($x, $y) is ${distance / SCALE} dp from the center", distance <= SAFE_RADIUS_PX)
        }
        assertTrue("nothing was drawn", painted > 1_000)
    }

    @Test
    fun foregroundStaysInsideTheSafeZone() {
        assertInsideSafeZone(render(R.drawable.ic_launcher_foreground, ICON_PX, ICON_PX))
    }

    @Test
    fun monochromeStaysInsideTheSafeZone() {
        assertInsideSafeZone(render(R.drawable.ic_launcher_monochrome, ICON_PX, ICON_PX))
    }

    @Test
    fun monochromeBarsShowNothingBehindThem() {
        val icon = render(R.drawable.ic_launcher_monochrome, ICON_PX, ICON_PX)
        // Front card body, left of the bars: solid.
        assertEquals(255, Color.alpha(icon.getPixel(157, 198)))
        // Back card's outline inside the middle card, seen through a bar.
        assertEquals(0, Color.alpha(icon.getPixel(277, 254)))
        // Middle card's outline grazing the top of the last bar: only the front-card clip hides it.
        assertEquals(0, Color.alpha(icon.getPixel(301, 207)))
        // Back card's outline inside the hollow middle card, outside the front card: only the middle-card clip hides it.
        assertEquals(0, Color.alpha(icon.getPixel(140, 169)))
    }

    @Test
    fun emptyStateIllustrationKeepsBrandColors() {
        val drawable = context.getDrawable(R.drawable.illustration_card_fan)!!
        assertEquals(108f / 90f, drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight, 0.01f)
        // Fixed colors, not theme attributes: the illustration must look the same in light and dark themes.
        val art = render(R.drawable.illustration_card_fan, 108 * SCALE, 90 * SCALE)
        assertEquals(Color.WHITE, art.getPixel(150, 144)) // front card, left of the bars
        assertEquals(0xFF2E8C83.toInt(), art.getPixel(130, 231)) // back card, below the middle one
        assertEquals(0xFFF2A93B.toInt(), art.getPixel(126, 211)) // middle card, below the front one
    }

    private companion object {
        const val SCALE = 4
        const val ICON_PX = 108 * SCALE
        const val SAFE_RADIUS_PX = 33f * SCALE
    }
}
