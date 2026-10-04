package io.github.vferries.encarte.core.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "ImageStore"

/** Card photos as JPEG files referenced by name. All methods block: call them off the main thread. */
class ImageStore(private val imagesDir: File, private val stagingDir: File) {

    fun file(name: String): File = File(imagesDir, name)

    fun exists(name: String): Boolean = file(name).isFile

    /** Decodes, rotates, downscales and saves an image; returns its new file name. */
    fun save(open: () -> InputStream): String = writeJpeg(imagesDir, open)

    /** Same as [save] but into the staging area, for imports that may still be rolled back. */
    fun stage(input: InputStream): String {
        val bytes = input.readBytes()
        return writeJpeg(stagingDir) { bytes.inputStream() }
    }

    fun commitStaged(names: Collection<String>): List<String> {
        imagesDir.mkdirs()
        return names.map { name ->
            val source = File(stagingDir, name)
            val target = file(name)
            if (!source.renameTo(target)) {
                source.copyTo(target, overwrite = true)
                source.delete()
            }
            name
        }
    }

    fun clearStaging() {
        stagingDir.listFiles()?.forEach { it.delete() }
    }

    fun delete(name: String) {
        if (!file(name).delete()) Log.w(TAG, "Image $name was already gone")
    }

    fun deleteAll(names: Collection<String>) = names.forEach(::delete)

    fun deleteOrphans(referenced: Set<String>) {
        imagesDir.listFiles()
            ?.filter { it.name !in referenced }
            ?.forEach { orphan ->
                Log.i(TAG, "Deleting orphan image ${orphan.name}")
                orphan.delete()
            }
    }

    /** Catima names archive images *.png, so exports re-encode stored JPEGs as PNG. */
    fun writePng(name: String, out: OutputStream) {
        val bitmap = BitmapFactory.decodeFile(file(name).path) ?: throw IOException("Cannot decode image $name")
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("Cannot encode image $name")
    }

    private fun writeJpeg(dir: File, open: () -> InputStream): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not a decodable image")

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(max(bounds.outWidth, bounds.outHeight))
        }
        val decoded = open().use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Not a decodable image")
        val bitmap = decoded.scaledToMaxSide(MAX_SIDE).rotatedFor(exifOrientation(open))

        dir.mkdirs()
        val name = "${UUID.randomUUID()}.jpg"
        FileOutputStream(File(dir, name)).use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) throw IOException("Cannot encode JPEG")
        }
        return name
    }

    private fun sampleSizeFor(longestSide: Int): Int {
        var sampleSize = 1
        while (longestSide / (sampleSize * 2) >= MAX_SIDE) sampleSize *= 2
        return sampleSize
    }

    private fun exifOrientation(open: () -> InputStream): Int = try {
        open().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    } catch (e: IOException) {
        Log.w(TAG, "Cannot read EXIF orientation, assuming normal", e)
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun Bitmap.scaledToMaxSide(maxSide: Int): Bitmap {
        val longest = max(width, height)
        if (longest <= maxSide) return this
        val ratio = maxSide.toFloat() / longest
        return scale((width * ratio).roundToInt(), (height * ratio).roundToInt(), true)
    }

    private fun Bitmap.rotatedFor(orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return this
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(degrees) }, true)
    }

    companion object {
        const val MAX_SIDE = 1600
        private const val JPEG_QUALITY = 85
    }
}
