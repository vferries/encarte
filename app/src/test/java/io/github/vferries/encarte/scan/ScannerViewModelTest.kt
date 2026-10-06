package io.github.vferries.encarte.scan

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.importing.CardDraft
import io.github.vferries.encarte.importing.FoundCode
import io.github.vferries.encarte.importing.ImportFailure
import io.github.vferries.encarte.importing.ImportOutcome
import io.github.vferries.encarte.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScannerViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private var outcome: ImportOutcome = ImportOutcome.Image(null)
    private val vm = ScannerViewModel(readFile = { outcome })

    private fun pick() = vm.readPickedFile { ByteArray(0).inputStream() }

    @Test
    fun aDecodedImageBecomesTheResult() = runTest {
        val code = ScannedCode("4006381333931", BarcodeFormat.EAN_13)
        outcome = ImportOutcome.Image(code)

        pick()

        assertEquals(code, vm.uiState.first { it.result != null }.result)
    }

    @Test
    fun anImageWithoutBarcodeIsReported() = runTest {
        pick()

        assertEquals(ImportFailure.NO_CODE_IN_IMAGE, vm.uiState.first { it.fileError != null }.fileError)
    }

    @Test
    fun aPassOrAPdfIsHandedOverForTheNextScreen() = runTest {
        val draft = ImportOutcome.Draft(CardDraft(storeName = "Cinéma", cardNumber = "A-42", barcodeFormat = BarcodeFormat.QR_CODE))
        outcome = draft

        pick()

        val state = vm.uiState.first { it.fileResult != null }
        assertEquals(draft, state.fileResult)
        assertNull("the camera keeps no result", state.result)
    }

    @Test
    fun severalPdfCodesAreHandedOverToo() = runTest {
        val choice = ImportOutcome.Choice(
            listOf(FoundCode("A", BarcodeFormat.QR_CODE, 1), FoundCode("4006381333931", BarcodeFormat.EAN_13, 2))
        )
        outcome = choice

        pick()

        assertEquals(choice, vm.uiState.first { it.fileResult != null }.fileResult)
    }

    @Test
    fun aFailureShowsUntilTheNextPick() = runTest {
        outcome = ImportOutcome.Failure(ImportFailure.PDF_UNREADABLE)
        pick()
        assertEquals(ImportFailure.PDF_UNREADABLE, vm.uiState.value.fileError)

        outcome = ImportOutcome.Failure(ImportFailure.UNRECOGNIZED_FILE)
        pick()

        assertEquals(ImportFailure.UNRECOGNIZED_FILE, vm.uiState.value.fileError)
    }

    @Test
    fun theFileIsReadingUntilItsOutcomeArrives() = runTest {
        val read = CompletableDeferred<ImportOutcome>()
        val slow = ScannerViewModel(readFile = { read.await() })

        slow.readPickedFile { ByteArray(0).inputStream() }
        assertTrue(slow.uiState.value.readingFile)

        read.complete(ImportOutcome.Failure(ImportFailure.NO_CODE_IN_PDF))
        val done = slow.uiState.first { !it.readingFile }
        assertFalse(done.readingFile)
        assertEquals(ImportFailure.NO_CODE_IN_PDF, done.fileError)
    }
}
