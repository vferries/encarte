package io.github.vferries.encarte.cards.edit

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Parcel
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.brands.BrandCatalog
import io.github.vferries.encarte.cards.list.cardCollator
import io.github.vferries.encarte.core.barcode.BarcodeError
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.core.color.CardPalette
import io.github.vferries.encarte.core.data.CardRepository
import io.github.vferries.encarte.core.data.CardSide
import io.github.vferries.encarte.core.data.GroupNameResult
import io.github.vferries.encarte.core.data.GroupRepository
import io.github.vferries.encarte.core.data.ImageStore
import io.github.vferries.encarte.testing.MainDispatcherRule
import io.github.vferries.encarte.testing.eventually
import io.github.vferries.encarte.testing.inMemoryDatabase
import io.github.vferries.encarte.testing.testCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
import java.time.LocalDate
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
    private val cards by lazy { CardRepository(db, images, Clock.fixed(now, ZoneOffset.UTC)) }
    private val groups by lazy { GroupRepository(db) }
    private val brands = BrandCatalog {
        """[{"name": "Carrefour", "aliases": ["Carrefour Market"], "color": "#254F9B"},
            {"name": "Castorama", "aliases": [], "color": "#0078D7"}]"""
    }

    @After
    fun tearDown() = db.close()

    private fun newCard(value: String? = null, format: BarcodeFormat? = null) = CardEditViewModel(
        null, value, format, showUnsupportedFormatNotice = false, cards = cards, brands = brands,
        savedStateHandle = SavedStateHandle(),
        groups = groups,
    )

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
        val vm = CardEditViewModel(id, null, null, false, cards, brands, SavedStateHandle(), groups)
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
        val vm = CardEditViewModel(404, null, null, false, cards, brands, SavedStateHandle(), groups)

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
    fun imagePickReportsWhenItIsOverWhateverTheOutcome() = runTest {
        val vm = newCard("123")
        var finished = 0

        vm.onImagePicked(CardSide.FRONT, onFinished = { finished++ }) { jpeg() }
        eventually { finished == 1 }
        assertNotNull(vm.frontImage)

        vm.onImagePicked(CardSide.BACK, onFinished = { finished++ }) { "not an image".byteInputStream() }
        eventually { finished == 2 }
        assertTrue(vm.imageError)
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

    @Test
    fun newCardEditsSurviveProcessDeath() = runTest {
        val before = ScreenWithSavedState(restored = null)
        val vm = before.editor(cardId = null, prefillValue = "123", prefillFormat = BarcodeFormat.CODE_128)
        vm.storeName.setTextAndPlaceCursorAtEnd("Fnac")
        vm.cardNumber.setTextAndPlaceCursorAtEnd("4006381333931")
        vm.selectFormat(BarcodeFormat.EAN_13)
        vm.setDifferentEncodedValue(true)
        vm.barcodeValue.setTextAndPlaceCursorAtEnd("4006381333948")
        vm.note.setTextAndPlaceCursorAtEnd("Gold member")
        vm.selectColor(CardPalette.swatches[2])
        vm.setExpiresOn(LocalDate.of(2027, 3, 12))
        vm.onImagePicked(CardSide.FRONT) { jpeg() }
        eventually { vm.frontImage != null }
        val picked = vm.frontImage!!

        val restored = ScreenWithSavedState(before.processDeath())
            .editor(cardId = null, prefillValue = "123", prefillFormat = BarcodeFormat.CODE_128)

        assertEquals("Fnac", restored.storeName.text.toString())
        assertEquals("4006381333931", restored.cardNumber.text.toString())
        assertEquals(BarcodeFormat.EAN_13, restored.barcodeFormat)
        assertTrue(restored.differentEncodedValue)
        assertEquals("4006381333948", restored.barcodeValue.text.toString())
        assertEquals("Gold member", restored.note.text.toString())
        assertEquals(CardPalette.swatches[2], restored.color)
        assertEquals(LocalDate.of(2027, 3, 12), restored.expiresOn)
        assertEquals(picked, restored.frontImage)
        assertNull(restored.backImage)
        assertTrue(restored.hasChanges)
        restored.discard()
        eventually { !images.exists(picked) }
    }

    @Test
    fun restoredEditorDropsPhotosDeletedSinceTheProcessDied() = runTest {
        val before = ScreenWithSavedState(restored = null)
        val vm = before.editor(cardId = null)
        vm.onImagePicked(CardSide.BACK) { jpeg() }
        eventually { vm.backImage != null }
        val state = before.processDeath()
        // The startup sweep deletes images no saved card references.
        cards.deleteOrphanImages()

        val restored = ScreenWithSavedState(state).editor(cardId = null)

        assertNull(restored.backImage)
        assertFalse(restored.hasChanges)
    }

    @Test
    fun editedCardIsNotReloadedOverRestoredEdits() = runTest {
        val id = cards.save(testCard("Fnac", cardNumber = "42", isFavorite = true).copy(note = "old"))
        val before = ScreenWithSavedState(restored = null)
        val vm = before.editor(cardId = id)
        eventually { !vm.isLoading }
        vm.note.setTextAndPlaceCursorAtEnd("new")

        val restored = ScreenWithSavedState(before.processDeath()).editor(cardId = id)
        eventually { !restored.isLoading }

        assertEquals("new", restored.note.text.toString())
        assertEquals("Fnac", restored.storeName.text.toString())
        assertTrue(restored.hasChanges)
        restored.note.setTextAndPlaceCursorAtEnd("old")
        assertFalse("the restored editor still knows the card as loaded", restored.hasChanges)
        restored.note.setTextAndPlaceCursorAtEnd("new")
        restored.save()
        eventually { restored.savedCardId != null }
        assertEquals(id, restored.savedCardId)
        val saved = cards.get(id)!!
        assertEquals("new", saved.note)
        assertTrue("favorite flag is preserved", saved.isFavorite)
    }

    /**
     * A screen's saved-state owner: [processDeath] saves its state the way the activity does and
     * passes it through a Parcel; a new instance built from it restores it into new ViewModels.
     */
    private inner class ScreenWithSavedState(restored: Bundle?) : SavedStateRegistryOwner, ViewModelStoreOwner {
        private val lifecycleRegistry = LifecycleRegistry.createUnsafe(this)
        private val savedState = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
        override val viewModelStore = ViewModelStore()

        init {
            savedState.performAttach()
            enableSavedStateHandles()
            savedState.performRestore(restored)
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun editor(cardId: Long?, prefillValue: String? = null, prefillFormat: BarcodeFormat? = null): CardEditViewModel {
            val factory = viewModelFactory {
                initializer {
                    CardEditViewModel(cardId, prefillValue, prefillFormat, false, cards, brands, createSavedStateHandle(), groups)
                }
            }
            val extras = MutableCreationExtras().apply {
                set(SAVED_STATE_REGISTRY_OWNER_KEY, this@ScreenWithSavedState)
                set(VIEW_MODEL_STORE_OWNER_KEY, this@ScreenWithSavedState)
            }
            return ViewModelProvider.create(viewModelStore, factory, extras)[CardEditViewModel::class]
        }

        fun processDeath(): Bundle {
            val state = Bundle().also(savedState::performSave)
            viewModelStore.clear()
            val parcel = Parcel.obtain()
            try {
                parcel.writeBundle(state)
                parcel.setDataPosition(0)
                return parcel.readBundle(javaClass.classLoader)!!
            } finally {
                parcel.recycle()
            }
        }
    }

    @Test
    fun expiryDateIsSaved() = runTest {
        val vm = newCard("123")
        vm.storeName.setTextAndPlaceCursorAtEnd("Fnac")
        vm.setExpiresOn(LocalDate.of(2027, 3, 12))

        vm.save()
        eventually { vm.savedCardId != null }

        assertEquals(LocalDate.of(2027, 3, 12), cards.get(vm.savedCardId!!)!!.expiresOn)
    }

    @Test
    fun editingAnArchivedCardKeepsItArchivedAndTracksTheDate() = runTest {
        val id = cards.save(testCard("Fnac", isArchived = true, expiresOn = LocalDate.of(2027, 3, 12)))
        val vm = CardEditViewModel(id, null, null, false, cards, brands, SavedStateHandle(), groups)
        eventually { !vm.isLoading }
        assertEquals(LocalDate.of(2027, 3, 12), vm.expiresOn)
        assertFalse(vm.hasChanges)

        vm.setExpiresOn(null)
        assertTrue(vm.hasChanges)
        vm.save()
        eventually { vm.savedCardId != null }

        val saved = cards.get(id)!!
        assertNull(saved.expiresOn)
        assertTrue("archived state is preserved", saved.isArchived)
    }

    private suspend fun group(name: String) = (groups.create(name) as GroupNameResult.Saved).id

    @Test
    fun aNewCardStartsInItsInitialGroupWithoutUnsavedChanges() = runTest {
        val courses = group("Courses")

        val vm = CardEditViewModel(null, "42", null, false, cards, brands, SavedStateHandle(), groups, initialGroupId = courses)

        assertEquals(setOf(courses), vm.selectedGroupIds)
        assertFalse(vm.hasChanges)
    }

    @Test
    fun groupsAreSavedWithTheCard() = runTest {
        val courses = group("Courses")
        val mode = group("Mode")
        val vm = newCard("42")
        vm.storeName.setTextAndPlaceCursorAtEnd("Fnac")
        vm.toggleGroup(courses)
        vm.toggleGroup(mode)
        vm.toggleGroup(mode)

        vm.save()
        eventually { vm.savedCardId != null }

        assertEquals(setOf(courses), groups.groupIdsOf(vm.savedCardId!!))
    }

    @Test
    fun editingLoadsTheGroupsAndTracksTheirChanges() = runTest {
        val courses = group("Courses")
        val id = cards.save(testCard("Fnac"), setOf(courses))
        val vm = CardEditViewModel(id, null, null, false, cards, brands, SavedStateHandle(), groups)
        eventually { !vm.isLoading }
        assertEquals(setOf(courses), vm.selectedGroupIds)
        assertFalse(vm.hasChanges)

        vm.toggleGroup(courses)

        assertTrue(vm.hasChanges)
    }

    @Test
    fun aGroupCreatedInTheEditorIsCheckedAndOutlivesADiscard() = runTest {
        val vm = newCard("42")

        val created = vm.createGroup("Bricolage") as GroupNameResult.Saved
        assertEquals(setOf(created.id), vm.selectedGroupIds)
        vm.discard()

        assertEquals(listOf("Bricolage"), groups.observeGroups(cardCollator()).first().map { it.name })
    }

    @Test
    fun checkedGroupsSurviveProcessDeath() = runTest {
        val courses = group("Courses")
        val before = ScreenWithSavedState(restored = null)
        before.editor(cardId = null, prefillValue = "42").toggleGroup(courses)

        val restored = ScreenWithSavedState(before.processDeath()).editor(cardId = null, prefillValue = "42")

        assertEquals(setOf(courses), restored.selectedGroupIds)
    }

    private fun jpeg() = ByteArrayOutputStream().also {
        Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it)
    }.toByteArray().inputStream()
}
