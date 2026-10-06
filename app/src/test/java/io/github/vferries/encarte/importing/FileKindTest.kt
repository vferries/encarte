package io.github.vferries.encarte.importing

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

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
    fun randomBytesADamagedZipAndAnEmptyFileAreUnrecognised() {
        assertEquals(FileKind.UNRECOGNIZED, kindOf(ByteArray(64) { (it * 37).toByte() }))
        assertEquals(FileKind.UNRECOGNIZED, kindOf(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2, 3)))
        assertEquals(FileKind.UNRECOGNIZED, kindOf(ByteArray(0)))
    }
}
