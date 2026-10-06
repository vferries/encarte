package io.github.vferries.encarte.cards.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Button
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vferries.encarte.R
import io.github.vferries.encarte.core.data.CardGroup
import io.github.vferries.encarte.core.data.GroupNameResult
import io.github.vferries.encarte.core.prefs.SortOrder
import io.github.vferries.encarte.core.text.normalizedForMatching
import io.github.vferries.encarte.core.ui.CardTile
import io.github.vferries.encarte.core.ui.EncarteAlertDialog
import io.github.vferries.encarte.core.ui.EncarteDropdownMenu
import io.github.vferries.encarte.groups.GroupNameDialog
import io.github.vferries.encarte.importing.ImportFailure

@Composable
fun CardListRoute(
    viewModel: CardListViewModel,
    archivedNotice: Long?,
    onArchivedNoticeShown: () -> Unit,
    onOpenCard: (Long) -> Unit,
    onAddCard: (groupId: Long?) -> Unit,
    onOpenSettings: () -> Unit,
    onImport: () -> Unit,
    onChooseCards: (Long) -> Unit,
    importFailure: ImportFailure?,
    onImportFailureShown: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CardListScreen(
        state = state,
        query = viewModel.query,
        archivedNotice = archivedNotice,
        onSortOrderChange = viewModel::setSortOrder,
        onOpenCard = onOpenCard,
        onAddCard = { onAddCard(state.selectedGroup?.id) },
        onOpenSettings = onOpenSettings,
        onImport = onImport,
        onUndoArchive = viewModel::unarchive,
        onArchivedNoticeShown = onArchivedNoticeShown,
        onSelectGroup = viewModel::selectGroup,
        onCreateGroup = viewModel::createGroup,
        onRenameGroup = viewModel::renameGroup,
        onDeleteGroup = viewModel::deleteGroup,
        onChooseCards = onChooseCards,
        importFailure = importFailure,
        onImportFailureShown = onImportFailureShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardListScreen(
    state: CardListUiState,
    query: TextFieldState,
    archivedNotice: Long?,
    onSortOrderChange: (SortOrder) -> Unit,
    onOpenCard: (Long) -> Unit,
    onAddCard: () -> Unit,
    onOpenSettings: () -> Unit,
    onImport: () -> Unit,
    onUndoArchive: (Long) -> Unit,
    onArchivedNoticeShown: () -> Unit,
    onSelectGroup: (Long?) -> Unit,
    onCreateGroup: suspend (String) -> GroupNameResult,
    onRenameGroup: suspend (Long, String) -> GroupNameResult,
    onDeleteGroup: (Long) -> Unit,
    onChooseCards: (Long) -> Unit,
    /** Why a file sent by another app gave no card. */
    importFailure: ImportFailure? = null,
    onImportFailureShown: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    val archivedMessage = stringResource(R.string.card_archived)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(archivedNotice) {
        val id = archivedNotice ?: return@LaunchedEffect
        try {
            val result = snackbar.showSnackbar(archivedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) onUndoArchive(id)
        } finally {
            // Also when the list leaves the screen mid-notice: coming back later must not offer the undo again.
            onArchivedNoticeShown()
        }
    }
    val importMessage = importFailure?.let { stringResource(it.message) }
    LaunchedEffect(importFailure) {
        val message = importMessage ?: return@LaunchedEffect
        try {
            snackbar.showSnackbar(message)
        } finally {
            // Also when the list leaves the screen mid-message: coming back must not repeat it.
            onImportFailureShown()
        }
    }

    // Ids, not groups: they are saveable, and a group deleted meanwhile simply closes its dialog.
    var creatingGroup by rememberSaveable { mutableStateOf(false) }
    var renamingGroup by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingGroup by rememberSaveable { mutableStateOf<Long?>(null) }
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
        snackbarHost = { SnackbarHost(snackbar) },
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
            else -> CardGrid(state, query, onOpenCard, onChooseCards, PaddingValues(16.dp), Modifier.padding(padding)) {
                GroupChipRow(
                    groups = state.groups,
                    selected = state.selectedGroup,
                    onSelect = onSelectGroup,
                    onNewGroup = { creatingGroup = true },
                    onChooseCards = onChooseCards,
                    onRename = { renamingGroup = it },
                    onDelete = { deletingGroup = it },
                )
            }
        }
    }
    if (creatingGroup) {
        GroupNameDialog(
            title = stringResource(R.string.group_new),
            confirmLabel = stringResource(R.string.action_create),
            initialName = "",
            onConfirm = onCreateGroup,
            // Straight to its cards: a new group is created to be filled.
            onSaved = { id ->
                creatingGroup = false
                onChooseCards(id)
            },
            onDismiss = { creatingGroup = false },
        )
    }
    state.groups.firstOrNull { it.id == renamingGroup }?.let { group ->
        GroupNameDialog(
            title = stringResource(R.string.group_rename_title),
            confirmLabel = stringResource(R.string.action_rename),
            initialName = group.name,
            onConfirm = { name -> onRenameGroup(group.id, name) },
            onSaved = { renamingGroup = null },
            onDismiss = { renamingGroup = null },
        )
    }
    state.groups.firstOrNull { it.id == deletingGroup }?.let { group ->
        EncarteAlertDialog(
            onDismissRequest = { deletingGroup = null },
            title = { Text(stringResource(R.string.group_delete_title, group.name)) },
            text = { Text(stringResource(R.string.group_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deletingGroup = null
                    onDeleteGroup(group.id)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deletingGroup = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SortMenu(current: SortOrder, onSortOrderChange: (SortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_sort), stringResource(R.string.action_sort))
        }
        EncarteDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for ((order, label) in listOf(
                SortOrder.NAME to R.string.sort_by_name,
                SortOrder.RECENTLY_USED to R.string.sort_recently_used,
                SortOrder.EXPIRY to R.string.sort_expiry,
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

@Composable
private fun CardGrid(
    state: CardListUiState,
    query: TextFieldState,
    onOpenCard: (Long) -> Unit,
    onChooseCards: (Long) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    groupChips: @Composable () -> Unit,
) {
    var archivedOpen by rememberSaveable { mutableStateOf(false) }
    // Same normalization as the filter: a query of spaces or punctuation matches everything, so it is not a search.
    val searching = query.text.toString().normalizedForMatching().isNotEmpty()
    // While searching, archived matches show without opening the section.
    val showArchived = archivedOpen || searching
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
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "groups") { groupChips() }
        if (state.favorites.isEmpty() && state.others.isEmpty() && state.archived.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val group = state.selectedGroup
                if (group != null && state.selectedGroupIsEmpty) {
                    EmptyGroup(group, onChooseCards)
                } else {
                    Text(stringResource(R.string.no_search_results), Modifier.padding(vertical = 24.dp))
                }
            }
        }
        section(R.string.section_favorites, state.favorites, onOpenCard)
        section(R.string.section_all_cards, state.others, onOpenCard, showHeader = state.favorites.isNotEmpty())
        if (state.archived.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                ArchivedHeader(state.archived.size, expanded = showArchived, toggleEnabled = !searching, onToggle = { archivedOpen = !archivedOpen })
            }
            if (showArchived) cardTiles(state.archived, onOpenCard)
        }
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
    cardTiles(tiles, onOpenCard)
}

private fun LazyGridScope.cardTiles(tiles: List<CardTileModel>, onOpenCard: (Long) -> Unit) {
    items(tiles, key = { it.card.id }, contentType = { "card" }) { tile ->
        CardTile(tile.card, tile.image, onClick = { onOpenCard(tile.card.id) }, expiry = tile.expiry)
    }
}

@Composable
private fun ArchivedHeader(count: Int, expanded: Boolean, toggleEnabled: Boolean, onToggle: () -> Unit) {
    val stateText = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = toggleEnabled, role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = stateText }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.section_archived, count),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
            contentDescription = null,
        )
    }
}

@Composable
private fun EmptyGroup(group: CardGroup, onChooseCards: (Long) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.group_empty, group.name), textAlign = TextAlign.Center)
        OutlinedButton(onClick = { onChooseCards(group.id) }, shape = MaterialTheme.shapes.small) {
            Text(stringResource(R.string.group_choose_cards))
        }
    }
}

@Composable
private fun EmptyState(onAddCard: () -> Unit, onImport: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.illustration_card_fan),
            contentDescription = null, // decorative: the title right below says the list is empty
            // The column does not scroll: the illustration shrinks first so the buttons stay on screen in landscape.
            modifier = Modifier.weight(1f, fill = false).width(160.dp).aspectRatio(108f / 90f),
        )
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.empty_body), textAlign = TextAlign.Center)
        Button(onClick = onAddCard, shape = MaterialTheme.shapes.small) { Text(stringResource(R.string.action_add_card)) }
        OutlinedButton(onClick = onImport, shape = MaterialTheme.shapes.small) { Text(stringResource(R.string.action_import)) }
    }
}
