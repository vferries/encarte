package io.github.vferries.encarte.importing

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
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

    /**
     * Copies [open]'s content under a random UUID name. A failed, oversized or cancelled copy leaves nothing. Blocking.
     * [shouldContinue] is asked between buffers: when false, the copy stops with a [CancellationException], so a slow
     * provider is not read to the end for nothing.
     */
    fun copy(shouldContinue: () -> Boolean = { true }, open: () -> InputStream): File {
        dir.mkdirs()
        val name = UUID.randomUUID().toString()
        ownNames += name
        val file = File(dir, name)
        var complete = false
        try {
            providerCall { open() }.use { input -> FileOutputStream(file).use { output -> copyBounded(input, output, shouldContinue) } }
            complete = true
            return file
        } finally {
            if (!complete) delete(file)
        }
    }

    /** [copy], or null when the provider fails or the file is too large (see [providerOrNull]). Blocking. */
    fun copyOrNull(shouldContinue: () -> Boolean = { true }, open: () -> InputStream): File? =
        providerOrNull { copy(shouldContinue, open) }

    /**
     * The first [size] bytes (fewer for a shorter file) of a fresh [open], which is closed; null when the provider
     * fails (see [providerOrNull]). Blocking.
     */
    fun headOrNull(size: Int, open: () -> InputStream): ByteArray? = providerOrNull {
        providerCall { open() }.use { input ->
            val buffer = ByteArray(size)
            var filled = 0
            while (filled < size) {
                val read = providerCall { input.read(buffer, filled, size - filled) }
                if (read < 0) break
                filled += read
            }
            buffer.copyOf(filled)
        }
    }

    /**
     * [block]'s result, or null (logged, class only: the provider chose the message and the URI is private) when the
     * provider fails or the file is too large. The one place that maps provider failures. Blocking.
     */
    fun <T : Any> providerOrNull(block: () -> T): T? = try {
        block()
    } catch (e: IOException) {
        // Includes FileTooLargeException and a provider's NullPointerException (see providerCall).
        Log.w(TAG, "Cannot read the file: ${e.javaClass.simpleName}")
        null
    } catch (e: SecurityException) {
        Log.w(TAG, "The file is no longer readable")
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalArgumentException) {
        providerFailed(e)
    } catch (e: IllegalStateException) {
        providerFailed(e)
    } catch (e: UnsupportedOperationException) {
        providerFailed(e)
    }

    /** A provider reached over binder can throw these; a partial copy is already deleted by [copy]. */
    private fun providerFailed(e: RuntimeException): Nothing? {
        Log.w(TAG, "The content provider failed: ${e.javaClass.simpleName}")
        return null
    }

    /**
     * A provider's NullPointerException crosses binder like any other (Parcel EX_NULL_POINTER). It is caught only
     * around the provider's own calls, never around this app's code, where it would be a bug to surface.
     */
    internal fun <T> providerCall(call: () -> T): T = try {
        call()
    } catch (e: NullPointerException) {
        throw IOException("The content provider threw a NullPointerException", e)
    }

    /** The copy named [name], or null when [name] is not a name [copy] makes (logged). */
    fun resolve(name: String): File? {
        if (!isFileName(name)) {
            Log.w(TAG, "Refusing an import file name of ${name.length} characters")
            return null
        }
        return File(dir, name)
    }

    /** Whether this process copied [name]: a restored request may name a copy of an earlier, dead process. */
    fun isOwnCopy(name: String): Boolean = name in ownNames

    fun delete(file: File) {
        // Forget the name only once the file is gone, or clearLeftovers could race the deletion.
        if (!file.delete() && file.exists()) Log.w(TAG, "Cannot delete import file ${file.name}")
        ownNames -= file.name
    }

    /** Deletes the copies left by an earlier process: nothing will read them any more. Blocking. */
    fun clearLeftovers() {
        dir.listFiles()?.filter { it.name !in ownNames }?.forEach { leftover ->
            Log.i(TAG, "Deleting leftover import file ${leftover.name}")
            if (!leftover.delete()) Log.w(TAG, "Cannot delete leftover import file ${leftover.name}")
        }
    }

    /** Bounded while copying: the announced size of a shared file can lie. */
    private fun copyBounded(input: InputStream, output: OutputStream, shouldContinue: () -> Boolean) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            if (!shouldContinue()) throw CancellationException("Copy cancelled")
            val read = providerCall { input.read(buffer) }
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
