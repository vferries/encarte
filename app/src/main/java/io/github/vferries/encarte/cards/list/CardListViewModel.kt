package io.github.vferries.encarte.cards.list

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vferries.encarte.core.data.Card
import io.github.vferries.encarte.core.data.CardRepository
import io.github.vferries.encarte.core.data.ExpiryStatus
import io.github.vferries.encarte.core.data.expiryStatus
import io.github.vferries.encarte.core.prefs.SettingsRepository
import io.github.vferries.encarte.core.prefs.SortOrder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.Collator
import java.time.Clock
import java.time.LocalDate

data class CardTileModel(val card: Card, val image: File?, val expiry: ExpiryStatus = ExpiryStatus.None)

data class CardListUiState(
    val isLoading: Boolean = true,
    val sortOrder: SortOrder = SortOrder.NAME,
    val favorites: List<CardTileModel> = emptyList(),
    val others: List<CardTileModel> = emptyList(),
    val archived: List<CardTileModel> = emptyList(),
    val hasCards: Boolean = false,
)

class CardListViewModel(
    private val cards: CardRepository,
    private val settings: SettingsRepository,
    private val collator: Collator,
    private val clock: Clock,
) : ViewModel() {

    val query = TextFieldState()

    val uiState: StateFlow<CardListUiState> = combine(
        cards.observeCards(),
        settings.sortOrder,
        snapshotFlow { query.text.toString() },
    ) { all, order, text ->
        val sections = all.toSections(text, order, collator)
        val today = LocalDate.now(clock)
        CardListUiState(
            isLoading = false,
            sortOrder = order,
            favorites = sections.favorites.map { tileModel(it, today) },
            others = sections.others.map { tileModel(it, today) },
            archived = sections.archived.map { tileModel(it, today) },
            hasCards = all.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardListUiState())

    fun unarchive(id: Long) {
        viewModelScope.launch { cards.setArchived(id, false) }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch { settings.setSortOrder(order) }
    }

    private fun tileModel(card: Card, today: LocalDate) =
        CardTileModel(card, card.frontImage?.let(cards::imageFile), expiryStatus(card.expiresOn, today))
}
