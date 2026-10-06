package io.github.vferries.encarte.cards.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vferries.encarte.R
import io.github.vferries.encarte.core.data.CardGroup
import io.github.vferries.encarte.core.ui.EncarteDropdownMenu

/** "All", one chip per group, then "New group". With no group yet, only "New group" shows. */
@Composable
fun GroupChipRow(
    groups: List<CardGroup>,
    selected: CardGroup?,
    onSelect: (Long?) -> Unit,
    onNewGroup: () -> Unit,
    onChooseCards: (Long) -> Unit,
    onRename: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (groups.isNotEmpty()) {
            GroupChip(stringResource(R.string.group_all), isSelected = selected == null, onClick = { onSelect(null) })
            for (group in groups) {
                GroupChipWithMenu(group, group.id == selected?.id, onSelect, onChooseCards, onRename, onDelete)
            }
        }
        GroupChip(
            stringResource(R.string.group_new),
            isSelected = false,
            onClick = onNewGroup,
            role = Role.Button,
            leadingIcon = R.drawable.ic_add,
        )
    }
}

@Composable
private fun GroupChipWithMenu(
    group: CardGroup,
    selected: Boolean,
    onSelect: (Long?) -> Unit,
    onChooseCards: (Long) -> Unit,
    onRename: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        GroupChip(group.name, selected, onClick = { onSelect(group.id) }, onLongClick = { menuOpen = true })
        EncarteDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.group_choose_cards)) },
                onClick = {
                    menuOpen = false
                    onChooseCards(group.id)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_rename)) },
                onClick = {
                    menuOpen = false
                    onRename(group.id)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = {
                    menuOpen = false
                    onDelete(group.id)
                },
            )
        }
    }
}

/** Material 3 chips have no long click: this one does, and labels it for accessibility services. */
@Composable
private fun GroupChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    role: Role = Role.Tab,
    leadingIcon: Int? = null,
) {
    val shape = MaterialTheme.shapes.small
    val longClickLabel = stringResource(R.string.group_options)
    Surface(
        shape = shape,
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 32.dp)
            .widthIn(max = 200.dp)
            .clip(shape)
            .semantics { selected = isSelected }
            .combinedClickable(
                role = role,
                onLongClickLabel = if (onLongClick != null) longClickLabel else null,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) Icon(painterResource(leadingIcon), contentDescription = null, Modifier.size(18.dp))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
        }
    }
}
