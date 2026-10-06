package io.github.vferries.encarte.groups

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vferries.encarte.cards.list.matches
import io.github.vferries.encarte.core.data.Card
import io.github.vferries.encarte.core.data.CardGroup
import io.github.vferries.encarte.core.data.CardRepository
import io.github.vferries.encarte.core.data.GroupRepository
import io.github.vferries.encarte.core.text.normalizedForMatching
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator

private const val TAG = "GroupCardsViewModel"

data class GroupCardRow(val card: Card, val isMember: Boolean)

data class GroupCardsUiState(
    val isLoading: Boolean = true,
    /** Null once loaded: the group no longer exists. */
    val group: CardGroup? = null,
    val rows: List<GroupCardRow> = emptyList(),
)

class GroupCardsViewModel(
    private val groupId: Long,
    cards: CardRepository,
    private val groups: GroupRepository,
    collator: Collator,
) : ViewModel() {

    val query = TextFieldState()

    // Archived cards last: they are the least likely to be put in a group.
    private val order = compareBy<Card> { it.isArchived }.then(compareBy(collator) { it.storeName })

    val uiState: StateFlow<GroupCardsUiState> = combine(
        groups.observeGroup(groupId),
        cards.observeCards(),
        groups.observeMemberships(),
        snapshotFlow { query.text.toString() },
    ) { group, all, memberships, text ->
        val normalizedQuery = text.normalizedForMatching()
        GroupCardsUiState(
            isLoading = false,
            group = group,
            rows = all.filter { it.matches(normalizedQuery) }
                .sortedWith(order)
                .map { GroupCardRow(it, groupId in memberships[it.id].orEmpty()) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupCardsUiState())

    /** Written at once, like the favorite star: there is no Save button to forget. */
    fun setMember(cardId: Long, member: Boolean) {
        viewModelScope.launch {
            try {
                groups.setMembership(cardId, groupId, member)
            } catch (e: SQLiteConstraintException) {
                // The group was deleted meanwhile (foreign key): the screen is about to close, nothing to tick.
                Log.w(TAG, "Cannot tick card $cardId: group $groupId no longer exists", e)
            }
        }
    }
}
