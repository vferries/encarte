package io.github.vferries.encarte.core.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardPaletteTest {
    @Test
    fun hasTwelveOpaqueSwatches() {
        assertEquals(12, CardPalette.swatches.size)
        assertTrue(CardPalette.swatches.all { it ushr 24 == 0xFF })
    }

    @Test
    fun defaultColorIsStableAcrossSpellings() {
        val color = CardPalette.defaultFor("Carrefour")
        assertEquals(color, CardPalette.defaultFor(" carrefour "))
        assertTrue(color in CardPalette.swatches)
    }

    @Test
    fun contentColorIsReadable() {
        assertEquals(CardPalette.BLACK, CardPalette.contentColorFor(0xFFFFFF00.toInt()))
        assertEquals(CardPalette.WHITE, CardPalette.contentColorFor(0xFF000080.toInt()))
    }

    @Test
    fun opaqueForcesFullAlpha() {
        assertEquals(0xFF000001.toInt(), CardPalette.opaque(1))
        assertEquals(0xFF123456.toInt(), CardPalette.opaque(0x00123456))
    }
}
