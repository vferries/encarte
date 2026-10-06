package io.github.vferries.encarte.navigation

import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.importing.CardDraft
import io.github.vferries.encarte.importing.DraftNotice
import io.github.vferries.encarte.importing.FoundCode
import io.github.vferries.encarte.importing.ImportFailure
import io.github.vferries.encarte.importing.ImportOutcome
import io.github.vferries.encarte.scan.ScannedCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportDestinationsTest {
    private val qr = FoundCode("LOYALTY-QR-1", BarcodeFormat.QR_CODE, page = 1)
    private val ean = FoundCode("4006381333931", BarcodeFormat.EAN_13, page = 2)

    @Test
    fun aDraftOpensTheEditorInTheScannerGroup() {
        val draft = CardDraft(storeName = "Cinéma", cardNumber = "A-42", barcodeFormat = BarcodeFormat.QR_CODE)

        assertEquals(CardEditKey(draft = draft, groupId = 3), ImportOutcome.Draft(draft).destination(groupId = 3))
    }

    @Test
    fun aPassWithoutBarcodeHasNoUnsupportedFormatNotice() {
        val draft = CardDraft(storeName = "Cinéma", notice = DraftNotice.PASS_WITHOUT_BARCODE)

        assertEquals(CardEditKey(draft = draft), ImportOutcome.Draft(draft).destination(groupId = null))
    }

    @Test
    fun aPdfCodeEncarteCannotDrawComesWithTheNotice() {
        val draft = FoundCode("MAXI-7", null, page = 1).let { CardDraft(cardNumber = it.value) }

        assertEquals(CardEditKey(draft = draft, unsupportedFormat = true), ImportOutcome.Draft(draft).destination(groupId = null))
    }

    @Test
    fun severalCodesOpenTheChooser() {
        assertEquals(ImportChoiceKey(listOf(qr, ean), groupId = 3), ImportOutcome.Choice(listOf(qr, ean)).destination(groupId = 3))
    }

    @Test
    fun anImageOpensTheEditorLikeAScan() {
        val code = ScannedCode("4006381333931", BarcodeFormat.EAN_13)

        assertEquals(
            CardEditKey(barcodeValue = "4006381333931", barcodeFormat = BarcodeFormat.EAN_13, groupId = 3),
            ImportOutcome.Image(code).destination(groupId = 3),
        )
        assertEquals(
            CardEditKey(barcodeValue = "MAXI-7", unsupportedFormat = true),
            ImportOutcome.Image(ScannedCode("MAXI-7", null)).destination(groupId = null),
        )
    }

    @Test
    fun failuresAndEmptyImagesLeadNowhere() {
        assertNull(ImportOutcome.Image(null).destination(groupId = null))
        assertNull(ImportOutcome.Failure(ImportFailure.NO_CODE_IN_PDF).destination(groupId = null))
    }
}
