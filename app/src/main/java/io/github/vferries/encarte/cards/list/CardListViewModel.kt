package io.github.vferries.encarte.cards.list

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vferries.encarte.core.data.Card
import io.github.vferries.encarte.core.data.CardGroup
import io.github.vferries.encarte.core.data.CardRepository
import io.github.vferries.encarte.core.data.ExpiryStatus
import io.github.vferries.encarte.core.data.GroupNameResult
import io.github.vferries.encarte.core.data.GroupRepository
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

private const val KEY_SELECTED_GROUP = "selected_group"

data class CardTileModel(val card: Card, val image: File?, val expiry: ExpiryStatus = ExpiryStatus.None)

data class CardListUiState(
    val isLoading: Boolean = true,
    val sortOrder: SortOrder = SortOrder.NAME,
    val favorites: List<CardTileModel> = emptyList(),
    val others: List<CardTileModel> = emptyList(),
    val archived: List<CardTileModel> = emptyList(),
    val hasCards: Boolean = false,
    val groups: List<CardGroup> = emptyList(),
    /** Null shows every card. */
    val selectedGroup: CardGroup? = null,
    /** The selected group has no card at all, whatever the search says. */
    val selectedGroupIsEmpty: Boolean = false,
)

class CardListViewModel(
    private val cards: CardRepository,
    private val groups: GroupRepository,
    private val settings: SettingsRepository,
    private val collator: Collator,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val query = TextFieldState()

    // Saved state, not settings: it survives process death, and a cold start begins on "All" (spec §5.1).
    private val selectedGroupId = savedStateHandle.getStateFlow<Long?>(KEY_SELECTED_GROUP, null)

    private val groupFilter = combine(
        groups.observeGroups(collator),
        groups.observeMemberships(),
        selectedGroupId,
    ) { all, memberships, selectedId ->
        // A deleted group is no longer listed: the selection falls back to "All".
        val selected = all.firstOrNull { it.id == selectedId }
        GroupFilter(all, selected, selected?.let { group -> memberships.filterValues { group.id in it }.keys })
    }

    val uiState: StateFlow<CardListUiState> = combine(
        cards.observeCards(),
        settings.sortOrder,
        snapshotFlow { query.text.toString() },
        groupFilter,
    ) { all, order, text, filter ->
        val sections = all.toSections(text, order, collator, filter.members)
        val today = LocalDate.now(clock)
        CardListUiState(
            isLoading = false,
            sortOrder = order,
            favorites = sections.favorites.map { tileModel(it, today) },
            others = sections.others.map { tileModel(it, today) },
            archived = sections.archived.map { tileModel(it, today) },
            hasCards = all.isNotEmpty(),
            groups = filter.groups,
            selectedGroup = filter.selected,
            selectedGroupIsEmpty = filter.members?.isEmpty() == true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardListUiState())

    fun selectGroup(id: Long?) {
        savedStateHandle[KEY_SELECTED_GROUP] = id
    }

    suspend fun createGroup(name: String): GroupNameResult = groups.create(name)

    suspend fun renameGroup(id: Long, name: String): GroupNameResult = groups.rename(id, name)

    fun deleteGroup(id: Long) {
        if (selectedGroupId.value == id) selectGroup(null)
        viewModelScope.launch { groups.delete(id) }
    }

    fun unarchive(id: Long) {
        viewModelScope.launch { cards.setArchived(id, false) }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch { settings.setSortOrder(order) }
    }

    private fun tileModel(card: Card, today: LocalDate) =
        CardTileModel(card, card.frontImage?.let(cards::imageFile), expiryStatus(card.expiresOn, today))

    /** [members] is null when no group is selected. */
    private data class GroupFilter(val groups: List<CardGroup>, val selected: CardGroup?, val members: Set<Long>?)
}
