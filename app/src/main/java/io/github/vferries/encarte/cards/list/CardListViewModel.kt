package io.github.vferries.encarte.cards.list

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vferries.encarte.core.data.Card
import io.github.vferries.encarte.core.data.CardRepository
import io.github.vferries.encarte.core.prefs.SettingsRepository
import io.github.vferries.encarte.core.prefs.SortOrder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.Collator

data class CardTileModel(val card: Card, val image: File?)

data class CardListUiState(
    val isLoading: Boolean = true,
    val sortOrder: SortOrder = SortOrder.NAME,
    val favorites: List<CardTileModel> = emptyList(),
    val others: List<CardTileModel> = emptyList(),
    val hasCards: Boolean = false,
)

class CardListViewModel(
    private val cards: CardRepository,
    private val settings: SettingsRepository,
    private val collator: Collator,
) : ViewModel() {

    val query = TextFieldState()

    val uiState: StateFlow<CardListUiState> = combine(
        cards.observeCards(),
        settings.sortOrder,
        snapshotFlow { query.text.toString() },
    ) { all, order, text ->
        val sections = all.toSections(text, order, collator)
        CardListUiState(
            isLoading = false,
            sortOrder = order,
            favorites = sections.favorites.map(::tileModel),
            others = sections.others.map(::tileModel),
            hasCards = all.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardListUiState())

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch { settings.setSortOrder(order) }
    }

    private fun tileModel(card: Card) = CardTileModel(card, card.frontImage?.let(cards::imageFile))
}
