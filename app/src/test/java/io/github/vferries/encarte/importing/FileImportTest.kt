package io.github.vferries.encarte.importing

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.scan.ScannedCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog
import java.io.File
import java.io.IOException
import java.time.Clock
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalCoroutinesApi::class)
class FileImportTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dir by lazy { File(tmp.root, "imports") }
    private val files by lazy { ImportFiles(dir) }
    private val qr = FoundCode("LOYALTY-1", BarcodeFormat.QR_CODE, page = 1)
    private val ean = FoundCode("4006381333931", BarcodeFormat.EAN_13, page = 2)
    private var pdfScan: PdfScan = PdfScan.Codes(emptyList())
    private var imageCode: ScannedCode? = null

    // zxing-cpp and PdfRenderer do not run on the JVM: their results are faked; the pass reader is the real one.
    private val import by lazy {
        FileImport(
            files = files,
            readPass = PassReader({ "en" }, Clock.systemUTC())::read,
            findPdfCodes = { pdfScan },
            decodeImage = { imageCode },
            io = UnconfinedTestDispatcher(),
        )
    }

    private suspend fun picked(bytes: ByteArray) = import.importPicked { bytes.inputStream() }

    private fun leftovers() = dir.listFiles().orEmpty().toList()

    @Test
    fun aPassOpensTheEditorPrefilled() = runTest {
        assertEquals(ImportOutcome.Draft(CardDraft(storeName = "Cinéma", notice = DraftNotice.PASS_WITHOUT_BARCODE)), picked(TestFiles.pass()))
    }

    @Test
    fun aBrokenPassIsReportedAsSuch() = runTest {
        assertEquals(ImportOutcome.Failure(ImportFailure.UNRECOGNIZED_PASS), picked(TestFiles.pass("""{"formatVersion": 2}""")))
    }

    @Test
    fun aPdfGivesADraftAChoiceOrAFailure() = runTest {
        pdfScan = PdfScan.Codes(listOf(ean))
        assertEquals(ImportOutcome.Draft(CardDraft(cardNumber = "4006381333931", barcodeFormat = BarcodeFormat.EAN_13)), picked(TestFiles.pdf))

        pdfScan = PdfScan.Codes(listOf(qr, ean))
        assertEquals(ImportOutcome.Choice(listOf(qr, ean)), picked(TestFiles.pdf))

        pdfScan = PdfScan.Codes(emptyList())
        assertEquals(ImportOutcome.Failure(ImportFailure.NO_CODE_IN_PDF), picked(TestFiles.pdf))

        pdfScan = PdfScan.Unreadable
        assertEquals(ImportOutcome.Failure(ImportFailure.PDF_UNREADABLE), picked(TestFiles.pdf))
    }

    @Test
    fun anImageIsScannedForOneCode() = runTest {
        imageCode = ScannedCode("4006381333931", BarcodeFormat.EAN_13)
        assertEquals(ImportOutcome.Image(imageCode), picked(TestFiles.png()))

        imageCode = null
        val nothing = picked(TestFiles.png())
        assertEquals(ImportOutcome.Image(null), nothing)
        assertEquals(ImportFailure.NO_CODE_IN_IMAGE, nothing.failure)
    }

    @Test
    fun anythingElseIsUnrecognised() = runTest {
        assertEquals(ImportOutcome.Failure(ImportFailure.UNRECOGNIZED_FILE), picked("just text".toByteArray()))
    }

    @Test
    fun theCopyIsDeletedWhateverTheOutcome() = runTest {
        picked(TestFiles.pass())
        picked("just text".toByteArray())
        pdfScan = PdfScan.Unreadable
        picked(TestFiles.pdf)

        assertEquals(emptyList<File>(), leftovers())
    }

    @Test
    fun aFileThatCannotBeCopiedCannotBeOpened() = runTest {
        assertEquals(ImportOutcome.Failure(ImportFailure.CANNOT_OPEN), import.importPicked { throw SecurityException("revoked") })
        assertEquals(ImportOutcome.Failure(ImportFailure.CANNOT_OPEN), import.importPicked { throw IOException("gone") })
        assertEquals(
            ImportOutcome.Failure(ImportFailure.CANNOT_OPEN),
            import.importPicked { ByteArray((MAX_IMPORT_BYTES + 1).toInt()).inputStream() },
        )
        assertEquals(emptyList<File>(), leftovers())
    }

    @Test
    fun aProviderThrowingARuntimeExceptionCannotBeOpened() = runTest {
        val cannotOpen = ImportOutcome.Failure(ImportFailure.CANNOT_OPEN)

        assertEquals(cannotOpen, import.importPicked { throw IllegalArgumentException("bad uri") })
        assertEquals(cannotOpen, import.importPicked { throw IllegalStateException("died") })
        assertEquals(cannotOpen, import.importPicked { throw UnsupportedOperationException("no") })
        assertEquals(emptyList<File>(), leftovers())
    }

    @Test
    fun aRefusedNameIsNotLogged() = runTest {
        import.importReceived("../secret-hostile-name")

        assertEquals(emptyList<String>(), ShadowLog.getLogs().filter { it.msg.contains("secret-hostile-name") }.map { it.msg })
    }

    @Test
    fun aReceivedFileIsReadThenDeleted() = runTest {
        val received = files.copy { TestFiles.pass().inputStream() }

        assertEquals(ImportOutcome.Draft::class, import.importReceived(received.name)::class)
        assertEquals(emptyList<File>(), leftovers())
    }

    @Test
    fun aFileOfAnEarlierProcessIsGoneWithoutBeingRead() = runTest {
        dir.mkdirs()
        val earlier = File(dir, UUID.randomUUID().toString()).apply { writeBytes(TestFiles.pass()) }

        assertEquals(ImportOutcome.Failure(ImportFailure.FILE_GONE), import.importReceived(earlier.name))
        assertEquals("the startup cleanup owns it", listOf(earlier), leftovers())
    }

    @Test
    fun aReceivedFileThatIsGoneOrBadlyNamedIsNoLongerAvailable() = runTest {
        val gone = ImportOutcome.Failure(ImportFailure.FILE_GONE)

        assertEquals(gone, import.importReceived(UUID.randomUUID().toString()))
        assertEquals(gone, import.importReceived("../databases/encarte.db"))
    }

    @Test
    fun onlyFailuresAndEmptyImagesAreReported() {
        assertNull(ImportOutcome.Draft(CardDraft()).failure)
        assertNull(ImportOutcome.Choice(listOf(qr, ean)).failure)
        assertNull(ImportOutcome.Image(ScannedCode("1", null)).failure)
        assertEquals(ImportFailure.PDF_UNREADABLE, ImportOutcome.Failure(ImportFailure.PDF_UNREADABLE).failure)
    }
}
