package io.github.vferries.encarte.cards.edit

import android.graphics.Bitmap
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.brands.BrandCatalog
import io.github.vferries.encarte.core.barcode.BarcodeError
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.core.color.CardPalette
import io.github.vferries.encarte.core.data.CardRepository
import io.github.vferries.encarte.core.data.CardSide
import io.github.vferries.encarte.core.data.ImageStore
import io.github.vferries.encarte.testing.MainDispatcherRule
import io.github.vferries.encarte.testing.eventually
import io.github.vferries.encarte.testing.inMemoryDatabase
import io.github.vferries.encarte.testing.testCard
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
class CardEditViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val now = Instant.parse("2026-10-04T12:00:00Z")
    private val db = inMemoryDatabase()
    private val images by lazy { ImageStore(File(tmp.root, "images"), File(tmp.root, "staging")) }
    private val cards by lazy { CardRepository(db.cardDao(), images, Clock.fixed(now, ZoneOffset.UTC)) }
    private val brands = BrandCatalog {
        """[{"name": "Carrefour", "aliases": ["Carrefour Market"], "color": "#254F9B"},
            {"name": "Castorama", "aliases": [], "color": "#0078D7"}]"""
    }

    @After
    fun tearDown() = db.close()

    private fun newCard(value: String? = null, format: BarcodeFormat? = null) =
        CardEditViewModel(null, value, format, showUnsupportedFormatNotice = false, cards = cards, brands = brands)

    @Test
    fun prefilledScanNeedsStoreNameBeforeSaving() = runTest {
        val vm = newCard("4006381333931", BarcodeFormat.EAN_13)
        assertEquals("4006381333931", vm.cardNumber.text.toString())
        assertFalse(vm.canSave)

        vm.storeName.setTextAndPlaceCursorAtEnd("Fnac")

        assertTrue(vm.canSave)
        assertEquals("4006381333931", vm.previewValue)
    }

    @Test
    fun invalidCheckDigitBlocksSaving() = runTest {
        val vm = newCard("4006381333932", BarcodeFormat.EAN_13)
        vm.storeName.setTextAndPlaceCursorAtEnd("Fnac")

        assertEquals(BarcodeError.INVALID_CHECK_DIGIT, vm.barcodeError)
        assertFalse(vm.canSave)
        assertNull(vm.previewValue)
    }

    @Test
    fun knownBrandSetsColorUntilUserPicksOne() = runTest {
        val vm = newCard("123")
        vm.storeName.setTextAndPlaceCursorAtEnd("carrefour market")
        assertEquals(0xFF254F9B.toInt(), vm.color)

        vm.selectColor(CardPalette.swatches[0])

        assertEquals(CardPalette.swatches[0], vm.color)
    }

    @Test
    fun unknownNameGetsPaletteColor() = runTest {
        val vm = newCard("123")
        vm.storeName.setTextAndPlaceCursorAtEnd("Ma boulangerie")

        assertEquals(CardPalette.defaultFor("Ma boulangerie"), vm.color)
    }

    @Test
    fun suggestionsHideOnceNameMatchesExactly() = runTest {
        val vm = newCard("123")
        vm.storeName.setTextAndPlaceCursorAtEnd("ca")
        assertEquals(listOf("Carrefour", "Castorama"), vm.suggestions.map { it.name })

        vm.selectSuggestion(vm.suggestions.first())

        assertEquals("Carrefour", vm.storeName.text.toString())
        assertEquals(emptyList<Any>(), vm.suggestions)
        assertEquals(0xFF254F9B.toInt(), vm.color)
    }

    @Test
    fun savingNewCardStoresItAndReportsId() = runTest {
        val vm = newCard("4006381333931", BarcodeFormat.EAN_13)
        vm.storeName.setTextAndPlaceCursorAtEnd("  Fnac ")
        vm.note.setTextAndPlaceCursorAtEnd("Gold member")

        vm.save()
        eventually { vm.savedCardId != null }

        val saved = cards.get(vm.savedCardId!!)!!
        assertEquals("Fnac", saved.storeName)
        assertEquals("4006381333931", saved.cardNumber)
        assertNull(saved.barcodeValue)
        assertEquals(BarcodeFormat.EAN_13, saved.barcodeFormat)
        assertEquals("Gold member", saved.note)
        assertEquals(now, saved.createdAt)
    }

    @Test
    fun differentEncodedValueIsSavedOnlyWhenItDiffers() = runTest {
        val vm = newCard("1234", BarcodeFormat.CODE_128)
        vm.storeName.setTextAndPlaceCursorAtEnd("Shop")
        vm.setDifferentEncodedValue(true)
        assertFalse("empty encoded value", vm.canSave)

        vm.barcodeValue.setTextAndPlaceCursorAtEnd("X-1234")
        vm.save()
        eventually { vm.savedCardId != null }

        assertEquals("X-1234", cards.get(vm.savedCardId!!)!!.barcodeValue)
    }

    @Test
    fun editingLoadsCardAndTracksChanges() = runTest {
        val id = cards.save(testCard("Fnac", cardNumber = "42", barcodeFormat = BarcodeFormat.QR_CODE, isFavorite = true))
        val vm = CardEditViewModel(id, null, null, false, cards, brands)
        eventually { !vm.isLoading }

        assertEquals("Fnac", vm.storeName.text.toString())
        assertEquals(BarcodeFormat.QR_CODE, vm.barcodeFormat)
        assertFalse(vm.hasChanges)
        assertFalse(vm.isNew)

        vm.note.setTextAndPlaceCursorAtEnd("changed")
        assertTrue(vm.hasChanges)

        vm.save()
        eventually { vm.savedCardId != null }
        val saved = cards.get(id)!!
        assertEquals("changed", saved.note)
        assertTrue("favorite flag is preserved", saved.isFavorite)
    }

    @Test
    fun missingCardIsNotFound() = runTest {
        val vm = CardEditViewModel(404, null, null, false, cards, brands)

        eventually { !vm.isLoading }

        assertTrue(vm.notFound)
        assertFalse(vm.canSave)
    }

    @Test
    fun replacingAPickedImageDeletesTheFirstAndDiscardDeletesTheRest() = runTest {
        val vm = newCard("123")
        vm.onImagePicked(CardSide.FRONT) { jpeg() }
        eventually { vm.frontImage != null }
        val first = vm.frontImage!!

        vm.onImagePicked(CardSide.FRONT) { jpeg() }
        eventually { vm.frontImage != first }
        val second = vm.frontImage!!
        eventually { !images.exists(first) }

        vm.discard()
        eventually { !images.exists(second) }
    }

    @Test
    fun unreadableImageRaisesError() = runTest {
        val vm = newCard("123")

        vm.onImagePicked(CardSide.BACK) { "not an image".byteInputStream() }

        eventually { vm.imageError }
        assertNull(vm.backImage)
        vm.dismissImageError()
        assertFalse(vm.imageError)
    }

    private fun jpeg() = ByteArrayOutputStream().also {
        Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it)
    }.toByteArray().inputStream()
}
