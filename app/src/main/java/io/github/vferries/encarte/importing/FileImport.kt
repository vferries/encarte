package io.github.vferries.encarte.importing

import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.StringRes
import io.github.vferries.encarte.R
import io.github.vferries.encarte.core.ui.decodeSampledBitmap
import io.github.vferries.encarte.scan.ScannedCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream

private const val TAG = "FileImport"
/** Like the scanner's former gallery pick: plenty for a barcode, quick to decode. */
private const val IMAGE_MAX_SIDE = 2048
/** Enough for the PDF and ZIP signatures. */
private const val SNIFF_BYTES = 5

/** Tells "not an image" (null bitmap) from a provider failure (no result) in [ImportFiles.providerOrNull]. */
private class Decoded(val bitmap: Bitmap?)

/** What reading one file gives. */
sealed interface ImportOutcome {
    /** A pass, or a PDF holding one code: the editor opens pre-filled. */
    data class Draft(val draft: CardDraft) : ImportOutcome

    /** A PDF holding several codes: the user chooses one. */
    data class Choice(val codes: List<FoundCode>) : ImportOutcome

    /** An image, scanned like a gallery pick; null when it holds no code. */
    data class Image(val code: ScannedCode?) : ImportOutcome

    data class Failure(val reason: ImportFailure) : ImportOutcome
}

enum class ImportFailure(@StringRes val message: Int) {
    UNRECOGNIZED_FILE(R.string.import_file_unrecognized),
    UNRECOGNIZED_PASS(R.string.import_pass_unrecognized),
    PDF_UNREADABLE(R.string.import_pdf_unreadable),
    NO_CODE_IN_PDF(R.string.import_pdf_no_code),
    NO_CODE_IN_IMAGE(R.string.no_barcode_in_image),
    CANNOT_OPEN(R.string.import_file_cannot_open),
    FILE_GONE(R.string.import_file_gone),
}

/** What to tell the user instead of opening the editor; null when there is a card to edit or a code to choose. */
val ImportOutcome.failure: ImportFailure?
    get() = when (this) {
        is ImportOutcome.Failure -> reason
        is ImportOutcome.Image -> if (code == null) ImportFailure.NO_CODE_IN_IMAGE else null
        is ImportOutcome.Draft, is ImportOutcome.Choice -> null
    }

/** A PDF gives no reliable store name: the user types it. */
fun FoundCode.toDraft(): CardDraft = CardDraft(cardNumber = value, barcodeFormat = format)

/**
 * Reads a file to import, whatever its announced type. A pass or a PDF is copied first, and the copy deleted after;
 * a picture is decoded from the provider. Shared by the scanner and ImportKey.
 */
class FileImport(
    private val files: ImportFiles,
    private val readPass: (InputStream) -> CardDraft?,
    private val findPdfCodes: (File) -> PdfScan,
    private val decodeImage: (Bitmap) -> ScannedCode?,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** A file picked in the scanner. */
    suspend fun importPicked(open: () -> InputStream): ImportOutcome = withContext(io) {
        val cannotOpen = ImportOutcome.Failure(ImportFailure.CANNOT_OPEN)
        val head = files.headOrNull(SNIFF_BYTES, open) ?: return@withContext cannotOpen
        if (needsCopy(head)) {
            val file = files.copyOrNull(shouldContinue = { isActive }, open = open) ?: return@withContext cannotOpen
            analyseAndDelete(file)
        } else {
            decodePicked(open)
        }
    }

    /**
     * Pictures are decoded straight from the provider: a high-resolution photo can exceed the copy cap, and the
     * sampled decode never holds it whole. Passes and PDFs are read whole, so they keep the bounded copy.
     */
    private fun needsCopy(head: ByteArray) = head.startsWith(PDF_SIGNATURE) || head.startsWith(ZIP_SIGNATURE)

    private fun decodePicked(open: () -> InputStream): ImportOutcome {
        val decoded = files.providerOrNull { Decoded(decodeSampledBitmap(IMAGE_MAX_SIDE) { files.providerCall(open) }) }
            ?: return ImportOutcome.Failure(ImportFailure.CANNOT_OPEN)
        val bitmap = decoded.bitmap
        if (bitmap == null) {
            Log.w(TAG, "The picked file is not an image")
            return ImportOutcome.Failure(ImportFailure.UNRECOGNIZED_FILE)
        }
        return ImportOutcome.Image(decodeImage(bitmap))
    }

    /** A file another app sent, which ImportActivity copied under [fileName]. */
    suspend fun importReceived(fileName: String): ImportOutcome = withContext(io) {
        val file = files.resolve(fileName)
        // A copy of an earlier process is going to be deleted by the startup cleanup: don't race it.
        if (file == null || !files.isOwnCopy(fileName) || !file.isFile) {
            Log.w(TAG, "Import file is no longer available or its name was refused (${fileName.length} characters)")
            return@withContext ImportOutcome.Failure(ImportFailure.FILE_GONE)
        }
        analyseAndDelete(file)
    }

    private fun analyseAndDelete(file: File): ImportOutcome = try {
        analyse(file)
    } catch (e: IOException) {
        Log.w(TAG, "Cannot read the file to import", e)
        ImportOutcome.Failure(ImportFailure.CANNOT_OPEN)
    } finally {
        files.delete(file)
    }

    private fun analyse(file: File): ImportOutcome = when (detectFileKind(file)) {
        FileKind.PDF -> pdfOutcome(findPdfCodes(file))
        FileKind.PASS -> file.inputStream().use(readPass)?.let { ImportOutcome.Draft(it) }
            ?: ImportOutcome.Failure(ImportFailure.UNRECOGNIZED_PASS)
        FileKind.IMAGE -> ImportOutcome.Image(decodeSampledBitmap(file, IMAGE_MAX_SIDE)?.let(decodeImage))
        FileKind.UNRECOGNIZED -> ImportOutcome.Failure(ImportFailure.UNRECOGNIZED_FILE)
    }

    private fun pdfOutcome(scan: PdfScan): ImportOutcome = when (scan) {
        PdfScan.Unreadable -> ImportOutcome.Failure(ImportFailure.PDF_UNREADABLE)
        is PdfScan.Codes -> when (scan.codes.size) {
            0 -> ImportOutcome.Failure(ImportFailure.NO_CODE_IN_PDF)
            1 -> ImportOutcome.Draft(scan.codes.single().toDraft())
            else -> ImportOutcome.Choice(scan.codes)
        }
    }
}
