package io.github.vferries.encarte.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizationTest {
    @Test
    fun lowercasesAndStripsAccents() {
        assertEquals("ecomarche", "  Écomarché ".normalizedForMatching())
    }

    @Test
    fun removesPunctuationAndSpaces() {
        assertEquals("eleclerc", "E.Leclerc".normalizedForMatching())
        assertEquals("leroymerlin", "Leroy-Merlin".normalizedForMatching())
        assertEquals("mcdonalds", "McDonald's".normalizedForMatching())
        assertEquals("12345678", "1234 5678".normalizedForMatching())
    }

    @Test
    fun expandsLigatures() {
        assertEquals("tapealoeil", "Tape à l'œil".normalizedForMatching())
        assertEquals("aeon", "Æon".normalizedForMatching())
    }

    @Test
    fun emptyStaysEmpty() {
        assertEquals("", "".normalizedForMatching())
        assertEquals("", " - ".normalizedForMatching())
    }
}
