package io.github.vferries.encarte.core.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.time.Clock

class CardRepository(
    private val dao: CardDao,
    private val images: ImageStore,
    private val clock: Clock,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    fun observeCards(): Flow<List<Card>> = dao.observeAll()

    fun observeCard(id: Long): Flow<Card?> = dao.observe(id)

    suspend fun get(id: Long): Card? = dao.get(id)

    /** Inserts when [Card.id] is 0 (assigning createdAt), otherwise updates and drops replaced images. */
    suspend fun save(card: Card): Long = withContext(io) {
        if (card.id == 0L) {
            dao.insert(card.copy(createdAt = clock.instant()))
        } else {
            val previous = dao.get(card.id)
            dao.update(card)
            if (previous != null) {
                images.deleteAll(
                    listOfNotNull(
                        previous.frontImage.takeIf { it != card.frontImage },
                        previous.backImage.takeIf { it != card.backImage },
                    )
                )
            }
            card.id
        }
    }

    suspend fun delete(id: Long) = withContext(io) {
        val card = dao.get(id) ?: return@withContext
        dao.deleteById(id)
        images.deleteAll(listOfNotNull(card.frontImage, card.backImage))
    }

    suspend fun markUsed(id: Long) = dao.markUsed(id, clock.instant())

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    suspend fun saveImage(open: () -> InputStream): String = withContext(io) { images.save(open) }

    suspend fun discardImage(name: String) = withContext(io) { images.delete(name) }

    fun imageFile(name: String): File = images.file(name)

    /** Safety net for images picked in an editor that was never saved (process death, crash). */
    suspend fun deleteOrphanImages() = withContext(io) {
        images.deleteOrphans(dao.referencedImages().toSet())
    }
}
