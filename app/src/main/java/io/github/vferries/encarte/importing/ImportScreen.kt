package io.github.vferries.encarte.importing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vferries.encarte.R

/** Shows "Reading the file…" until the received file is read, then hands its outcome over once. */
@Composable
fun ImportRoute(viewModel: ImportViewModel, onRead: (ImportOutcome) -> Unit) {
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()
    LaunchedEffect(outcome) {
        outcome?.let(onRead)
    }
    ImportScreen()
}

@Composable
fun ImportScreen() {
    Scaffold { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(stringResource(R.string.import_reading))
        }
    }
}
