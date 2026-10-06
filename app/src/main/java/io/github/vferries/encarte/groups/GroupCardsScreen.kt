package io.github.vferries.encarte.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vferries.encarte.R
import io.github.vferries.encarte.core.color.CardPalette

@Composable
fun GroupCardsRoute(viewModel: GroupCardsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // A deleted group (or a stale key restored after process death) has nothing left to edit.
    LaunchedEffect(state.isLoading, state.group) {
        if (!state.isLoading && state.group == null) onBack()
    }
    GroupCardsScreen(state, viewModel.query, onToggle = viewModel::setMember, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCardsScreen(
    state: GroupCardsUiState,
    query: TextFieldState,
    onToggle: (cardId: Long, member: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.group?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).consumeWindowInsets(padding).imePadding(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item(contentType = "search") {
                OutlinedTextField(
                    state = query,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
            }
            items(state.rows, key = { it.card.id }, contentType = { "card" }) { row -> CardRow(row, onToggle) }
        }
    }
}

@Composable
private fun CardRow(row: GroupCardRow, onToggle: (cardId: Long, member: Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = row.isMember, role = Role.Checkbox, onValueChange = { onToggle(row.card.id, it) })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(16.dp).background(Color(CardPalette.opaque(row.card.color)), CircleShape))
        Text(row.card.storeName, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Checkbox(checked = row.isMember, onCheckedChange = null)
    }
}
