package io.github.vferries.encarte.importing

import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

private const val TAG = "FileKind"
private val PDF_SIGNATURE = "%PDF-".toByteArray(Charsets.US_ASCII)
private val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

enum class FileKind { PDF, PASS, IMAGE, UNRECOGNIZED }

/** From the content, never from the announced MIME type: sources often label a pass as anything. Blocking. */
fun detectFileKind(file: File): FileKind = try {
    val head = file.head(PDF_SIGNATURE.size)
    when {
        head.startsWith(PDF_SIGNATURE) -> FileKind.PDF
        head.startsWith(ZIP_SIGNATURE) && ZipFile(file).use { it.getEntry("pass.json") != null } -> FileKind.PASS
        isImage(file) -> FileKind.IMAGE
        else -> FileKind.UNRECOGNIZED
    }
} catch (e: IOException) {
    // A damaged ZIP lands here too.
    Log.w(TAG, "Cannot identify the file: ${e.message}")
    FileKind.UNRECOGNIZED
}

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

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private fun isImage(file: File): Boolean {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    return bounds.outWidth > 0 && bounds.outHeight > 0
}
