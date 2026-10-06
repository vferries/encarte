package io.github.vferries.encarte.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.vferries.encarte.R
import io.github.vferries.encarte.core.data.CardGroup
import io.github.vferries.encarte.launcher.WidgetSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigScreen(groups: List<CardGroup>, onChoose: (WidgetSource) -> Unit, onClose: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_config_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item { SourceRow(stringResource(R.string.widget_favorites)) { onChoose(WidgetSource.Favorites) } }
            items(groups, key = { it.id }) { group -> SourceRow(group.name) { onChoose(WidgetSource.Group(group.id)) } }
        }
    }
}

@Composable
private fun SourceRow(label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
