package io.github.vferries.encarte.core.data

import android.content.Context
import androidx.room3.Room
import androidx.room3.useWriterConnection
import androidx.room3.withWriteTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.testing.testCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class CardDaoTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: EncarteDatabase
    private lateinit var dao: CardDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<EncarteDatabase>(context).build()
        dao = db.cardDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun crudAndFlows() = runTest {
        val id = dao.insert(testCard("Library", barcodeFormat = BarcodeFormat.EAN_13))

        val stored = dao.observe(id).first()!!
        assertEquals(BarcodeFormat.EAN_13, stored.barcodeFormat)
        assertEquals(Instant.ofEpochMilli(1_700_000_000_000), stored.createdAt)
        assertNull(stored.lastUsedAt)

        dao.update(stored.copy(note = "updated"))
        assertEquals("updated", dao.get(id)!!.note)
        assertEquals(listOf(id), dao.observeAll().first().map { it.id })

        val usedAt = Instant.ofEpochMilli(1_800_000_000_000)
        dao.markUsed(id, usedAt)
        assertEquals(usedAt, dao.get(id)!!.lastUsedAt)

        dao.setFavorite(id, true)
        assertTrue(dao.get(id)!!.isFavorite)

        assertEquals(1, dao.deleteById(id))
        assertNull(dao.get(id))
        assertEquals(emptyList<Card>(), dao.getAll())
    }

    @Test
    fun referencedImagesIsDistinctAndSkipsNulls() = runTest {
        dao.insert(testCard("A", frontImage = "a.jpg", backImage = "shared.jpg"))
        dao.insert(testCard("B", frontImage = "shared.jpg"))
        dao.insert(testCard("C"))

        assertEquals(setOf("a.jpg", "shared.jpg"), dao.referencedImages().toSet())
        assertEquals(2, dao.referencedImages().size)
    }

    @Test
    fun unknownBarcodeFormatNameReadsAsNull() = runTest {
        val id = dao.insert(testCard("Legacy", barcodeFormat = BarcodeFormat.QR_CODE))
        db.useWriterConnection { connection ->
            connection.usePrepared("UPDATE cards SET barcodeFormat = 'NOT_A_FORMAT' WHERE id = ?") { statement ->
                statement.bindLong(1, id)
                statement.step()
            }
        }

        assertNull(dao.get(id)!!.barcodeFormat)
    }

    @Test
    fun writeTransactionRollsBackOnFailure() = runTest {
        runCatching {
            db.withWriteTransaction {
                dao.insert(testCard("A"))
                error("boom")
            }
        }

        assertEquals(emptyList<Card>(), dao.getAll())
    }

    @Test
    fun encodedValueFallsBackToCardNumber() {
        assertEquals("123456", testCard().encodedValue)
        assertEquals("999", testCard().copy(barcodeValue = "999").encodedValue)
    }

    @Test
    fun expiryAndArchiveArePersisted() = runTest {
        val id = dao.insert(testCard("Library", expiresOn = LocalDate.of(2027, 3, 12)))

        dao.setArchived(id, true)

        val stored = dao.get(id)!!
        assertEquals(LocalDate.of(2027, 3, 12), stored.expiresOn)
        assertTrue(stored.isArchived)
        dao.setArchived(id, false)
        assertFalse(dao.get(id)!!.isArchived)
    }
}
