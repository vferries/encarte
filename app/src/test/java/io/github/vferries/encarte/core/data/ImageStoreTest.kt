package io.github.vferries.encarte.core.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: ImageStore
    private lateinit var imagesDir: File
    private lateinit var stagingDir: File

    @Before
    fun setUp() {
        imagesDir = File(tmp.root, "images")
        stagingDir = File(tmp.root, "staging")
        store = ImageStore(imagesDir, stagingDir)
    }

    @Test
    fun largeImageIsDownscaledToMaxSide() {
        val source = jpegFile(4000, 1000)

        val name = store.save { source.inputStream() }

        val bounds = bounds(store.file(name))
        assertEquals(1600, bounds.first)
        assertEquals(400, bounds.second)
        assertTrue(name.endsWith(".jpg"))
    }

    @Test
    fun smallImageIsNotUpscaled() {
        val name = store.save { jpegFile(100, 50).inputStream() }

        assertEquals(100 to 50, bounds(store.file(name)))
    }

    @Test
    fun exifRotationIsApplied() {
        val source = jpegFile(400, 200)
        ExifInterface(source.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }

        val name = store.save { source.inputStream() }

        assertEquals(200 to 400, bounds(store.file(name)))
    }

    @Test
    fun notAnImageThrows() {
        assertThrows(IOException::class.java) { store.save { "not an image".byteInputStream() } }
    }

    @Test
    fun stagedImagesMoveOnCommitAndStagingClears() {
        val staged = store.stage(jpegFile(10, 10).inputStream())
        assertTrue(File(stagingDir, staged).isFile)

        val committed = store.commitStaged(listOf(staged))

        assertEquals(listOf(staged), committed)
        assertTrue(store.exists(staged))
        assertFalse(File(stagingDir, staged).exists())
        store.stage(jpegFile(10, 10).inputStream())
        store.clearStaging()
        assertTrue(stagingDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun deleteOrphansKeepsReferencedFiles() {
        val kept = store.save { jpegFile(10, 10).inputStream() }
        val orphan = store.save { jpegFile(10, 10).inputStream() }

        store.deleteOrphans(setOf(kept))

        assertTrue(store.exists(kept))
        assertFalse(store.exists(orphan))
    }

    @Test
    fun writePngProducesPng() {
        val name = store.save { jpegFile(10, 10).inputStream() }
        val out = ByteArrayOutputStream()

        store.writePng(name, out)

        val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        assertArrayEquals(pngSignature, out.toByteArray().copyOf(4))
    }

    private fun jpegFile(width: Int, height: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        return tmp.newFile().apply { outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
    }

    private fun bounds(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        return options.outWidth to options.outHeight
    }
}
