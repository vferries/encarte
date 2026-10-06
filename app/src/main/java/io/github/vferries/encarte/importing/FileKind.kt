package io.github.vferries.encarte.importing

import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

private const val TAG = "FileKind"
internal val PDF_SIGNATURE = "%PDF-".toByteArray(Charsets.US_ASCII)
internal val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

enum class FileKind { PDF, PASS, IMAGE, UNRECOGNIZED }

/** From the content, never from the announced MIME type: sources often label a pass as anything. Blocking. */
fun detectFileKind(file: File): FileKind = detectFileKind(file, ::hasRootPassJson)

/** [hasPassJson] is a parameter so the tests can simulate what Android's ZipFile throws and the JVM's does not. */
internal fun detectFileKind(file: File, hasPassJson: (File) -> Boolean): FileKind = try {
    val head = file.head(PDF_SIGNATURE.size)
    when {
        head.startsWith(PDF_SIGNATURE) -> FileKind.PDF
        head.startsWith(ZIP_SIGNATURE) && hasPassJson(file) -> FileKind.PASS
        isImage(file) -> FileKind.IMAGE
        else -> FileKind.UNRECOGNIZED
    }
} catch (e: IOException) {
    // A damaged ZIP lands here too. Never the message: a hostile ZIP puts its entry names in it.
    Log.w(TAG, "Cannot identify the file: ${e.javaClass.simpleName}")
    FileKind.UNRECOGNIZED
} catch (e: IllegalArgumentException) {
    // Android 8-13's ZipFile throws this, not ZipException, on a malformed entry name or comment.
    Log.w(TAG, "Cannot identify the file: ${e.javaClass.simpleName}")
    FileKind.UNRECOGNIZED
}

private fun hasRootPassJson(file: File): Boolean = ZipFile(file).use { it.getEntry("pass.json") != null }

private fun File.head(size: Int): ByteArray = inputStream().use { input ->
    val buffer = ByteArray(size)
    var filled = 0
    while (filled < size) {
        val read = input.read(buffer, filled, size - filled)
        if (read < 0) break
        filled += read
    }
    buffer.copyOf(filled)
}

internal fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private fun isImage(file: File): Boolean {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    return bounds.outWidth > 0 && bounds.outHeight > 0
}
