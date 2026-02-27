package com.example.adobongkangkong.ui.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.prefs.FirstRunPrefs
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.usecase.ImportFoodsCsvUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val prefs: FirstRunPrefs,
    private val foodRepository: FoodRepository,
    private val importFoodsCsv: ImportFoodsCsvUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(StartupUiState())
    val state: StateFlow<StartupUiState> = _state

    fun start() {
        viewModelScope.launch {
            try {
                _state.value = StartupUiState(
                    isWorking = true,
                    message = "Preparing database…"
                )

                val hasFoods = !foodRepository.isFoodsEmpty()

                if (!hasFoods) {
                    _state.value = StartupUiState(
                        isWorking = true,
                        message = "Importing foods…"
                    )

                    importFoodsCsv(
                        assetFileName = "foods.csv",
                        skipIfFoodsExist = true
                    )

                    prefs.setImportDone(true)
                }

                _state.value = StartupUiState(
                    isWorking = false,
                    isDone = true,
                    message = "Ready"
                )
            } catch (t: Throwable) {
                _state.value = StartupUiState(
                    isWorking = false,
                    error = t.message ?: "Startup failed"
                )
            }
        }
    }

    fun retry() {
        if (state.value.isWorking) return
        start()
    }
}
