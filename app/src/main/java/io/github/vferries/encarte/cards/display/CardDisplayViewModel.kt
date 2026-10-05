package io.github.vferries.encarte.cards.display

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vferries.encarte.core.data.Card
import io.github.vferries.encarte.core.data.CardRepository
import io.github.vferries.encarte.core.data.ExpiryStatus
import io.github.vferries.encarte.core.data.expiryStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import java.time.LocalDate

data class CardDisplayUiState(
    val isLoading: Boolean = true,
    val card: Card? = null,
    val frontImage: File? = null,
    val backImage: File? = null,
    val expiry: ExpiryStatus = ExpiryStatus.None,
    val isDeleted: Boolean = false,
    val justArchived: Boolean = false,
)

class CardDisplayViewModel(
    private val cardId: Long,
    private val cards: CardRepository,
    private val clock: Clock,
) : ViewModel() {

    private val deleted = MutableStateFlow(false)
    private val archivedHere = MutableStateFlow(false)

    val uiState: StateFlow<CardDisplayUiState> = combine(
        cards.observeCard(cardId),
        deleted,
        archivedHere,
    ) { card, isDeleted, isArchivedHere ->
        CardDisplayUiState(
            isLoading = false,
            card = card,
            frontImage = card?.frontImage?.let(cards::imageFile),
            backImage = card?.backImage?.let(cards::imageFile),
            expiry = expiryStatus(card?.expiresOn, LocalDate.now(clock)),
            isDeleted = isDeleted,
            justArchived = isArchivedHere,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardDisplayUiState())

    init {
        // Once per screen entry (the ViewModel survives rotation): feeds the "recently used" sort.
        viewModelScope.launch { cards.markUsed(cardId) }
    }

    fun toggleFavorite() {
        val card = uiState.value.card ?: return
        viewModelScope.launch { cards.setFavorite(cardId, !card.isFavorite) }
    }

    fun toggleArchived() {
        val card = uiState.value.card ?: return
        viewModelScope.launch {
            cards.setArchived(cardId, !card.isArchived)
            // Archiving leaves the screen (the list offers to undo it); unarchiving stays here.
            if (!card.isArchived) archivedHere.value = true
        }
    }

    fun delete() {
        viewModelScope.launch {
            cards.delete(cardId)
            deleted.value = true
        }
    }
}
