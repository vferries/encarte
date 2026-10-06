package io.github.vferries.encarte.backup

import android.util.Log
import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteException
import io.github.vferries.encarte.core.data.Card
import io.github.vferries.encarte.core.data.EncarteDatabase
import io.github.vferries.encarte.core.data.ImageStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.util.UUID

private const val TAG = "BackupService"

sealed interface ImportResult {
    data class Success(val imported: Int, val skippedDuplicates: Int) : ImportResult
    data object PasswordRequired : ImportResult
    data object WrongPassword : ImportResult
    data class UnsupportedVersion(val version: Int) : ImportResult
    data object Invalid : ImportResult
    data object IoError : ImportResult
}

sealed interface ExportResult {
    data class Success(val count: Int) : ExportResult
    data object IoError : ExportResult
}

class BackupService(
    private val database: EncarteDatabase,
    private val images: ImageStore,
    private val archive: CatimaArchive,
    private val workDir: File,
    private val labels: () -> ImportLabels,
    private val clock: Clock,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val dao = database.cardDao()

    /** A local copy allows retrying with another password without re-opening the file picker. */
    suspend fun copyToWorkFile(open: () -> InputStream): File = withContext(io) {
        workDir.mkdirs()
        val file = File(workDir, "import-${UUID.randomUUID()}")
        open().use { input -> FileOutputStream(file).use { input.copyTo(it) } }
        file
    }

    fun discard(file: File) {
        if (!file.delete()) Log.w(TAG, "Work file ${file.name} was already gone")
    }

    fun clearWorkDir() {
        workDir.listFiles()?.forEach { it.delete() }
    }

    suspend fun import(file: File, password: CharArray?): ImportResult = withContext(io) {
        try {
            importOrThrow(file, password)
        } catch (e: PasswordRequiredException) {
            Log.i(TAG, "Backup is encrypted: asking for the password")
            ImportResult.PasswordRequired
        } catch (e: WrongPasswordException) {
            Log.i(TAG, "Wrong backup password")
            ImportResult.WrongPassword
        } catch (e: UnsupportedCatimaVersionException) {
            Log.w(TAG, "Unsupported Catima version ${e.version}")
            ImportResult.UnsupportedVersion(e.version)
        } catch (e: CatimaFormatException) {
            Log.w(TAG, "Invalid backup: ${e.message}")
            ImportResult.Invalid
        } catch (e: IOException) {
            Log.e(TAG, "Cannot read backup", e)
            ImportResult.IoError
        } catch (e: SQLiteException) {
            Log.e(TAG, "Import failed in the database", e)
            ImportResult.IoError
        } finally {
            images.clearStaging()
        }
    }

    private suspend fun importOrThrow(file: File, password: CharArray?): ImportResult {
        val sources = archive.read(file, password).cards
        val now = clock.instant()
        val currentLabels = labels()
        val mapped = sources.map { it to CatimaMapping.toCard(it, currentLabels, clock.zone, now) }

        val knownKeys = dao.getAll().mapTo(mutableSetOf()) { it.duplicateKey() }
        val toImport = mapped.filter { (_, card) -> knownKeys.add(card.duplicateKey()) }
        val wantedIds = toImport.mapTo(mutableSetOf()) { (source, _) -> source.id }

        val staged = mutableMapOf<CatimaImageRef, String>()
        archive.forEachImage(file, password) { ref, input ->
            if (ref.cardId in wantedIds && ref.side != ImageSide.ICON) {
                try {
                    staged[ref] = images.stage(input)
                } catch (e: IOException) {
                    Log.w(TAG, "Skipping unreadable image ${ref.entryName}", e)
                }
            }
        }

        // Images first: a card must never reference a file that does not exist.
        val committed = images.commitStaged(staged.values)
        try {
            database.withWriteTransaction<Unit> {
                for ((source, card) in toImport) {
                    dao.insert(
                        card.copy(
                            frontImage = staged[CatimaImageRef(source.id, ImageSide.FRONT)],
                            backImage = staged[CatimaImageRef(source.id, ImageSide.BACK)],
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Import transaction failed, removing its images", e)
            images.deleteAll(committed)
            throw e
        }
        return ImportResult.Success(imported = toImport.size, skippedDuplicates = mapped.size - toImport.size)
    }

    suspend fun export(open: () -> OutputStream, password: CharArray?): ExportResult = withContext(io) {
        workDir.mkdirs()
        val temp = File(workDir, "export-${UUID.randomUUID()}.zip")
        try {
            val cards = dao.getAll()
            val csv = CatimaCsv.write(cards.map { CatimaMapping.toCatima(it, clock.zone) })
            archive.write(FileOutputStream(temp), csv, cards.flatMap(::archiveImages), password)
            open().use { out -> temp.inputStream().use { it.copyTo(out) } }
            ExportResult.Success(cards.size)
        } catch (e: IOException) {
            Log.e(TAG, "Export failed", e)
            ExportResult.IoError
        } catch (e: SQLiteException) {
            Log.e(TAG, "Export failed in the database", e)
            ExportResult.IoError
        } catch (e: SecurityException) {
            // The destination's grant can be revoked between the picker and the write.
            Log.e(TAG, "Export destination is not writable", e)
            ExportResult.IoError
        } finally {
            temp.delete()
        }
    }

    private fun archiveImages(card: Card): List<ArchiveImage> = listOfNotNull(
        card.frontImage?.let { archiveImage(card, it, ImageSide.FRONT) },
        card.backImage?.let { archiveImage(card, it, ImageSide.BACK) },
    )

    private fun archiveImage(card: Card, name: String, side: ImageSide): ArchiveImage? {
        if (!images.exists(name)) {
            Log.w(TAG, "Card ${card.id} references missing image $name: not exported")
            return null
        }
        return ArchiveImage(CatimaImageRef(card.id.toInt(), side)) { out -> images.writePng(name, out) }
    }
}
