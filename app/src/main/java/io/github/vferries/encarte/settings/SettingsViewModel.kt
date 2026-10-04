package io.github.vferries.encarte.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vferries.encarte.backup.BackupService
import io.github.vferries.encarte.backup.ExportResult
import io.github.vferries.encarte.backup.ImportResult
import io.github.vferries.encarte.core.prefs.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private const val TAG = "SettingsViewModel"

enum class PasswordPrompt { FIRST_TRY, RETRY }

sealed interface BackupMessage {
    data class Exported(val count: Int) : BackupMessage
    data class Imported(val imported: Int, val skipped: Int) : BackupMessage
    data object ExportFailed : BackupMessage
    data object ImportInvalid : BackupMessage
    data class ImportUnsupported(val version: Int) : BackupMessage
    data object ImportFailed : BackupMessage
}

data class SettingsUiState(
    val lockEnabled: Boolean = false,
    val busy: Boolean = false,
    val passwordPrompt: PasswordPrompt? = null,
    val message: BackupMessage? = null,
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val backup: BackupService,
) : ViewModel() {

    private val ui = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> = combine(ui, settings.lockEnabled) { state, lock ->
        state.copy(lockEnabled = lock)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Kept here (not in the UI) so a configuration change during the file picker can't drop it. */
    private var exportPassword: CharArray? = null
    private var exportPrepared = false
    private var importFile: File? = null

    fun setLockEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setLockEnabled(enabled) }
    }

    fun prepareExport(password: CharArray?) {
        cancelExport()
        exportPassword = password
        exportPrepared = true
    }

    fun exportUnavailable() {
        cancelExport()
        ui.update { it.copy(message = BackupMessage.ExportFailed) }
    }

    fun importUnavailable() {
        ui.update { it.copy(message = BackupMessage.ImportFailed) }
    }

    fun exportTo(open: () -> OutputStream) {
        if (!exportPrepared) {
            // After process death the picker result can arrive without its password: never export unencrypted.
            Log.w(TAG, "Export destination received without a prepared export")
            ui.update { it.copy(message = BackupMessage.ExportFailed) }
            return
        }
        val password = exportPassword
        exportPassword = null
        exportPrepared = false
        ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val message = when (val result = backup.export(open, password)) {
                    is ExportResult.Success -> BackupMessage.Exported(result.count)
                    ExportResult.IoError -> BackupMessage.ExportFailed
                }
                ui.update { it.copy(message = message) }
            } finally {
                password?.fill('\u0000')
                ui.update { it.copy(busy = false) }
            }
        }
    }

    fun cancelExport() {
        exportPassword?.fill('\u0000')
        exportPassword = null
        exportPrepared = false
    }

    fun startImport(open: () -> InputStream) {
        ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            val file = try {
                backup.copyToWorkFile(open)
            } catch (e: IOException) {
                Log.e(TAG, "Cannot copy the picked backup", e)
                ui.update { it.copy(busy = false, message = BackupMessage.ImportFailed) }
                return@launch
            } catch (e: SecurityException) {
                Log.e(TAG, "The picked backup is no longer readable", e)
                ui.update { it.copy(busy = false, message = BackupMessage.ImportFailed) }
                return@launch
            }
            importFile = file
            runImport(file, password = null)
        }
    }

    fun submitImportPassword(password: CharArray) {
        val file = importFile ?: return
        ui.update { it.copy(busy = true, passwordPrompt = null) }
        viewModelScope.launch {
            try {
                runImport(file, password)
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun cancelImport() {
        discardImportFile()
        ui.update { it.copy(passwordPrompt = null, busy = false) }
    }

    fun messageShown() {
        ui.update { it.copy(message = null) }
    }

    private suspend fun runImport(file: File, password: CharArray?) {
        when (val result = backup.import(file, password)) {
            ImportResult.PasswordRequired -> ui.update { it.copy(busy = false, passwordPrompt = PasswordPrompt.FIRST_TRY) }
            ImportResult.WrongPassword -> ui.update { it.copy(busy = false, passwordPrompt = PasswordPrompt.RETRY) }
            else -> {
                discardImportFile()
                ui.update { it.copy(busy = false, passwordPrompt = null, message = messageFor(result)) }
            }
        }
    }

    private fun messageFor(result: ImportResult): BackupMessage = when (result) {
        is ImportResult.Success -> BackupMessage.Imported(result.imported, result.skippedDuplicates)
        is ImportResult.UnsupportedVersion -> BackupMessage.ImportUnsupported(result.version)
        ImportResult.Invalid -> BackupMessage.ImportInvalid
        else -> BackupMessage.ImportFailed
    }

    private fun discardImportFile() {
        importFile?.let(backup::discard)
        importFile = null
    }

    override fun onCleared() {
        discardImportFile()
        exportPassword?.fill('\u0000')
    }
}
