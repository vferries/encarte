package io.github.vferries.encarte.backup

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room3.useWriterConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.core.data.Card
import io.github.vferries.encarte.core.data.EncarteDatabase
import io.github.vferries.encarte.core.data.GroupNameResult
import io.github.vferries.encarte.core.data.GroupRepository
import io.github.vferries.encarte.core.data.ImageStore
import io.github.vferries.encarte.testing.inMemoryDatabase
import io.github.vferries.encarte.testing.testCard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.lingala.zip4j.ZipFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupServiceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val labels = ImportLabels("Valid from: %1\$s", "Balance: %1\$s", "%1\$s points", Locale.US)
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

    private fun png(width: Int = 4, height: Int = 4) = ByteArrayOutputStream().also {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
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
    fun encryptedExportThenImportIntoEmptyDatabaseRoundTrips() = runTest { assertRoundTrip("pw".toCharArray()) }

    @Test
    fun plainExportThenImportIntoEmptyDatabaseRoundTrips() = runTest { assertRoundTrip(password = null) }

    private suspend fun assertRoundTrip(password: CharArray?) {
        val front = images.save { png(width = 8, height = 4).inputStream() }
        val back = images.save { png(width = 4, height = 8).inputStream() }
        val originals = listOf(
            testCard(
                "Fnac", cardNumber = "4006381333931", barcodeFormat = BarcodeFormat.EAN_13, isFavorite = true,
                frontImage = front, backImage = back, lastUsedAt = Instant.parse("2026-09-30T08:15:42.123Z"),
            ).copy(note = "Gold member\nsince 2020, \"VIP\"", color = 0xFF123456.toInt()),
            testCard("Decathlon", cardNumber = "1234 5678", barcodeFormat = BarcodeFormat.CODE_128)
                .copy(barcodeValue = "X-12345678", expiresOn = LocalDate.of(2027, 3, 12), isArchived = true),
            testCard("Käse", cardNumber = "Käseschnitte", barcodeFormat = BarcodeFormat.QR_CODE)
                .copy(note = "Crème brûlée ☕"),
        )
        originals.forEach { db.cardDao().insert(it) }
        val exported = ByteArrayOutputStream()

        assertEquals(ExportResult.Success(3), service.export({ exported }, password?.copyOf()))

        val otherDb = inMemoryDatabase()
        try {
            val otherImages = ImageStore(File(tmp.root, "images2"), File(tmp.root, "staging2"))
            val file = File(tmp.root, "exported.zip").apply { writeBytes(exported.toByteArray()) }
            assertEquals(ImportResult.Success(3, 0), serviceFor(otherDb, otherImages).import(file, password))
            val imported = otherDb.cardDao().getAll().associateBy { it.storeName }
            for (original in originals) {
                val copy = imported.getValue(original.storeName)
                assertEquals(original.comparable(), copy.comparable())
            }
            val fnac = imported.getValue("Fnac")
            assertEquals(8 to 4, otherImages.size(fnac.frontImage!!))
            assertEquals(4 to 8, otherImages.size(fnac.backImage!!))
        } finally {
            otherDb.close()
        }
    }

    /** What a backup must preserve: ids, creation dates and image file names are the importer's own. */
    private fun Card.comparable() = copy(
        id = 0, createdAt = Instant.EPOCH, frontImage = null, backImage = null,
        lastUsedAt = lastUsedAt?.truncatedTo(ChronoUnit.SECONDS),
    )

    private fun ImageStore.size(name: String): Pair<Int, Int> {
        assertTrue("image $name exists", exists(name))
        val bitmap = BitmapFactory.decodeFile(file(name).path)
        return bitmap.width to bitmap.height
    }

    @Test
    fun revokedDestinationIsAnExportError() = runTest {
        db.cardDao().insert(testCard("Fnac"))

        assertEquals(ExportResult.IoError, service.export({ throw SecurityException("permission revoked") }, null))
    }

    @Test
    fun databaseFailureIsAnExportError() = runTest {
        db.useWriterConnection { connection -> connection.usePrepared("DROP TABLE cards") { it.step() } }

        assertEquals(ExportResult.IoError, service.export({ ByteArrayOutputStream() }, null))
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

    @Test
    fun importsCatimaExpiryAndArchive() = runTest {
        // The upstream fixture has no archived card: archive Department Store, which has an expiry date.
        val original = fixture("catima_v2.csv")
        val row = "2,Department Store,,,1618041729,0,,A,,,,-9977996,0,0,"
        val csv = original.replace("${row}0", "${row}1")
        assertNotEquals("the fixture row was rewritten", original, csv)

        service.import(fixtureArchive(csv = csv), null)

        val cards = db.cardDao().getAll()
        val store = cards.single { it.storeName == "Department Store" }
        // 1618041729 read as epoch milliseconds, in the test clock's UTC zone.
        assertEquals(LocalDate.of(1970, 1, 19), store.expiresOn)
        assertTrue(store.isArchived)
        assertFalse(cards.single { it.storeName == "Pharmacy" }.isArchived)
    }

    private suspend fun membershipsByStore(database: EncarteDatabase): Map<String, Set<String>> {
        val stores = database.cardDao().getAll().associate { it.id to it.storeName }
        val names = database.groupDao().getAll().associate { it.id to it.name }
        return database.groupDao().getAllMemberships()
            .groupBy({ stores.getValue(it.cardId) }, { names.getValue(it.groupId) })
            .mapValues { (_, groups) -> groups.toSet() }
    }

    @Test
    fun importsCatimaGroups() = runTest {
        service.import(fixtureArchive(), null)

        assertEquals(setOf("Health", "Food", "Fashion"), db.groupDao().getAll().map { it.name }.toSet())
        assertEquals(
            mapOf(
                "Pharmacy" to setOf("Health"), "Grocery Store" to setOf("Food"), "Restaurant" to setOf("Food"),
                "Clothes Store" to setOf("Fashion"), "Shoe Store" to setOf("Fashion"),
            ),
            membershipsByStore(db),
        )
    }

    @Test
    fun importedGroupsJoinExistingGroupsOfTheSameName() = runTest {
        val food = (GroupRepository(db).create("food") as GroupNameResult.Saved).id

        service.import(fixtureArchive(), null)

        val groups = db.groupDao().getAll()
        assertEquals(3, groups.size)
        assertEquals("food", groups.single { it.id == food }.name)
        assertEquals(setOf("food"), membershipsByStore(db).getValue("Restaurant"))
    }

    @Test
    fun duplicatesKeepTheirCurrentGroups() = runTest {
        val file = fixtureArchive()
        service.import(file, null)
        val pharmacy = db.cardDao().getAll().single { it.storeName == "Pharmacy" }.id
        val health = db.groupDao().getAll().single { it.name == "Health" }.id
        GroupRepository(db).setMembership(pharmacy, health, member = false)

        service.import(file, null)

        assertNull(membershipsByStore(db)["Pharmacy"])
    }

    @Test
    fun linksToUnlistedGroupsCreateThemAndLinksToUnknownCardsAreIgnored() = runTest {
        val csv = CatimaCsv.write(
            listOf(CatimaCard(id = 1, store = "Shop", cardId = "42")),
            groups = emptyList(),
            links = listOf(CatimaGroupLink(1, "Orphan"), CatimaGroupLink(99, "Ghost")),
        )

        assertEquals(ImportResult.Success(1, 0), service.import(fixtureArchive(csv = csv), null))

        assertEquals(listOf("Orphan"), db.groupDao().getAll().map { it.name })
        assertEquals(mapOf("Shop" to setOf("Orphan")), membershipsByStore(db))
    }

    @Test
    fun namesDifferingOnlyByCaseOrAccentsBecomeOneGroup() = runTest {
        val csv = CatimaCsv.write(
            listOf(CatimaCard(id = 1, store = "Shop", cardId = "42"), CatimaCard(id = 2, store = "Other", cardId = "43")),
            groups = listOf("Food", "food", "Fôod"),
            links = listOf(CatimaGroupLink(1, "Food"), CatimaGroupLink(2, "food"), CatimaGroupLink(1, "FOOD")),
        )

        service.import(fixtureArchive(csv = csv), null)

        assertEquals(listOf("Food"), db.groupDao().getAll().map { it.name })
        assertEquals(mapOf("Shop" to setOf("Food"), "Other" to setOf("Food")), membershipsByStore(db))
    }

    @Test
    fun groupsSurviveAnExportImportRoundTrip() = runTest {
        val groups = GroupRepository(db)
        val fnac = db.cardDao().insert(testCard("Fnac"))
        val kase = db.cardDao().insert(testCard("Käse", cardNumber = "K-1"))
        val courses = (groups.create("Courses, vrac") as GroupNameResult.Saved).id
        val mode = (groups.create("🛒 Mode") as GroupNameResult.Saved).id
        groups.create("Vide")
        groups.setMembership(fnac, courses, member = true)
        groups.setMembership(kase, courses, member = true)
        groups.setMembership(fnac, mode, member = true)
        val exported = ByteArrayOutputStream()

        assertEquals(ExportResult.Success(2), service.export({ exported }, null))

        val otherDb = inMemoryDatabase()
        try {
            val file = File(tmp.root, "groups.zip").apply { writeBytes(exported.toByteArray()) }
            serviceFor(otherDb, ImageStore(File(tmp.root, "images2"), File(tmp.root, "staging2"))).import(file, null)
            assertEquals(setOf("Courses, vrac", "🛒 Mode", "Vide"), otherDb.groupDao().getAll().map { it.name }.toSet())
            assertEquals(
                mapOf("Fnac" to setOf("Courses, vrac", "🛒 Mode"), "Käse" to setOf("Courses, vrac")),
                membershipsByStore(otherDb),
            )
        } finally {
            otherDb.close()
        }
    }
}
