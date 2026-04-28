package com.example.adobongkangkong.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.core.log.MeowLog
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
        MeowLog.d("BackupViewModel> exportTo START uri=$uri")

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isBusy = true,
                    message = "Exporting backup…",
                    error = null
                )
            }

            runCatching {
                MeowLog.d("BackupViewModel> repo.exportBackup START")
                repo.exportBackup(uri)
            }.onSuccess {
                MeowLog.d("BackupViewModel> repo.exportBackup SUCCESS uri=$uri")

                _state.update {
                    it.copy(
                        isBusy = false,
                        message = "Backup exported.",
                        error = null
                    )
                }

                MeowLog.d("BackupViewModel> exportTo SUCCESS")
            }.onFailure { t ->
                MeowLog.e("BackupViewModel> exportTo FAILED uri=$uri", t)

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
        MeowLog.d("BackupViewModel> importFrom START uri=$uri")

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isBusy = true,
                    message = "Importing backup…",
                    error = null
                )
            }

            runCatching {
                MeowLog.d("BackupViewModel> repo.importBackup START")
                repo.importBackup(uri)
            }.onSuccess {
                MeowLog.d("BackupViewModel> repo.importBackup SUCCESS uri=$uri")

                _state.update {
                    it.copy(
                        isBusy = false,
                        message = "Backup imported. Restart required.",
                        error = null,
                        needsRestart = true
                    )
                }

                MeowLog.d("BackupViewModel> importFrom SUCCESS needsRestart=true")
            }.onFailure { t ->
                MeowLog.e("BackupViewModel> importFrom FAILED uri=$uri", t)

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
        MeowLog.d("BackupViewModel> dismissRestartPrompt")
        _state.update { it.copy(needsRestart = false) }
    }

    fun clearMessage() {
        MeowLog.d("BackupViewModel> clearMessage")
        _state.update { it.copy(message = null, error = null) }
    }
}

data class BackupUiState(
    val isBusy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val needsRestart: Boolean = false
)