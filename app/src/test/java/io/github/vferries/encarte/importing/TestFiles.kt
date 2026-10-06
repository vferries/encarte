package io.github.vferries.encarte.importing

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Small files of each kind, built in the tests. */
object TestFiles {
    fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    fun pass(passJson: String = """{"formatVersion": 1, "organizationName": "Cinéma"}"""): ByteArray =
        zip(mapOf("pass.json" to passJson.toByteArray(), "icon.png" to png()))

    /** Needs Robolectric's native graphics to encode. */
    fun png(): ByteArray = ByteArrayOutputStream().also {
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()

    val pdf: ByteArray = "%PDF-1.7\n%âãÏÓ\n".toByteArray(Charsets.ISO_8859_1)
}
