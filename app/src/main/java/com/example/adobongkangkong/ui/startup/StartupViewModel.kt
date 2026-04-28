package com.example.adobongkangkong.ui.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.core.log.MeowLog
import com.example.adobongkangkong.data.local.prefs.FirstRunPrefs
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.usecase.EnsureNutrientCatalogSeededUseCase
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
    private val importFoodsCsv: ImportFoodsCsvUseCase,
    private val ensureNutrientCatalogSeeded: EnsureNutrientCatalogSeededUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(StartupUiState())
    val state: StateFlow<StartupUiState> = _state

    fun start() {
        MeowLog.d("StartupViewModel> START")

        viewModelScope.launch {
            try {
                _state.value = StartupUiState(
                    isWorking = true,
                    message = "Preparing database…"
                )

                MeowLog.d("StartupViewModel> ensureNutrientCatalogSeeded START")
                ensureNutrientCatalogSeeded()
                MeowLog.d("StartupViewModel> ensureNutrientCatalogSeeded SUCCESS")

                val hasFoods = !foodRepository.isFoodsEmpty()
                MeowLog.d("StartupViewModel> hasFoods=$hasFoods")

                if (!hasFoods) {
                    _state.value = StartupUiState(
                        isWorking = true,
                        message = "Importing foods…"
                    )

                    MeowLog.d("StartupViewModel> importFoodsCsv START")

                    importFoodsCsv(
                        assetFileName = "foods.csv",
                        skipIfFoodsExist = true
                    )

                    MeowLog.d("StartupViewModel> importFoodsCsv SUCCESS")

                    prefs.setImportDone(true)
                    MeowLog.d("StartupViewModel> prefs.setImportDone SUCCESS")
                } else {
                    MeowLog.d("StartupViewModel> import skipped (foods already exist)")
                }

                _state.value = StartupUiState(
                    isWorking = false,
                    isDone = true,
                    message = "Ready"
                )

                MeowLog.d("StartupViewModel> SUCCESS")
            } catch (t: Throwable) {
                MeowLog.e("StartupViewModel> FAILED", t)

                _state.value = StartupUiState(
                    isWorking = false,
                    error = t.message ?: "Startup failed"
                )
            }
        }
    }

    fun retry() {
        if (state.value.isWorking) {
            MeowLog.d("StartupViewModel> retry ignored (already working)")
            return
        }

        MeowLog.d("StartupViewModel> retry triggered")
        start()
    }
}