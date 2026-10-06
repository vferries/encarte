package io.github.vferries.encarte.core.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

// GROUPS is an SQL keyword since SQLite 3.28: the table name is always quoted.
@Dao
interface GroupDao {
    /** Unordered: sorting is locale-aware and done in Kotlin, like the cards. */
    @Query("SELECT * FROM `groups`")
    fun observeAll(): Flow<List<CardGroup>>

    @Query("SELECT * FROM `groups`")
    suspend fun getAll(): List<CardGroup>

    @Insert
    suspend fun insert(group: CardGroup): Long

    @Query("UPDATE `groups` SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String): Int

    @Query("DELETE FROM `groups` WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM card_groups")
    fun observeMemberships(): Flow<List<CardGroupCrossRef>>

    @Query("SELECT groupId FROM card_groups WHERE cardId = :cardId")
    suspend fun groupIdsOf(cardId: Long): List<Long>

    /** Ignoring conflicts makes adding an existing membership a no-op. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMemberships(memberships: List<CardGroupCrossRef>)

    @Query("DELETE FROM card_groups WHERE cardId = :cardId AND groupId = :groupId")
    suspend fun deleteMembership(cardId: Long, groupId: Long)
}
