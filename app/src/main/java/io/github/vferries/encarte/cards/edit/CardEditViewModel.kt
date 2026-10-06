package io.github.vferries.encarte.cards.edit

import android.os.Bundle
import android.util.Log
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vferries.encarte.brands.Brand
import io.github.vferries.encarte.brands.BrandCatalog
import io.github.vferries.encarte.cards.list.cardCollator
import io.github.vferries.encarte.core.barcode.BarcodeError
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.core.barcode.BarcodeValidator
import io.github.vferries.encarte.core.color.CardPalette
import io.github.vferries.encarte.core.data.Card
import io.github.vferries.encarte.core.data.CardGroup
import io.github.vferries.encarte.core.data.CardRepository
import io.github.vferries.encarte.core.data.CardSide
import io.github.vferries.encarte.core.data.GroupNameResult
import io.github.vferries.encarte.core.data.GroupRepository
import io.github.vferries.encarte.importing.CardDraft
import io.github.vferries.encarte.importing.DraftNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate

private const val TAG = "CardEditViewModel"
private const val SAVED_STATE_KEY = "card_edit_form"
private const val KEY_LOADED = "loaded"
private const val KEY_STORE_NAME = "store_name"
private const val KEY_CARD_NUMBER = "card_number"
private const val KEY_DIFFERENT_ENCODED_VALUE = "different_encoded_value"
private const val KEY_BARCODE_VALUE = "barcode_value"
private const val KEY_BARCODE_FORMAT = "barcode_format"
private const val KEY_NOTE = "note"
private const val KEY_COLOR = "color"
private const val KEY_FRONT_IMAGE = "front_image"
private const val KEY_BACK_IMAGE = "back_image"
private const val KEY_EXPIRES_ON = "expires_on"
private const val KEY_GROUP_IDS = "group_ids"
private const val KEY_CREATED_IMAGES = "created_images"

class CardEditViewModel(
    private val cardId: Long?,
    prefillValue: String?,
    prefillFormat: BarcodeFormat?,
    val showUnsupportedFormatNotice: Boolean,
    private val cards: CardRepository,
    private val brands: BrandCatalog,
    savedStateHandle: SavedStateHandle,
    private val groups: GroupRepository,
    /** Pre-checked on a new card: it was added while the list showed this group. */
    initialGroupId: Long? = null,
    /** Pre-fills a new card from an imported file; ignored when editing an existing card. */
    draft: CardDraft? = null,
) : ViewModel() {

    val storeName = TextFieldState()
    val cardNumber = TextFieldState(prefillValue.orEmpty())
    val barcodeValue = TextFieldState()
    val note = TextFieldState()

    var barcodeFormat by mutableStateOf(prefillFormat)
        private set
    private var differentEncodedValueState by mutableStateOf(false)
    val differentEncodedValue: Boolean get() = differentEncodedValueState
    var frontImage by mutableStateOf<String?>(null)
        private set
    var backImage by mutableStateOf<String?>(null)
        private set

    private var expiresOnState by mutableStateOf<LocalDate?>(null)

    /** Null: the card never expires. */
    val expiresOn: LocalDate? get() = expiresOnState

    private var selectedGroupIdsState by mutableStateOf(setOfNotNull(initialGroupId))
    val selectedGroupIds: Set<Long> get() = selectedGroupIdsState

    /** Every group, for the chips. The card's own selection is part of the form. */
    val allGroups: StateFlow<List<CardGroup>> = groups.observeGroups(cardCollator())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var isLoading by mutableStateOf(cardId != null)
        private set
    var notFound by mutableStateOf(false)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var savedCardId by mutableStateOf<Long?>(null)
        private set
    var imageError by mutableStateOf(false)
        private set

    /** Null until the user (or the edited card) fixes a color; until then it follows the store name. */
    private var manualColor by mutableStateOf<Int?>(null)
    private var original: Card? = null
    private var initialForm: FormSnapshot? = null
    /** Files saved during this session and not yet owned by a saved card. */
    private val createdImages = mutableSetOf<String>()

    val isNew: Boolean get() = cardId == null

    /** Shown above the form: what the import could not fill in. */
    val draftNotice: DraftNotice? = draft?.notice.takeIf { cardId == null }

    val color: Int
        get() = manualColor ?: storeName.text.toString().let { name ->
            brands.match(name)?.argb ?: CardPalette.defaultFor(name)
        }

    val suggestions: List<Brand>
        get() {
            val name = storeName.text.toString()
            val matches = brands.suggest(name)
            return if (matches.any { it.name == name }) emptyList() else matches
        }

    private val encodedText: String
        get() = (if (differentEncodedValue) barcodeValue.text else cardNumber.text).toString().trim()

    /** Null while there is nothing to validate yet: the required-field hints cover that case. */
    val barcodeError: BarcodeError?
        get() {
            val format = barcodeFormat ?: return null
            val value = encodedText
            return if (value.isEmpty()) null else BarcodeValidator.validate(value, format)
        }

    val previewValue: String?
        get() = encodedText.takeIf { it.isNotEmpty() && barcodeFormat != null && barcodeError == null }

    val canSave: Boolean
        get() = !isLoading && !isSaving && !notFound &&
            storeName.text.isNotBlank() && cardNumber.text.isNotBlank() &&
            barcodeFormat.let { format -> format == null || BarcodeValidator.validate(encodedText, format) == null }

    val hasChanges: Boolean get() = initialForm.let { it != null && it != snapshot() }

    init {
        viewModelScope.launch(Dispatchers.Default) { brands.preload() }
        // The process can die while the camera app is in front: the form survives it.
        val restored = savedStateHandle.get<Bundle>(SAVED_STATE_KEY)?.takeIf { it.getBoolean(KEY_LOADED) }
        if (cardId == null) {
            // The draft is the starting point, like a scanned number: leaving it untouched discards without asking.
            draft?.let { fill(it.toForm(selectedGroupIds)) }
            initialForm = snapshot()
            restored?.let(::restore)
        } else {
            viewModelScope.launch { load(cardId, restored) }
        }
        savedStateHandle.setSavedStateProvider(SAVED_STATE_KEY, ::saveState)
    }

    /** With [restored] edits, the card is read again only for what the form doesn't hold (id, dates, favorite). */
    private suspend fun load(id: Long, restored: Bundle?) {
        val card = cards.get(id)
        if (card == null) {
            Log.w(TAG, "Card $id not found for editing")
            notFound = true
            initialForm = snapshot()
        } else {
            original = card
            val loaded = card.toForm(groups.groupIdsOf(id))
            initialForm = loaded
            if (restored != null) restore(restored) else fill(loaded)
        }
        isLoading = false
    }

    private fun fill(form: FormSnapshot) {
        storeName.setTextAndPlaceCursorAtEnd(form.storeName)
        cardNumber.setTextAndPlaceCursorAtEnd(form.cardNumber)
        differentEncodedValueState = form.differentEncodedValue
        barcodeValue.setTextAndPlaceCursorAtEnd(form.barcodeValue)
        barcodeFormat = form.barcodeFormat
        note.setTextAndPlaceCursorAtEnd(form.note)
        manualColor = form.color
        frontImage = form.frontImage
        backImage = form.backImage
        expiresOnState = form.expiresOn
        selectedGroupIdsState = form.groupIds
    }

    /** Saved only once the form is filled: an editor that died while loading simply loads the card again. */
    private fun saveState(): Bundle = Bundle().apply {
        if (isLoading || notFound) return@apply
        val form = snapshot()
        putBoolean(KEY_LOADED, true)
        putString(KEY_STORE_NAME, form.storeName)
        putString(KEY_CARD_NUMBER, form.cardNumber)
        putBoolean(KEY_DIFFERENT_ENCODED_VALUE, form.differentEncodedValue)
        putString(KEY_BARCODE_VALUE, form.barcodeValue)
        putString(KEY_BARCODE_FORMAT, form.barcodeFormat?.name)
        putString(KEY_NOTE, form.note)
        form.color?.let { putInt(KEY_COLOR, it) }
        putString(KEY_FRONT_IMAGE, form.frontImage)
        putString(KEY_BACK_IMAGE, form.backImage)
        form.expiresOn?.let { putLong(KEY_EXPIRES_ON, it.toEpochDay()) }
        putLongArray(KEY_GROUP_IDS, form.groupIds.toLongArray())
        putStringArrayList(KEY_CREATED_IMAGES, ArrayList(createdImages))
    }

    private fun restore(state: Bundle) {
        fill(
            FormSnapshot(
                storeName = state.getString(KEY_STORE_NAME).orEmpty(),
                cardNumber = state.getString(KEY_CARD_NUMBER).orEmpty(),
                differentEncodedValue = state.getBoolean(KEY_DIFFERENT_ENCODED_VALUE),
                barcodeValue = state.getString(KEY_BARCODE_VALUE).orEmpty(),
                barcodeFormat = state.getString(KEY_BARCODE_FORMAT)?.let(BarcodeFormat::valueOf),
                note = state.getString(KEY_NOTE).orEmpty(),
                color = if (state.containsKey(KEY_COLOR)) state.getInt(KEY_COLOR) else null,
                frontImage = state.getString(KEY_FRONT_IMAGE)?.takeIf(::imageStillExists),
                backImage = state.getString(KEY_BACK_IMAGE)?.takeIf(::imageStillExists),
                expiresOn = if (state.containsKey(KEY_EXPIRES_ON)) LocalDate.ofEpochDay(state.getLong(KEY_EXPIRES_ON)) else null,
                groupIds = state.getLongArray(KEY_GROUP_IDS)?.toSet().orEmpty(),
            )
        )
        state.getStringArrayList(KEY_CREATED_IMAGES)?.filterTo(createdImages, ::imageStillExists)
    }

    /** The startup sweep deletes the photos an unsaved editor had picked before the process died. */
    private fun imageStillExists(name: String): Boolean =
        cards.imageFile(name).isFile.also { exists -> if (!exists) Log.w(TAG, "Restored image $name is gone") }

    fun selectFormat(format: BarcodeFormat?) {
        barcodeFormat = format
    }

    fun setDifferentEncodedValue(enabled: Boolean) {
        differentEncodedValueState = enabled
    }

    fun selectColor(argb: Int) {
        manualColor = argb
    }

    fun setExpiresOn(date: LocalDate?) {
        expiresOnState = date
    }

    fun toggleGroup(id: Long) {
        selectedGroupIdsState = if (id in selectedGroupIdsState) selectedGroupIdsState - id else selectedGroupIdsState + id
    }

    /** The new group exists at once, even if this edit is discarded: an empty group is legitimate. */
    suspend fun createGroup(name: String): GroupNameResult = groups.create(name).also { result ->
        if (result is GroupNameResult.Saved) selectedGroupIdsState = selectedGroupIdsState + result.id
    }

    fun selectSuggestion(brand: Brand) {
        storeName.setTextAndPlaceCursorAtEnd(brand.name)
        manualColor = brand.argb
    }

    fun imageFile(name: String): File = cards.imageFile(name)

    /** [onFinished] runs once the import is over, successful or not, e.g. to delete a temporary source. */
    fun onImagePicked(side: CardSide, onFinished: () -> Unit = {}, open: () -> InputStream) {
        viewModelScope.launch {
            try {
                val name = cards.saveImage(open)
                createdImages += name
                replaceImage(side, name)
            } catch (e: IOException) {
                Log.w(TAG, "Cannot import picked image", e)
                imageError = true
            } finally {
                onFinished()
            }
        }
    }

    fun removeImage(side: CardSide) = replaceImage(side, null)

    fun dismissImageError() {
        imageError = false
    }

    fun save() {
        if (!canSave) return
        isSaving = true
        val card = buildCard()
        viewModelScope.launch {
            val id = cards.save(card, selectedGroupIds)
            createdImages.clear()
            savedCardId = id
        }
    }

    /** Drops images picked in this session; images of the edited card stay untouched. */
    fun discard() {
        val leftovers = createdImages.toList()
        createdImages.clear()
        viewModelScope.launch { leftovers.forEach { cards.discardImage(it) } }
    }

    private fun replaceImage(side: CardSide, name: String?) {
        val previous = if (side == CardSide.FRONT) frontImage else backImage
        if (side == CardSide.FRONT) frontImage = name else backImage = name
        // A file picked earlier in this session and now replaced is unreachable: drop it right away.
        if (previous != null && createdImages.remove(previous)) {
            viewModelScope.launch { cards.discardImage(previous) }
        }
    }

    private fun buildCard(): Card {
        val number = cardNumber.text.toString().trim()
        val encoded = barcodeValue.text.toString().trim()
        return Card(
            id = original?.id ?: 0,
            storeName = storeName.text.toString().trim(),
            cardNumber = number,
            barcodeValue = encoded.takeIf { differentEncodedValue && it.isNotEmpty() && it != number },
            barcodeFormat = barcodeFormat,
            note = note.text.toString().trim(),
            color = color,
            isFavorite = original?.isFavorite ?: false,
            frontImage = frontImage,
            backImage = backImage,
            createdAt = original?.createdAt ?: Instant.EPOCH, // replaced by CardRepository on insert
            lastUsedAt = original?.lastUsedAt,
            expiresOn = expiresOn,
            // The editor has no archive switch: editing an archived card keeps it archived.
            isArchived = original?.isArchived ?: false,
        )
    }

    private fun Card.toForm(groupIds: Set<Long>) = FormSnapshot(
        storeName = storeName,
        cardNumber = cardNumber,
        differentEncodedValue = barcodeValue != null,
        barcodeValue = barcodeValue.orEmpty(),
        barcodeFormat = barcodeFormat,
        note = note,
        color = color,
        frontImage = frontImage,
        backImage = backImage,
        expiresOn = expiresOn,
        groupIds = groupIds,
    )

    private fun CardDraft.toForm(groupIds: Set<Long>) = FormSnapshot(
        storeName = storeName,
        cardNumber = cardNumber,
        differentEncodedValue = barcodeValue != null,
        barcodeValue = barcodeValue.orEmpty(),
        barcodeFormat = barcodeFormat,
        note = note,
        color = color,
        frontImage = null,
        backImage = null,
        expiresOn = expiresOnEpochDay?.let(LocalDate::ofEpochDay),
        groupIds = groupIds,
    )

    private fun snapshot() = FormSnapshot(
        storeName = storeName.text.toString(),
        cardNumber = cardNumber.text.toString(),
        differentEncodedValue = differentEncodedValue,
        barcodeValue = barcodeValue.text.toString(),
        barcodeFormat = barcodeFormat,
        note = note.text.toString(),
        color = manualColor,
        frontImage = frontImage,
        backImage = backImage,
        expiresOn = expiresOn,
        groupIds = selectedGroupIds,
    )

    private data class FormSnapshot(
        val storeName: String,
        val cardNumber: String,
        val differentEncodedValue: Boolean,
        val barcodeValue: String,
        val barcodeFormat: BarcodeFormat?,
        val note: String,
        val color: Int?,
        val frontImage: String?,
        val backImage: String?,
        val expiresOn: LocalDate?,
        val groupIds: Set<Long>,
    )
}
