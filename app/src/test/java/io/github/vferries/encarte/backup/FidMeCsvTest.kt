package io.github.vferries.encarte.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class FidMeCsvTest {
    private fun fixture() = javaClass.getResource("/fidme/${FidMeCsv.FILE_NAME}")!!.readText()

    @Test
    fun readsTheFixtureWithQuotedSemicolonsAndBlankNumbers() {
        assertEquals(
            listOf(
                FidMeRow("Fnac", "4006381333931", program = "Carte Fnac+", firstName = "Marie", lastName = "Dupont"),
                FidMeRow("Carrefour", "C-2049-77", program = "Carrefour"),
                FidMeRow("Boulangerie \"Chez Paul\"; Lyon", "0042", lastName = "Dupont"),
                FidMeRow("Décathlon", "", program = "Carte Décathlon", firstName = "Marie", lastName = "Dupont"),
            ),
            FidMeCsv.read(fixture()),
        )
    }

    @Test
    fun aBomHeaderCaseAndSpacesAreTolerated() {
        assertEquals(listOf(FidMeRow("Fnac", "42")), FidMeCsv.read("\uFEFF RETAILER ; reference \r\n Fnac ; 42 \r\n"))
    }

    @Test
    fun blankLinesAreNotRows() {
        assertEquals(listOf(FidMeRow("Fnac", "42")), FidMeCsv.read("Retailer;Reference\n\nFnac;42\n\n"))
    }

    @Test
    fun shortRowsReadMissingColumnsAsEmpty() {
        assertEquals(listOf(FidMeRow("Fnac", "")), FidMeCsv.read("Retailer;Program;Reference\nFnac\n"))
    }

    @Test(expected = FidMeFormatException::class)
    fun withoutAReferenceColumnItIsNotAFidMeExport() {
        FidMeCsv.read("Retailer;Program\nFnac;Carte Fnac+\n")
    }

    @Test(expected = FidMeFormatException::class)
    fun withoutARetailerColumnItIsNotAFidMeExport() {
        FidMeCsv.read("Store;Reference\nFnac;42\n")
    }

    @Test(expected = FidMeFormatException::class)
    fun anEmptyFileIsNotAFidMeExport() {
        FidMeCsv.read("")
    }

    @Test(expected = FidMeFormatException::class)
    fun anUnterminatedQuoteIsMalformed() {
        FidMeCsv.read("Retailer;Reference\n\"Fnac;42\n")
    }
}
