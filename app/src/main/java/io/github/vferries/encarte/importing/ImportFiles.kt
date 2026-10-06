package io.github.vferries.encarte.importing

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "ImportFiles"
private val FILE_NAME = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

/** 20 MB: far above any pass or ticket PDF, and the analysis reads the file into memory. */
const val MAX_IMPORT_BYTES = 20L * 1024 * 1024

class FileTooLargeException(limit: Long) : IOException("File larger than $limit bytes")

/**
 * Copies of the files to import, in the app's cache: a picker's or another app's read grant can end at any time.
 * Each copy is deleted once analysed. A process killed meanwhile leaves its copies to the next startup's cleanup.
 */
class ImportFiles(private val dir: File, private val maxBytes: Long = MAX_IMPORT_BYTES) {
    /** Names copied by this process: on a cold start, ImportActivity's copy can land before the startup cleanup runs. */
    private val ownNames: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Copies [open]'s content under a random UUID name. A failed or oversized copy leaves nothing. Blocking. */
    fun copy(open: () -> InputStream): File {
        dir.mkdirs()
        val name = UUID.randomUUID().toString()
        ownNames += name
        val file = File(dir, name)
        var complete = false
        try {
            open().use { input -> FileOutputStream(file).use { output -> copyBounded(input, output) } }
            complete = true
            return file
        } finally {
            if (!complete) delete(file)
        }
    }

    /** The copy named [name], or null when [name] is not a name [copy] makes (logged). */
    fun resolve(name: String): File? {
        if (!isFileName(name)) {
            Log.w(TAG, "Refusing import file name \"$name\"")
            return null
        }
        return File(dir, name)
    }

    fun delete(file: File) {
        ownNames -= file.name
        if (!file.delete() && file.exists()) Log.w(TAG, "Cannot delete import file ${file.name}")
    }

    /** Deletes the copies left by an earlier process: nothing will read them any more. Blocking. */
    fun clearLeftovers() {
        dir.listFiles()?.filter { it.name !in ownNames }?.forEach { leftover ->
            Log.i(TAG, "Deleting leftover import file ${leftover.name}")
            if (!leftover.delete()) Log.w(TAG, "Cannot delete leftover import file ${leftover.name}")
        }
    }

    /** Bounded while copying: the announced size of a shared file can lie. */
    private fun copyBounded(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return
            total += read
            if (total > maxBytes) throw FileTooLargeException(maxBytes)
            output.write(buffer, 0, read)
        }
    }

    companion object {
        /** A UUID: no separator and no dot, so it cannot point outside the work folder. */
        fun isFileName(name: String): Boolean = FILE_NAME.matches(name)
    }
}
