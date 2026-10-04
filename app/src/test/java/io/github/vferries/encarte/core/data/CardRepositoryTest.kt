package io.github.vferries.encarte.core.data

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.testing.inMemoryDatabase
import io.github.vferries.encarte.testing.testCard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val now = Instant.parse("2026-10-04T10:00:00Z")
    private val db = inMemoryDatabase()
    private lateinit var images: ImageStore
    private lateinit var repository: CardRepository

    @OptIn(ExperimentalCoroutinesApi::class) // UnconfinedTestDispatcher
    @Before
    fun setUp() {
        images = ImageStore(File(tmp.root, "images"), File(tmp.root, "staging"))
        repository = CardRepository(db.cardDao(), images, Clock.fixed(now, ZoneOffset.UTC), UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insertAssignsCreatedAt() = runTest {
        val id = repository.save(testCard(createdAt = Instant.EPOCH))

        assertEquals(now, repository.get(id)!!.createdAt)
    }

    @Test
    fun updateDeletesReplacedImagesOnly() = runTest {
        val oldFront = repository.saveImage { jpeg() }
        val back = repository.saveImage { jpeg() }
        val id = repository.save(testCard(frontImage = oldFront, backImage = back))
        val newFront = repository.saveImage { jpeg() }

        repository.save(repository.get(id)!!.copy(frontImage = newFront))

        assertFalse(images.exists(oldFront))
        assertTrue(images.exists(newFront))
        assertTrue(images.exists(back))
    }

    @Test
    fun deleteRemovesCardAndImages() = runTest {
        val front = repository.saveImage { jpeg() }
        val id = repository.save(testCard(frontImage = front))

        repository.delete(id)

        assertNull(repository.get(id))
        assertFalse(images.exists(front))
    }

    @Test
    fun markUsedAndFavorite() = runTest {
        val id = repository.save(testCard())

        repository.markUsed(id)
        repository.setFavorite(id, true)

        val card = repository.observeCard(id).first()!!
        assertEquals(now, card.lastUsedAt)
        assertTrue(card.isFavorite)
    }

    @Test
    fun orphanCleanupKeepsReferencedImages() = runTest {
        val referenced = repository.saveImage { jpeg() }
        val orphan = repository.saveImage { jpeg() }
        repository.save(testCard(frontImage = referenced))

        repository.deleteOrphanImages()

        assertTrue(images.exists(referenced))
        assertFalse(images.exists(orphan))
    }

    private fun jpeg() = ByteArrayOutputStream().also {
        Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it)
    }.toByteArray().inputStream()
}
