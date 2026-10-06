package io.github.vferries.encarte.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.core.color.CardPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutIconsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun theInitialIsTheFirstCharacterUppercased() {
        assertEquals("É", initialOf("  été"))
        assertEquals("🛒", initialOf("🛒 Courses"))
        assertNull(initialOf("   "))
    }

    @Test
    fun theIconIsTheCardColorWithItsInitialDrawnOnIt() {
        // No alpha: imported colors may lack it, the icon must still be opaque.
        val bitmap = cardIconBitmap(context, LauncherCard(1, "Fnac", 0x1976D2))

        val background = CardPalette.opaque(0x1976D2)
        assertEquals(background, bitmap.getPixel(1, 1))
        val center = bitmap.width / 2
        val letterPixels = (center - 20..center + 20).flatMap { x -> (center - 20..center + 20).map { y -> bitmap.getPixel(x, y) } }
        assertTrue("the initial is drawn", letterPixels.any { it != background })
    }
}
