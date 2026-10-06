package io.github.vferries.encarte.groups

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.vferries.encarte.R
import io.github.vferries.encarte.core.data.GroupNameResult
import io.github.vferries.encarte.core.ui.EncarteAlertDialog
import kotlinx.coroutines.launch

/** Creates or renames a group. The repository judges the name; the dialog shows its answer under the field. */
@Composable
fun GroupNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    onConfirm: suspend (String) -> GroupNameResult,
    onSaved: (groupId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val name = rememberTextFieldState(initialName)
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val shownError = error
    EncarteAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                state = name,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.group_name_label)) },
                isError = shownError != null,
                supportingText = if (shownError != null) ({ Text(stringResource(shownError)) }) else null,
                lineLimits = TextFieldLineLimits.SingleLine,
            )
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            when (val result = onConfirm(name.text.toString())) {
                                is GroupNameResult.Saved -> onSaved(result.id)
                                GroupNameResult.Blank -> error = R.string.group_name_blank
                                GroupNameResult.Duplicate -> error = R.string.group_name_duplicate
                            }
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
