package com.example.adobongkangkong.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.backup.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val repo: BackupRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = "Exporting backup…", error = null) }
            runCatching {
                repo.exportBackup(uri)
            }.onSuccess {
                _state.update { it.copy(isBusy = false, message = "Backup exported.", error = null) }
            }.onFailure { t ->
                _state.update {
                    it.copy(
                        isBusy = false,
                        message = null,
                        error = t.message ?: "Export failed"
                    )
                }
            }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = "Importing backup…", error = null) }
            runCatching {
                repo.importBackup(uri)
            }.onSuccess {
                // After restore, safest is app restart so Room reopens restored DB.
                _state.update {
                    it.copy(
                        isBusy = false,
                        message = "Backup imported. Restart required.",
                        error = null,
                        needsRestart = true
                    )
                }
            }.onFailure { t ->
                _state.update {
                    it.copy(
                        isBusy = false,
                        message = null,
                        error = t.message ?: "Import failed"
                    )
                }
            }
        }
    }

    fun dismissRestartPrompt() {
        _state.update { it.copy(needsRestart = false) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, error = null) }
    }
}

data class BackupUiState(
    val isBusy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val needsRestart: Boolean = false
)