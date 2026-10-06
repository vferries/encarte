package io.github.vferries.encarte.core.data

import android.util.Log
import androidx.room3.withWriteTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Collator

private const val TAG = "GroupRepository"

sealed interface GroupNameResult {
    data class Saved(val id: Long) : GroupNameResult
    data object Blank : GroupNameResult
    data object Duplicate : GroupNameResult
}

class GroupRepository(private val database: EncarteDatabase) {
    private val dao = database.groupDao()

    /** Sorted by name like the cards: groups have no manual order (Catima doesn't export one either). */
    fun observeGroups(collator: Collator): Flow<List<CardGroup>> =
        dao.observeAll().map { groups -> groups.sortedWith(compareBy(collator) { it.name }) }

    fun observeGroup(id: Long): Flow<CardGroup?> = dao.observe(id)

    /** Card id → ids of its groups; cards in no group are absent. */
    fun observeMemberships(): Flow<Map<Long, Set<Long>>> = dao.observeMemberships().map { rows ->
        rows.groupBy({ it.cardId }, { it.groupId }).mapValues { (_, groupIds) -> groupIds.toSet() }
    }

    suspend fun create(name: String): GroupNameResult = saveName(id = null, name)

    suspend fun rename(id: Long, name: String): GroupNameResult = saveName(id, name)

    /** Removes the group and its memberships; the cards stay. */
    suspend fun delete(id: Long) {
        if (dao.deleteById(id) == 0) Log.w(TAG, "Group $id was already deleted")
    }

    suspend fun setMembership(cardId: Long, groupId: Long, member: Boolean) {
        if (member) {
            dao.insertMemberships(listOf(CardGroupCrossRef(cardId, groupId)))
        } else {
            dao.deleteMembership(cardId, groupId)
        }
    }

    suspend fun groupIdsOf(cardId: Long): Set<Long> = dao.groupIdsOf(cardId).toSet()

    /** The clash check and the write share one transaction, so two quick saves can't both pass the check. */
    private suspend fun saveName(id: Long?, name: String): GroupNameResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return GroupNameResult.Blank
        val key = groupNameKey(trimmed)
        return database.withWriteTransaction<GroupNameResult> {
            when {
                dao.getAll().any { it.id != id && groupNameKey(it.name) == key } -> GroupNameResult.Duplicate
                id == null -> GroupNameResult.Saved(dao.insert(CardGroup(name = trimmed)))
                else -> {
                    if (dao.rename(id, trimmed) == 0) Log.w(TAG, "Renaming group $id, which no longer exists")
                    GroupNameResult.Saved(id)
                }
            }
        }
    }
}
