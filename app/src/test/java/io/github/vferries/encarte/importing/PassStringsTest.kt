package io.github.vferries.encarte.importing

import org.junit.Assert.assertEquals
import org.junit.Test

class PassStringsTest {
    @Test
    fun readsEntriesBetweenCommentsWithTheirEscapes() {
        val text = """
            /* Store card
               strings */
            "store" = "Magasin \"Chez Paul\"";  // the logo text
            "lines"="Ligne 1\nLigne 2\tTab \\ fin";
        """.trimIndent()

        assertEquals(
            mapOf("store" to "Magasin \"Chez Paul\"", "lines" to "Ligne 1\nLigne 2\tTab \\ fin"),
            PassStrings.parse(text),
        )
    }

    @Test
    fun aMalformedLineIsSkippedAndTheRestKept() {
        val text = "\"a\" = \"1\";\n\"broken\" \"2\";\n\"c\" = \"3\"\n\"d\" = \"4\";\n\"unterminated = \"5\";\n\"e\" = \"6\";"

        assertEquals(mapOf("a" to "1", "d" to "4", "e" to "6"), PassStrings.parse(text))
    }

    @Test
    fun anUnterminatedCommentEndsTheTable() {
        assertEquals(mapOf("a" to "1"), PassStrings.parse("\"a\" = \"1\";\n/* \"b\" = \"2\";"))
    }

    @Test
    fun decodesUtf8WithOrWithoutBom() {
        assertEquals("\"é\" = \"è\";", PassStrings.decode("\uFEFF\"é\" = \"è\";".toByteArray(Charsets.UTF_8)))
        assertEquals("\"é\" = \"è\";", PassStrings.decode("\"é\" = \"è\";".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun decodesUtf16WithABomInEitherByteOrder() {
        val text = "\"store\" = \"Cinéma\";"

        assertEquals(text, PassStrings.decode(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)))
        assertEquals(text, PassStrings.decode(byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + text.toByteArray(Charsets.UTF_16BE)))
    }

    @Test
    fun decodesUtf16WithoutBomFromItsNulBytes() {
        val text = "\"store\" = \"Cinéma\";"

        assertEquals(text, PassStrings.decode(text.toByteArray(Charsets.UTF_16LE)))
        assertEquals(text, PassStrings.decode(text.toByteArray(Charsets.UTF_16BE)))
    }
}
