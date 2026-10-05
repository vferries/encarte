package io.github.vferries.encarte.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vferries.encarte.BuildConfig
import io.github.vferries.encarte.R
import io.github.vferries.encarte.core.ui.EncarteAlertDialog
import io.github.vferries.encarte.lock.DeviceAuthenticator
import io.github.vferries.encarte.lock.isDeviceSecure
import java.io.IOException
import java.time.LocalDate

private const val TAG = "SettingsScreen"
private const val SOURCE_URL = "https://github.com/vferries/encarte"
private val IMPORT_MIME_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "text/*")

@Composable
fun SettingsRoute(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var deviceSecure by remember { mutableStateOf(context.isDeviceSecure()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { deviceSecure = context.isDeviceSecure() }
    val nfcSupported = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) {
            viewModel.cancelExport()
        } else {
            viewModel.exportTo {
                context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open export destination")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.startImport {
                context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open backup")
            }
        }
    }
    val promptTitle = stringResource(R.string.lock_enable_prompt_title)

    SettingsScreen(
        state = state,
        deviceSecure = deviceSecure,
        nfcSupported = nfcSupported,
        onBack = onBack,
        onLockChange = { enabled ->
            when {
                !enabled -> viewModel.setLockEnabled(false)
                activity == null -> Log.w(TAG, "No FragmentActivity host: cannot prompt for authentication")
                else -> DeviceAuthenticator(activity).authenticate(promptTitle, onSuccess = { viewModel.setLockEnabled(true) })
            }
        },
        onNfcBlockChange = viewModel::setNfcBlockEnabled,
        onExportConfirmed = { password ->
            viewModel.prepareExport(password)
            try {
                exportLauncher.launch("encarte-backup-${LocalDate.now()}.zip")
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "No document creator available", e)
                viewModel.exportUnavailable()
            }
        },
        onImport = {
            try {
                importLauncher.launch(IMPORT_MIME_TYPES)
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "No document picker available", e)
                viewModel.importUnavailable()
            }
        },
        onImportPassword = viewModel::submitImportPassword,
        onImportCancelled = viewModel::cancelImport,
        onMessageShown = viewModel::messageShown,
        onOpenSource = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri()))
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "No browser available", e)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    deviceSecure: Boolean,
    nfcSupported: Boolean,
    onBack: () -> Unit,
    onLockChange: (Boolean) -> Unit,
    onNfcBlockChange: (Boolean) -> Unit,
    onExportConfirmed: (CharArray?) -> Unit,
    onImport: () -> Unit,
    onImportPassword: (CharArray) -> Unit,
    onImportCancelled: () -> Unit,
    onMessageShown: () -> Unit,
    onOpenSource: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var askExportPassword by rememberSaveable { mutableStateOf(false) }
    var showLicenses by rememberSaveable { mutableStateOf(false) }

    val messageText = state.message?.let { messageText(it) }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize()
                // Large screens: cap the content width instead of stretching the list.
                .wrapContentWidth().widthIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            SectionTitle(R.string.settings_security)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_lock)) },
                supportingContent = {
                    Text(stringResource(if (deviceSecure) R.string.settings_lock_summary else R.string.settings_lock_unavailable))
                },
                trailingContent = {
                    // Still switchable off if the screen lock was removed after enabling it.
                    Switch(
                        checked = state.lockEnabled,
                        onCheckedChange = onLockChange,
                        enabled = deviceSecure || state.lockEnabled,
                    )
                },
            )
            if (nfcSupported) {
                SectionTitle(R.string.settings_checkout)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_nfc_block)) },
                    supportingContent = { Text(stringResource(R.string.settings_nfc_block_summary)) },
                    trailingContent = { Switch(checked = state.nfcBlockEnabled, onCheckedChange = null) },
                    // The whole row toggles: a larger target, announced once as a switch.
                    modifier = Modifier.toggleable(
                        value = state.nfcBlockEnabled,
                        role = Role.Switch,
                        onValueChange = onNfcBlockChange,
                    ),
                )
            }
            SectionTitle(R.string.settings_backup)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_export)) },
                supportingContent = { Text(stringResource(R.string.settings_export_summary)) },
                modifier = Modifier.clickable(enabled = !state.busy) { askExportPassword = true },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_import)) },
                supportingContent = { Text(stringResource(R.string.settings_import_summary)) },
                modifier = Modifier.clickable(enabled = !state.busy, onClick = onImport),
            )
            SectionTitle(R.string.settings_about)
            ListItem(headlineContent = { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) })
            ListItem(headlineContent = { Text(stringResource(R.string.settings_license)) })
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_source_code)) },
                supportingContent = { Text(SOURCE_URL) },
                modifier = Modifier.clickable(onClick = onOpenSource),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_third_party)) },
                modifier = Modifier.clickable { showLicenses = true },
            )
        }
    }

    if (askExportPassword) {
        ExportPasswordDialog(
            onConfirm = { password ->
                askExportPassword = false
                onExportConfirmed(password)
            },
            onDismiss = { askExportPassword = false },
        )
    }
    val prompt = state.passwordPrompt
    if (prompt != null) {
        ImportPasswordDialog(retry = prompt == PasswordPrompt.RETRY, onSubmit = onImportPassword, onDismiss = onImportCancelled)
    }
    if (showLicenses) {
        EncarteAlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text(stringResource(R.string.settings_third_party)) },
            text = { Text(stringResource(R.string.third_party_licenses)) },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
private fun messageText(message: BackupMessage): String = when (message) {
    is BackupMessage.Exported -> pluralStringResource(R.plurals.export_success, message.count, message.count)
    is BackupMessage.Imported -> buildList {
        add(pluralStringResource(R.plurals.import_success_imported, message.imported, message.imported))
        if (message.skipped > 0) add(pluralStringResource(R.plurals.import_success_skipped, message.skipped, message.skipped))
    }.joinToString(", ")
    BackupMessage.ExportFailed -> stringResource(R.string.export_error_io)
    BackupMessage.ImportInvalid -> stringResource(R.string.import_error_invalid)
    is BackupMessage.ImportUnsupported -> stringResource(R.string.import_error_version, message.version)
    BackupMessage.ImportFailed -> stringResource(R.string.import_error_io)
}

@Composable
private fun SectionTitle(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun ExportPasswordDialog(onConfirm: (CharArray?) -> Unit, onDismiss: () -> Unit) {
    val password = remember { TextFieldState() }
    val confirmation = remember { TextFieldState() }
    val mismatch = password.text.isNotEmpty() && confirmation.text.isNotEmpty() && password.text.toString() != confirmation.text.toString()
    val valid = password.text.isNotEmpty() && password.text.toString() == confirmation.text.toString()
    EncarteAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.export_password_body))
                OutlinedSecureTextField(state = password, label = { Text(stringResource(R.string.field_password)) })
                OutlinedSecureTextField(
                    state = confirmation,
                    label = { Text(stringResource(R.string.field_password_confirm)) },
                    isError = mismatch,
                    supportingText = if (mismatch) ({ Text(stringResource(R.string.error_passwords_differ)) }) else null,
                )
            }
        },
        // Three actions in one end-aligned row that wraps on narrow screens instead of overflowing.
        confirmButton = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = { onConfirm(null) }) { Text(stringResource(R.string.action_export_without_password)) }
                TextButton(onClick = { onConfirm(password.takeChars()); confirmation.clearText() }, enabled = valid) {
                    Text(stringResource(R.string.action_export))
                }
            }
        },
    )
}

@Composable
private fun ImportPasswordDialog(retry: Boolean, onSubmit: (CharArray) -> Unit, onDismiss: () -> Unit) {
    val password = remember { TextFieldState() }
    EncarteAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (retry) R.string.import_wrong_password else R.string.import_password_body))
                OutlinedSecureTextField(
                    state = password,
                    label = { Text(stringResource(R.string.field_password)) },
                    isError = retry,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(password.takeChars()) }, enabled = password.text.isNotEmpty()) {
                Text(stringResource(R.string.action_import_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Hands the password over as a CharArray (wiped by its consumer) and clears the field. */
private fun TextFieldState.takeChars(): CharArray = text.toString().toCharArray().also { clearText() }
