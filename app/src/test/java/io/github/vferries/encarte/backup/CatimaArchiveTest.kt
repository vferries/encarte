package io.github.vferries.encarte.backup

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

class CatimaArchiveTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val archive = CatimaArchive()
    private val csv = CatimaCsv.write(
        listOf(CatimaCard(id = 1, store = "Shop", cardId = "42"), CatimaCard(id = 2, store = "Other", cardId = "43"))
    )
    private val frontBytes = byteArrayOf(1, 2, 3)

    private fun written(password: CharArray? = null, extraImages: List<ArchiveImage> = emptyList()): File {
        val file = tmp.newFile("backup.zip")
        val images = listOf(ArchiveImage(CatimaImageRef(1, ImageSide.FRONT)) { it.write(frontBytes) }) + extraImages
        archive.write(FileOutputStream(file), csv, images, password)
        return file
    }

    @Test
    fun plainArchiveRoundTrips() {
        val file = written()

        assertEquals(listOf("Shop", "Other"), archive.readCards(file, null).map { it.store })
        val images = mutableMapOf<CatimaImageRef, ByteArray>()
        archive.forEachImage(file, null) { ref, input -> images[ref] = input.readBytes() }
        assertEquals(setOf(CatimaImageRef(1, ImageSide.FRONT)), images.keys)
        assertArrayEquals(frontBytes, images.values.single())
    }

    @Test
    fun entryNamesFollowCatima() {
        assertEquals("card_12_front.png", CatimaImageRef(12, ImageSide.FRONT).entryName)
        assertEquals("card_3_back.png", CatimaImageRef(3, ImageSide.BACK).entryName)
    }

    @Test
    fun encryptedArchiveNeedsTheRightPassword() {
        val file = written("secret".toCharArray())

        assertThrows(PasswordRequiredException::class.java) { archive.readCards(file, null) }
        assertThrows(WrongPasswordException::class.java) { archive.readCards(file, "wrong".toCharArray()) }
        assertEquals(2, archive.readCards(file, "secret".toCharArray()).size)
        var images = 0
        archive.forEachImage(file, "secret".toCharArray()) { _, _ -> images++ }
        assertEquals(1, images)
    }

    @Test
    fun bareCsvFileIsAccepted() {
        val file = tmp.newFile("catima.csv").apply { writeText(csv) }

        assertEquals(2, archive.readCards(file, null).size)
        archive.forEachImage(file, null) { _, _ -> error("a bare CSV has no images") }
    }

    @Test
    fun unexpectedEntriesAreIgnoredAndIconsAreReported() {
        val file = tmp.newFile("extra.zip")
        val icon = ArchiveImage(CatimaImageRef(2, ImageSide.ICON)) { it.write(frontBytes) }
        archive.write(FileOutputStream(file), csv, listOf(icon), null)
        ZipFile(file).use { it.addStream("hello".byteInputStream(), ZipParameters().apply { fileNameInZip = "notes.txt" }) }

        val refs = mutableListOf<CatimaImageRef>()
        archive.forEachImage(file, null) { ref, _ -> refs += ref }

        assertEquals(listOf(CatimaImageRef(2, ImageSide.ICON)), refs)
        assertEquals(2, archive.readCards(file, null).size)
    }

    @Test
    fun archiveWithoutCsvIsInvalid() {
        val file = File(tmp.root, "no-csv.zip")
        ZipFile(file).use { it.addStream("x".byteInputStream(), ZipParameters().apply { fileNameInZip = "notes.txt" }) }

        assertThrows(CatimaFormatException::class.java) { archive.readCards(file, null) }
    }

    @Test
    fun guardsRejectOversizedArchives() {
        val file = written()

        assertThrows(CatimaFormatException::class.java) { CatimaArchive(maxCards = 1).readCards(file, null) }
        assertThrows(CatimaFormatException::class.java) { CatimaArchive(maxUncompressedBytes = 10).readCards(file, null) }
    }
}
