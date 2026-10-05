package io.github.vferries.encarte.core.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface CardDao {
    /** Unordered: sorting is locale-aware and done in Kotlin (see CardListing). */
    @Query("SELECT * FROM cards")
    fun observeAll(): Flow<List<Card>>

    @Query("SELECT * FROM cards WHERE id = :id")
    fun observe(id: Long): Flow<Card?>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun get(id: Long): Card?

    @Query("SELECT * FROM cards ORDER BY id")
    suspend fun getAll(): List<Card>

    @Insert
    suspend fun insert(card: Card): Long

    @Update
    suspend fun update(card: Card)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("UPDATE cards SET lastUsedAt = :at WHERE id = :id")
    suspend fun markUsed(id: Long, at: Instant)

    @Query("UPDATE cards SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE cards SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query(
        "SELECT frontImage FROM cards WHERE frontImage IS NOT NULL " +
            "UNION SELECT backImage FROM cards WHERE backImage IS NOT NULL"
    )
    suspend fun referencedImages(): List<String>
}
