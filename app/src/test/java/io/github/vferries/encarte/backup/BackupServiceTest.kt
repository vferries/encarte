package io.github.vferries.encarte.backup

import android.graphics.Bitmap
import androidx.room3.useWriterConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.core.data.EncarteDatabase
import io.github.vferries.encarte.core.data.ImageStore
import io.github.vferries.encarte.testing.inMemoryDatabase
import io.github.vferries.encarte.testing.testCard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.lingala.zip4j.ZipFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupServiceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val labels = ImportLabels("Valid from: %1\$s", "Expires: %1\$s", "Balance: %1\$s", "%1\$s points", Locale.US)
    private val clock = Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC)
    private val db = inMemoryDatabase()
    private val images by lazy { ImageStore(File(tmp.root, "images"), File(tmp.root, "staging")) }
    private val service by lazy { serviceFor(db, images) }
    private val archive = CatimaArchive()

    @After
    fun tearDown() = db.close()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun serviceFor(database: EncarteDatabase, store: ImageStore) =
        BackupService(database, store, archive, File(tmp.root, "work"), { labels }, clock, UnconfinedTestDispatcher())

    private fun fixtureArchive(password: CharArray? = null, csv: String = fixture("catima_v2.csv")): File {
        val file = File(tmp.root, "backup-${System.nanoTime()}.zip")
        val front = ArchiveImage(CatimaImageRef(4, ImageSide.FRONT)) { it.write(png()) }
        val icon = ArchiveImage(CatimaImageRef(4, ImageSide.ICON)) { it.write(png()) }
        archive.write(FileOutputStream(file), csv, listOf(front, icon), password)
        return file
    }

    private fun fixture(name: String) = javaClass.getResource("/catima/$name")!!.readText()

    private fun png() = ByteArrayOutputStream().also {
        Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()

    @Test
    fun importsCatimaFixtureWithImages() = runTest {
        val result = service.import(fixtureArchive(), null)

        assertEquals(ImportResult.Success(imported = 8, skippedDuplicates = 0), result)
        val cards = db.cardDao().getAll()
        val pharmacy = cards.single { it.storeName == "Pharmacy" }
        assertNotNull(pharmacy.frontImage)
        assertTrue(images.exists(pharmacy.frontImage!!))
        assertNull("icons are ignored", pharmacy.backImage)
        assertTrue(cards.single { it.storeName == "Shoe Store" }.note.contains("Balance: 12.50 EUR"))
        assertTrue(cards.all { it.color ushr 24 == 0xFF })
    }

    @Test
    fun secondImportOfSameArchiveSkipsEverything() = runTest {
        val file = fixtureArchive()
        service.import(file, null)

        assertEquals(ImportResult.Success(imported = 0, skippedDuplicates = 8), service.import(file, null))
        assertEquals(8, db.cardDao().getAll().size)
    }

    @Test
    fun duplicatesInsideOneArchiveAreImportedOnce() = runTest {
        val csv = CatimaCsv.write(
            listOf(CatimaCard(id = 1, store = "Shop", cardId = "42"), CatimaCard(id = 2, store = "shop", cardId = "42"))
        )

        val result = service.import(fixtureArchive(csv = csv), null)

        assertEquals(ImportResult.Success(imported = 1, skippedDuplicates = 1), result)
    }

    @Test
    fun wrongPasswordThenRightPasswordImportsWithoutRepicking() = runTest {
        val picked = fixtureArchive("secret".toCharArray())
        val work = service.copyToWorkFile { picked.inputStream() }

        assertEquals(ImportResult.PasswordRequired, service.import(work, null))
        assertEquals(ImportResult.WrongPassword, service.import(work, "nope".toCharArray()))
        assertEquals(ImportResult.Success(8, 0), service.import(work, "secret".toCharArray()))

        service.discard(work)
        assertTrue(!work.exists())
    }

    @Test
    fun newerFormatIsReported() = runTest {
        val file = File(tmp.root, "v3.csv").apply { writeText("3\r\n\r\n_id\r\n") }

        assertEquals(ImportResult.UnsupportedVersion(3), service.import(file, null))
    }

    @Test
    fun garbageIsInvalid() = runTest {
        val file = File(tmp.root, "garbage.bin").apply { writeBytes(byteArrayOf(0, 1, 2, 3)) }

        assertEquals(ImportResult.Invalid, service.import(file, null))
    }

    @Test
    fun corruptImageIsSkippedButCardIsImported() = runTest {
        val file = File(tmp.root, "corrupt.zip")
        val csv = CatimaCsv.write(listOf(CatimaCard(id = 1, store = "Shop", cardId = "42")))
        archive.write(FileOutputStream(file), csv, listOf(ArchiveImage(CatimaImageRef(1, ImageSide.FRONT)) { it.write(byteArrayOf(9, 9)) }), null)

        assertEquals(ImportResult.Success(1, 0), service.import(file, null))
        assertNull(db.cardDao().getAll().single().frontImage)
    }

    @Test
    fun exportThenImportIntoEmptyDatabaseRoundTrips() = runTest {
        val front = images.save { png().inputStream() }
        db.cardDao().insert(testCard("Fnac", cardNumber = "4006381333931", frontImage = front, isFavorite = true))
        db.cardDao().insert(testCard("Käse", cardNumber = "Käseschnitte"))
        val exported = ByteArrayOutputStream()

        assertEquals(ExportResult.Success(2), service.export({ exported }, "pw".toCharArray()))

        val otherDb = inMemoryDatabase()
        val otherImages = ImageStore(File(tmp.root, "images2"), File(tmp.root, "staging2"))
        val other = serviceFor(otherDb, otherImages)
        val file = File(tmp.root, "exported.zip").apply { writeBytes(exported.toByteArray()) }
        assertEquals(ImportResult.Success(2, 0), other.import(file, "pw".toCharArray()))
        val fnac = otherDb.cardDao().getAll().single { it.storeName == "Fnac" }
        assertEquals("4006381333931", fnac.cardNumber)
        assertTrue(fnac.isFavorite)
        assertTrue(otherImages.exists(fnac.frontImage!!))
        otherDb.close()
    }

    @Test
    fun exportContainsOnlyCatimaEntries() = runTest {
        val front = images.save { png().inputStream() }
        val id = db.cardDao().insert(testCard("Fnac", frontImage = front))
        val exported = ByteArrayOutputStream()

        service.export({ exported }, null)

        val file = File(tmp.root, "plain.zip").apply { writeBytes(exported.toByteArray()) }
        val names = ZipFile(file).use { zip -> zip.fileHeaders.map { it.fileName }.toSet() }
        assertEquals(setOf("catima.csv", "card_${id}_front.png"), names)
    }

    @Test
    fun failedTransactionLeavesNoCardsAndNoImages() = runTest {
        db.useWriterConnection { connection ->
            connection.usePrepared("CREATE TRIGGER fail_insert BEFORE INSERT ON cards BEGIN SELECT RAISE(ABORT, 'boom'); END") { it.step() }
        }

        val result = service.import(fixtureArchive(), null)

        assertEquals(ImportResult.IoError, result)
        assertTrue(db.cardDao().getAll().isEmpty())
        assertTrue(File(tmp.root, "images").walkTopDown().none { it.isFile })
    }
}
