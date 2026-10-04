package io.github.vferries.encarte.core.barcode

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.common.BitMatrix
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BarcodeBitmapsTest {
    @Test
    fun setModulesAreBlackOthersWhite() {
        val matrix = BitMatrix(3, 1).apply {
            set(0, 0)
            set(2, 0)
        }

        val bitmap = matrix.toBitmap()

        assertEquals(3, bitmap.width)
        assertEquals(1, bitmap.height)
        assertEquals(Color.BLACK, bitmap.getPixel(0, 0))
        assertEquals(Color.WHITE, bitmap.getPixel(1, 0))
        assertEquals(Color.BLACK, bitmap.getPixel(2, 0))
    }
}
