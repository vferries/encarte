package io.github.vferries.encarte.importing

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FileKindTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun kindOf(bytes: ByteArray) = detectFileKind(tmp.newFile().apply { writeBytes(bytes) })

    @Test
    fun recognisesEachKindFromItsContent() {
        assertEquals(FileKind.PDF, kindOf(TestFiles.pdf))
        assertEquals(FileKind.PASS, kindOf(TestFiles.pass()))
        assertEquals(FileKind.IMAGE, kindOf(TestFiles.png()))
    }

    @Test
    fun aZipWithoutRootPassJsonIsNotAPass() {
        assertEquals(FileKind.UNRECOGNIZED, kindOf(TestFiles.zip(mapOf("loyalty_programs.csv" to "x".toByteArray()))))
        assertEquals(FileKind.UNRECOGNIZED, kindOf(TestFiles.zip(mapOf("folder/pass.json" to "{}".toByteArray()))))
    }

    @Test
    fun aMalformedEntryCommentDoesNotEscape() {
        val zip = TestFiles.zip(mapOf("pass.json" to "{}".toByteArray()))
        // Central directory comment of the entry: not UTF-8. Android 8-13 throws IllegalArgumentException; JDK 21 reads it.
        val comment = byteArrayOf(0xC3.toByte(), 0x28)
        val file = tmp.newFile().apply { writeBytes(withEntryComment(zip, comment)) }

        val kind = detectFileKind(file)

        assertTrue(kind == FileKind.PASS || kind == FileKind.UNRECOGNIZED)
    }

    @Test
    fun anIllegalArgumentFromTheZipReaderIsUnrecognisedAndLoggedWithoutItsMessage() {
        val file = tmp.newFile().apply { writeBytes(TestFiles.zip(mapOf("pass.json" to "{}".toByteArray()))) }

        val kind = detectFileKind(file) { throw IllegalArgumentException("MALFORMED secret-entry-name") }

        assertEquals(FileKind.UNRECOGNIZED, kind)
        assertTrue(ShadowLog.getLogs().none { it.msg.contains("secret-entry-name") })
    }

    private fun withEntryComment(zip: ByteArray, comment: ByteArray): ByteArray {
        // Central directory header (0x02014b50): comment length at offset 32, comment after the name.
        val cd = (0..zip.size - 4).first { zip[it] == 0x50.toByte() && zip[it + 1] == 0x4B.toByte() && zip[it + 2] == 1.toByte() && zip[it + 3] == 2.toByte() }
        val nameLen = (zip[cd + 28].toInt() and 0xFF) or ((zip[cd + 29].toInt() and 0xFF) shl 8)
        val extraLen = (zip[cd + 30].toInt() and 0xFF) or ((zip[cd + 31].toInt() and 0xFF) shl 8)
        val at = cd + 46 + nameLen + extraLen
        val patched = zip.copyOfRange(0, at) + comment + zip.copyOfRange(at, zip.size)
        patched[cd + 32] = comment.size.toByte()
        patched[cd + 33] = 0
        // End of central directory is unchanged apart from the directory's size, at offset 12 from its signature.
        val eocd = (patched.size - 22 downTo 0).first { patched[it] == 0x50.toByte() && patched[it + 1] == 0x4B.toByte() && patched[it + 2] == 5.toByte() && patched[it + 3] == 6.toByte() }
        val size = (patched[eocd + 12].toInt() and 0xFF) + comment.size
        patched[eocd + 12] = size.toByte()
        return patched
    }

    @Test
    fun randomBytesADamagedZipAndAnEmptyFileAreUnrecognised() {
        assertEquals(FileKind.UNRECOGNIZED, kindOf(ByteArray(64) { (it * 37).toByte() }))
        assertEquals(FileKind.UNRECOGNIZED, kindOf(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2, 3)))
        assertEquals(FileKind.UNRECOGNIZED, kindOf(ByteArray(0)))
    }
}
