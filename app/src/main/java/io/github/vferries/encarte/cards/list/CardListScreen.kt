package io.github.vferries.encarte.cards.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vferries.encarte.R
import io.github.vferries.encarte.core.prefs.SortOrder
import io.github.vferries.encarte.core.ui.CardTile
import io.github.vferries.encarte.lock.LocalContentCovered

@Composable
fun CardListRoute(
    viewModel: CardListViewModel,
    onOpenCard: (Long) -> Unit,
    onAddCard: () -> Unit,
    onOpenSettings: () -> Unit,
    onImport: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CardListScreen(
        state = state,
        query = viewModel.query,
        onSortOrderChange = viewModel::setSortOrder,
        onOpenCard = onOpenCard,
        onAddCard = onAddCard,
        onOpenSettings = onOpenSettings,
        onImport = onImport,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardListScreen(
    state: CardListUiState,
    query: TextFieldState,
    onSortOrderChange: (SortOrder) -> Unit,
    onOpenCard: (Long) -> Unit,
    onAddCard: () -> Unit,
    onOpenSettings: () -> Unit,
    onImport: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (state.hasCards) SortMenu(state.sortOrder, onSortOrderChange)
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.action_settings))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddCard,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.action_add_card)) },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.padding(padding))
            !state.hasCards -> EmptyState(onAddCard, onImport, Modifier.padding(padding))
            else -> CardGrid(state, query, onOpenCard, PaddingValues(16.dp), Modifier.padding(padding))
        }
    }
}

@Composable
private fun SortMenu(current: SortOrder, onSortOrderChange: (SortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_sort), stringResource(R.string.action_sort))
        }
        if (!LocalContentCovered.current) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                for ((order, label) in listOf(
                    SortOrder.NAME to R.string.sort_by_name,
                    SortOrder.RECENTLY_USED to R.string.sort_recently_used,
                )) {
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        leadingIcon = { RadioButton(selected = order == current, onClick = null) },
                        onClick = {
                            expanded = false
                            onSortOrderChange(order)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CardGrid(
    state: CardListUiState,
    query: TextFieldState,
    onOpenCard: (Long) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "search") {
            OutlinedTextField(
                state = query,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                lineLimits = TextFieldLineLimits.SingleLine,
            )
        }
        if (state.favorites.isEmpty() && state.others.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.no_search_results), Modifier.padding(vertical = 24.dp))
            }
        }
        section(R.string.section_favorites, state.favorites, onOpenCard)
        section(R.string.section_all_cards, state.others, onOpenCard, showHeader = state.favorites.isNotEmpty())
        // Keeps the last row clear of the floating action button.
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(72.dp)) }
    }
}

private fun LazyGridScope.section(
    title: Int,
    tiles: List<CardTileModel>,
    onOpenCard: (Long) -> Unit,
    showHeader: Boolean = true,
) {
    if (tiles.isEmpty()) return
    if (showHeader) {
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        }
    }
    items(tiles, key = { it.card.id }, contentType = { "card" }) { tile ->
        CardTile(tile.card, tile.image, onClick = { onOpenCard(tile.card.id) })
    }
}

@Composable
private fun EmptyState(onAddCard: () -> Unit, onImport: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.empty_body), textAlign = TextAlign.Center)
        Button(onClick = onAddCard, shape = MaterialTheme.shapes.small) { Text(stringResource(R.string.action_add_card)) }
        OutlinedButton(onClick = onImport, shape = MaterialTheme.shapes.small) { Text(stringResource(R.string.action_import)) }
    }
}
