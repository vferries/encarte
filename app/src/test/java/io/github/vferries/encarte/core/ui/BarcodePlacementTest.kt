package io.github.vferries.encarte.core.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class BarcodePlacementTest {
    @Test
    fun oneDimensionalScalesHorizontallyByIntegerAndFillsHeight() {
        // 104 modules into 1000 px: scale 9 → 936 px wide, centered.
        val placement = barcodePlacement(104, 1, 1000, 400, twoDimensional = false)

        assertEquals(IntSize(936, 400), placement.size)
        assertEquals(IntOffset(32, 0), placement.offset)
    }

    @Test
    fun twoDimensionalKeepsSquareModules() {
        val placement = barcodePlacement(29, 29, 1000, 600, twoDimensional = true)

        assertEquals(IntSize(580, 580), placement.size)
        assertEquals(IntOffset(210, 10), placement.offset)
    }

    @Test
    fun matrixWiderThanSpaceIsSqueezedToFit() {
        // A 60-character Code 128 is ~700 modules; on a 500 px wide box it must still draw.
        val placement = barcodePlacement(700, 1, 500, 200, twoDimensional = false)

        assertEquals(IntSize(500, 200), placement.size)
        assertEquals(IntOffset(0, 0), placement.offset)
    }
}
