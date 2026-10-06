package io.github.vferries.encarte.importing

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

@RunWith(AndroidJUnit4::class)
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

    @Test(timeout = 5_000)
    fun manyMalformedLinesLogOneWarning() {
        ShadowLog.clear()

        PassStrings.parse("\"a\" = \"1\";\n" + "broken\n".repeat(50_000))

        assertEquals(1, ShadowLog.getLogs().count { it.tag == "PassStrings" })
    }

    @Test
    fun anUnterminatedCommentEndsTheTable() {
        assertEquals(mapOf("a" to "1"), PassStrings.parse("\"a\" = \"1\";\n/* \"b\" = \"2\";"))
    }

    @Test
    fun aValueWithoutItsClosingQuoteKeepsTheEarlierEntries() {
        assertEquals(mapOf("a" to "1"), PassStrings.parse("\"a\" = \"1\";\n\"b\" = \"2;"))
    }

    // Each line opens a comment that never closes: rescanning the rest of the text per line would be quadratic.
    @Test(timeout = 1_000)
    fun manyUnterminatedCommentsParseInLinearTime() {
        val hostile = "\"a\"/*\n".repeat(512 * 1024 / 6)

        assertEquals(mapOf("first" to "1"), PassStrings.parse("\"first\" = \"1\";\n$hostile"))
    }

    // Recovery must never rescan text a failed entry already read, or these inputs are quadratic.
    @Test(timeout = 1_000)
    fun manyEntriesSpanningOneTerminatedCommentParseInLinearTime() {
        val hostile = "\"a\"/*\n".repeat(512 * 1024 / 6) + "*/"

        assertEquals(mapOf("first" to "1"), PassStrings.parse("\"first\" = \"1\";\n$hostile"))
    }

    @Test(timeout = 1_000)
    fun manyOpeningQuotesClosedOnlyAtTheEndParseInLinearTime() {
        val hostile = "\"a\n".repeat(512 * 1024 / 3) + "\""

        assertEquals(mapOf("first" to "1"), PassStrings.parse("\"first\" = \"1\";\n$hostile"))
    }

    @Test
    fun emptyInputGivesAnEmptyTable() {
        assertEquals("", PassStrings.decode(ByteArray(0)))
        assertEquals(emptyMap<String, String>(), PassStrings.parse(""))
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
