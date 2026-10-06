package io.github.vferries.encarte.importing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

class ImportFilesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dir by lazy { File(tmp.root, "imports") }
    private val files by lazy { ImportFiles(dir, maxBytes = 10) }

    private fun leftovers() = dir.listFiles().orEmpty().map { it.name }

    @Test
    fun aCopyGetsARandomUuidName() {
        val file = files.copy { "%PDF-1.7".byteInputStream() }

        assertTrue(ImportFiles.isFileName(file.name))
        assertEquals(dir, file.parentFile)
        assertArrayEquals("%PDF-1.7".toByteArray(), file.readBytes())
    }

    @Test
    fun aFileAtTheCapIsCopied() {
        assertEquals(10L, files.copy { ByteArray(10).inputStream() }.length())
    }

    @Test
    fun aFileOverTheCapLeavesNothing() {
        assertThrowsIo<FileTooLargeException> { files.copy { ByteArray(11).inputStream() } }

        assertEquals(emptyList<String>(), leftovers())
    }

    @Test
    fun aCopyFailingHalfwayLeavesNothing() {
        val failing = object : InputStream() {
            private var sent = 0
            override fun read(): Int = if (sent++ < 4) 'x'.code else throw IOException("connection lost")
        }

        assertThrowsIo<IOException> { files.copy { failing } }

        assertEquals(emptyList<String>(), leftovers())
    }

    @Test
    fun aCancelledCopyStopsReadingAndLeavesNothing() {
        var reads = 0
        val endless = object : InputStream() {
            override fun read(): Int = 'x'.code
            override fun read(b: ByteArray, off: Int, len: Int): Int = 1.also { reads++; b[off] = 'x'.code.toByte() }
        }

        assertThrows(CancellationException::class.java) { files.copy(shouldContinue = { reads < 3 }) { endless } }

        assertEquals(3, reads)
        assertEquals(emptyList<String>(), leftovers())
    }

    @Test
    fun aCancelledCopyOrNullThrowsInsteadOfReturningNull() {
        assertThrows(CancellationException::class.java) { files.copyOrNull(shouldContinue = { false }) { "x".byteInputStream() } }

        assertEquals(emptyList<String>(), leftovers())
    }

    @Test
    fun onlyUuidNamesAreAccepted() {
        assertTrue(ImportFiles.isFileName(UUID.randomUUID().toString()))
        for (name in listOf("../x", "/data/data/io.github.vferries.encarte/databases/encarte.db", "", "abc")) {
            assertFalse("$name", ImportFiles.isFileName(name))
        }
        assertNull(files.resolve("../x"))
        val name = UUID.randomUUID().toString()
        assertEquals(File(dir, name), files.resolve(name))
    }

    @Test
    fun theStartupCleanupDeletesOnlyAnEarlierProcessCopies() {
        val earlier = ImportFiles(dir).copy { "old".byteInputStream() }
        val current = files.copy { "new".byteInputStream() }

        files.clearLeftovers()

        assertFalse(earlier.exists())
        assertTrue("a copy being handed over survives", current.exists())
    }

    @Test
    fun deletingTwiceIsHarmless() {
        val file = files.copy { "x".byteInputStream() }

        files.delete(file)
        files.delete(file)

        assertFalse(file.exists())
    }

    private inline fun <reified E : IOException> assertThrowsIo(block: () -> Unit) {
        try {
            block()
        } catch (e: IOException) {
            assertTrue("${e.javaClass}", e is E)
            return
        }
        throw AssertionError("Expected ${E::class.simpleName}")
    }
}
