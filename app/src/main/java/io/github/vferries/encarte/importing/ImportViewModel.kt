package io.github.vferries.encarte.importing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Reads a received file once: a rotation keeps the reading going, and the file is deleted afterwards. */
class ImportViewModel(
    fileName: String,
    /** FileImport.importReceived: analyses the copy, then deletes it. */
    read: suspend (fileName: String) -> ImportOutcome,
) : ViewModel() {
    private val _outcome = MutableStateFlow<ImportOutcome?>(null)
    val outcome: StateFlow<ImportOutcome?> = _outcome.asStateFlow()

    init {
        viewModelScope.launch { _outcome.value = read(fileName) }
    }
}
