package io.github.vferries.encarte.importing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vferries.encarte.R
import io.github.vferries.encarte.core.ui.BarcodeImage

/** "Choose a code": the codes a PDF holds, drawn, so the user picks the card's. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportChoiceScreen(codes: List<FoundCode>, onBack: () -> Unit, onChoose: (FoundCode) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_choice_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { padding ->
        // Large screens: cap the content width instead of stretching the rows.
        LazyColumn(Modifier.padding(padding).fillMaxSize().wrapContentWidth().widthIn(max = 640.dp)) {
            items(codes) { code ->
                CodeRow(code, onClick = { onChoose(code) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CodeRow(code: FoundCode, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val format = code.format
        // A code Encarté cannot draw shows its value alone.
        if (format != null) BarcodeImage(code.value, format, Modifier.widthIn(max = 280.dp).fillMaxWidth())
        Text(code.value, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(stringResource(R.string.import_choice_page, code.page), format?.label).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
