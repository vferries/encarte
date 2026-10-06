package io.github.vferries.encarte.core.data

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase

@Database(
    entities = [Card::class, CardGroup::class, CardGroupCrossRef::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
@ColumnTypeConverters(Converters::class)
abstract class EncarteDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao

    abstract fun groupDao(): GroupDao

    companion object {
        const val FILE_NAME = "encarte.db"

        // Room 3 defaults to AndroidSQLiteDriver and Dispatchers.IO on Android.
        fun create(context: Context): EncarteDatabase =
            Room.databaseBuilder<EncarteDatabase>(context.applicationContext, FILE_NAME).build()
    }
}
