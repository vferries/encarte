package io.github.vferries.encarte.backup

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

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

        assertEquals(listOf("Shop", "Other"), archive.read(file, null).cards.map { it.store })
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

        assertThrows(PasswordRequiredException::class.java) { archive.read(file, null).cards }
        assertThrows(WrongPasswordException::class.java) { archive.read(file, "wrong".toCharArray()).cards }
        assertEquals(2, archive.read(file, "secret".toCharArray()).cards.size)
        var images = 0
        archive.forEachImage(file, "secret".toCharArray()) { _, _ -> images++ }
        assertEquals(1, images)
    }

    @Test
    fun bareCsvFileIsAccepted() {
        val file = tmp.newFile("catima.csv").apply { writeText(csv) }

        assertEquals(2, archive.read(file, null).cards.size)
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
        assertEquals(2, archive.read(file, null).cards.size)
    }

    @Test
    fun archiveWithoutCsvIsInvalid() {
        val file = File(tmp.root, "no-csv.zip")
        ZipFile(file).use { it.addStream("x".byteInputStream(), ZipParameters().apply { fileNameInZip = "notes.txt" }) }

        assertThrows(CatimaFormatException::class.java) { archive.read(file, null).cards }
    }

    @Test
    fun guardsRejectTooManyCards() {
        val file = written()

        assertThrows(CatimaFormatException::class.java) { CatimaArchive(maxCards = 1).read(file, null).cards }
    }

    private fun bareCsv(groups: List<String>, links: List<CatimaGroupLink> = emptyList()): File =
        tmp.newFile().apply { writeText(CatimaCsv.write(listOf(CatimaCard(id = 1, store = "Shop", cardId = "42")), groups, links)) }

    @Test
    fun guardsRejectTooManyGroups() {
        val names = (1..1_000).map { "Group $it" }

        assertEquals(1_000, archive.read(bareCsv(names, listOf(CatimaGroupLink(1, "Group 1"))), null).groups.size)
        assertThrows(CatimaFormatException::class.java) { archive.read(bareCsv(names + "Group 1001"), null) }
    }

    @Test
    fun groupsNamedOnlyByLinksCountTowardsTheGroupLimit() {
        val names = (1..1_000).map { "Group $it" }

        // A link to a group missing from the groups table creates that group too.
        assertThrows(CatimaFormatException::class.java) {
            archive.read(bareCsv(names, listOf(CatimaGroupLink(1, "Group 1001"))), null)
        }
    }

    @Test
    fun guardsRejectTooManyGroupLinks() {
        val links = (1..100_000).map { CatimaGroupLink(it, "Courses") }

        assertEquals(100_000, archive.read(bareCsv(listOf("Courses"), links), null).links.size)
        assertThrows(CatimaFormatException::class.java) {
            archive.read(bareCsv(listOf("Courses"), links + CatimaGroupLink(100_001, "Courses")), null)
        }
    }

    @Test
    fun entryOverTheLimitIsRejected() {
        val big = ArchiveImage(CatimaImageRef(2, ImageSide.BACK)) { it.write(ByteArray(600)) }
        val file = written("secret".toCharArray(), extraImages = listOf(big))
        val small = CatimaArchive(maxEntryBytes = 500)

        assertEquals(2, small.read(file, "secret".toCharArray()).cards.size)
        val e = assertThrows(CatimaFormatException::class.java) {
            small.forEachImage(file, "secret".toCharArray()) { _, input -> input.readBytes() }
        }
        assertEquals("Archive entry too large", e.message)
        assertThrows(CatimaFormatException::class.java) {
            CatimaArchive(maxEntryBytes = 10).read(file, "secret".toCharArray()).cards
        }
    }

    @Test
    fun manyEntriesUnderTheLimitAreAcceptedWhateverTheirTotal() {
        val entries = (2..6).map { id -> ArchiveImage(CatimaImageRef(id, ImageSide.FRONT)) { it.write(ByteArray(600)) } }
        val file = written(extraImages = entries)
        val small = CatimaArchive(maxEntryBytes = 1024)

        assertEquals(2, small.read(file, null).cards.size)
        val sizes = mutableListOf<Int>()
        small.forEachImage(file, null) { _, input -> sizes += input.readBytes().size }
        assertEquals(listOf(3, 600, 600, 600, 600, 600), sizes)
        assertTrue(sizes.sum() > 2 * 1024)
    }

    @Test
    fun bareCsvOverTheLimitIsRejected() {
        val file = tmp.newFile("catima.csv").apply { writeText(csv) }
        val csvSize = file.length()

        assertEquals(2, CatimaArchive(maxCsvBytes = csvSize).read(file, null).cards.size)
        assertThrows(CatimaFormatException::class.java) {
            CatimaArchive(maxCsvBytes = csvSize - 1).read(file, null).cards
        }
    }

    @Test
    fun callbackIoFailureIsNotReportedAsWrongPassword() {
        val file = written("secret".toCharArray())

        val e = assertThrows(IOException::class.java) {
            archive.forEachImage(file, "secret".toCharArray()) { _, _ -> throw IOException("disk full") }
        }
        assertEquals("disk full", e.message)
    }
}
