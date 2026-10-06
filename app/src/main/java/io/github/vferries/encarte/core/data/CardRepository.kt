package io.github.vferries.encarte.core.data

import android.util.Log
import androidx.room3.withWriteTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.time.Clock

private const val TAG = "CardRepository"

class CardRepository(
    private val database: EncarteDatabase,
    private val images: ImageStore,
    private val clock: Clock,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val dao = database.cardDao()
    private val groupDao = database.groupDao()

    fun observeCards(): Flow<List<Card>> = dao.observeAll()

    fun observeCard(id: Long): Flow<Card?> = dao.observe(id)

    suspend fun get(id: Long): Card? = dao.get(id)

    /**
     * Inserts when [Card.id] is 0 (assigning createdAt), otherwise updates and drops replaced images.
     * Non-null [groupIds] replace the card's groups in the same transaction; null keeps them.
     */
    suspend fun save(card: Card, groupIds: Set<Long>? = null): Long = withContext(io) {
        var replacedImages = emptyList<String>()
        val id = database.withWriteTransaction<Long> {
            val id = if (card.id == 0L) {
                dao.insert(card.copy(createdAt = clock.instant()))
            } else {
                val previous = dao.get(card.id)
                if (previous == null) {
                    // Nothing to update, and memberships of a missing card would break the foreign key.
                    Log.w(TAG, "Card ${card.id} was deleted while it was edited")
                    return@withWriteTransaction card.id
                }
                dao.update(card)
                replacedImages = listOfNotNull(
                    previous.frontImage.takeIf { it != card.frontImage },
                    previous.backImage.takeIf { it != card.backImage },
                )
                card.id
            }
            if (groupIds != null) replaceGroups(id, groupIds)
            id
        }
        // After the commit: a rolled-back save must not lose images its card still references.
        images.deleteAll(replacedImages)
        id
    }

    /** A group deleted while the card was being edited is skipped: the card still saves. */
    private suspend fun replaceGroups(cardId: Long, groupIds: Set<Long>) {
        val existing = groupDao.getAll().mapTo(mutableSetOf()) { it.id }
        val missing = groupIds - existing
        if (missing.isNotEmpty()) Log.w(TAG, "Groups $missing were deleted while card $cardId was edited")
        groupDao.deleteMembershipsOf(cardId)
        groupDao.insertMemberships((groupIds intersect existing).map { CardGroupCrossRef(cardId, it) })
    }

    suspend fun delete(id: Long) = withContext(io) {
        val card = dao.get(id) ?: return@withContext
        dao.deleteById(id)
        images.deleteAll(listOfNotNull(card.frontImage, card.backImage))
    }

    suspend fun markUsed(id: Long) = dao.markUsed(id, clock.instant())

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived)

    suspend fun saveImage(open: () -> InputStream): String = withContext(io) { images.save(open) }

    suspend fun discardImage(name: String) = withContext(io) { images.delete(name) }

    fun imageFile(name: String): File = images.file(name)

    /** Safety net for images picked in an editor that was never saved (process death, crash). */
    suspend fun deleteOrphanImages() = withContext(io) {
        images.deleteOrphans(dao.referencedImages().toSet())
    }
}
