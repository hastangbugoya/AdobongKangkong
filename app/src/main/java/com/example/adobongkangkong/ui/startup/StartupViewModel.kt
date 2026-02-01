package com.example.adobongkangkong.ui.startup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.prefs.FirstRunPrefs
import com.example.adobongkangkong.domain.usecase.ImportFoodsCsvUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StartupUiState(
    val isWorking: Boolean = true,
    val message: String = "Preparing…",
    val isDone: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val prefs: FirstRunPrefs,
    private val importFoodsCsv: ImportFoodsCsvUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(StartupUiState())
    val state: StateFlow<StartupUiState> = _state

    fun start() {
        Log.d("Meow", "StartupViewModel > start")
        viewModelScope.launch {
            try {
                val already = prefs.importDone.first()
                if (already) {
                    _state.value = StartupUiState(
                        isWorking = false,
                        isDone = true,
                        message = "Ready"
                    )
                    return@launch
                }

                _state.value = StartupUiState(
                    isWorking = true,
                    message = "Importing foods…"
                )
                Log.d("Meow", "StartupViewModel before import")
                val report = importFoodsCsv(
                    assetFileName = "foods.csv",
                    skipIfFoodsExist = false // important: first-run should force import
                )
                Log.d("Meow", "StartupViewModel after import")
                prefs.setImportDone(true)

                _state.value = StartupUiState(
                    isWorking = false,
                    isDone = true,
                    message = "Imported ${report.foodsInserted} foods"
                )
            } catch (t: Throwable) {
                _state.value = StartupUiState(
                    isWorking = false,
                    isDone = false,
                    message = "Import failed",
                    error = t.message ?: "Unknown error"
                )
            }
        }
    }

    // Optional: allow retry on failure
    fun retry() = start()
}
