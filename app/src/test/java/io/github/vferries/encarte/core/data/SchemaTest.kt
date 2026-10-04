package io.github.vferries.encarte.core.data

import androidx.room3.Room
import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Guards the exported schema: future migrations start from this file. */
@RunWith(AndroidJUnit4::class)
class SchemaTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val dbFile = instrumentation.targetContext.getDatabasePath("schema-test.db")

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        file = dbFile,
        driver = AndroidSQLiteDriver(),
        databaseClass = EncarteDatabase::class,
    )

    @Test
    fun version1FromExportedSchemaOpensWithRoom() = runTest {
        helper.createDatabase(1).apply {
            execSQL(
                "INSERT INTO cards (storeName, cardNumber, note, color, isFavorite, createdAt) " +
                    "VALUES ('Library', '42', '', 0, 0, 1700000000000)"
            )
            close()
        }

        // Room validates the on-disk schema (identity hash) against the compiled entities when opening.
        val db = Room.databaseBuilder<EncarteDatabase>(instrumentation.targetContext, dbFile.absolutePath).build()
        assertEquals("Library", db.cardDao().getAll().single().storeName)
        db.close()
    }
}
