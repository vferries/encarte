package io.github.vferries.encarte.core.data

import androidx.room3.Room
import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** 1.0.0 → 1.1.0 upgrades must keep every card: the new columns only get their defaults. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val dbFile = instrumentation.targetContext.getDatabasePath("migration-test.db")

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        file = dbFile,
        driver = AndroidSQLiteDriver(),
        databaseClass = EncarteDatabase::class,
    )

    @Test
    fun version1To2KeepsEveryValueAndAddsDefaults() = runTest {
        helper.createDatabase(1).apply {
            execSQL(
                "INSERT INTO cards (id, storeName, cardNumber, barcodeValue, barcodeFormat, note, color, isFavorite, " +
                    "frontImage, backImage, createdAt, lastUsedAt) VALUES (7, 'Fnac', '4006381333931', 'X-1', " +
                    "'EAN_13', 'Gold', -16777216, 1, 'front.jpg', 'back.jpg', 1700000000000, 1700000100000)"
            )
            execSQL(
                "INSERT INTO cards (id, storeName, cardNumber, note, color, isFavorite, createdAt) " +
                    "VALUES (8, 'Library', '42', '', 0, 0, 1700000000000)"
            )
            close()
        }

        helper.runMigrationsAndValidate(2).apply {
            prepare("SELECT expiresOn, isArchived FROM cards ORDER BY id").use { row ->
                repeat(2) {
                    assertTrue(row.step())
                    assertTrue("expiresOn is null", row.isNull(0))
                    assertEquals(0L, row.getLong(1))
                }
            }
            close()
        }

        val db = Room.databaseBuilder<EncarteDatabase>(instrumentation.targetContext, dbFile.absolutePath).build()
        try {
            assertEquals(
                Card(
                    id = 7, storeName = "Fnac", cardNumber = "4006381333931", barcodeValue = "X-1",
                    barcodeFormat = BarcodeFormat.EAN_13, note = "Gold", color = -16777216, isFavorite = true,
                    frontImage = "front.jpg", backImage = "back.jpg",
                    createdAt = Instant.ofEpochMilli(1_700_000_000_000),
                    lastUsedAt = Instant.ofEpochMilli(1_700_000_100_000),
                ),
                db.cardDao().get(7),
            )
            assertEquals("Library", db.cardDao().get(8)!!.storeName)
        } finally {
            db.close()
        }
    }
}
