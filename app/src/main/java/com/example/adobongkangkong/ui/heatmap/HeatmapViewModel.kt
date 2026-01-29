package com.example.adobongkangkong.ui.heatmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.ui.dashboard.pinned.model.DashboardPinOption
import com.example.adobongkangkong.ui.dashboard.pinned.usecase.ObserveDashboardPinOptionsUseCase
import com.example.adobongkangkong.ui.dashboard.pinned.usecase.ObservePinnedNutrientsUseCase
import com.example.adobongkangkong.ui.heatmap.model.HeatmapDay
import com.example.adobongkangkong.ui.heatmap.model.TargetRange
import com.example.adobongkangkong.ui.heatmap.usecase.BuildMonthlyNutrientHeatmapUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
@HiltViewModel
class HeatmapViewModel @Inject constructor(
    private val buildMonthlyHeatmap: BuildMonthlyNutrientHeatmapUseCase,
    private val observePinnedNutrients: ObservePinnedNutrientsUseCase,
    private val observePinOptions: ObserveDashboardPinOptionsUseCase,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    private val _selectedNutrient = MutableStateFlow<NutrientKey?>(null)
    val selectedNutrient: StateFlow<NutrientKey?> = _selectedNutrient

    private val _selectedDay = MutableStateFlow<HeatmapDay?>(null)
    val selectedDay: StateFlow<HeatmapDay?> = _selectedDay

    val nutrientOptions: StateFlow<List<DashboardPinOption>> =
        observePinOptions()
            .stateIn(viewModelScope, WhileSubscribed(5_000), emptyList())

    private val resolvedNutrient: StateFlow<NutrientKey?> =
        combine(_selectedNutrient, observePinnedNutrients()) { selected, pinned ->
            selected ?: pinned.firstOrNull()
        }
            .distinctUntilChanged()
            .stateIn(viewModelScope, WhileSubscribed(5_000), null)

    val heatmapDays: StateFlow<List<HeatmapDay>> =
        combine(_month, resolvedNutrient) { m, k -> m to k }
            .flatMapLatest { (m, k) ->
                flow {
                    emit(
                        buildMonthlyHeatmap(
                            month = m,
                            zoneId = zoneId,
                            targetRange = TargetRange(min = null, target = null, max = null),
                            nutrientKey = k
                        )
                    )
                }
            }
            .stateIn(viewModelScope, WhileSubscribed(5_000), emptyList())

    fun goPrevMonth() { _month.update { it.minusMonths(1) } }
    fun goNextMonth() { _month.update { it.plusMonths(1) } }

    fun onDayClicked(day: HeatmapDay) {
        if (_selectedNutrient.value == null) return
        _selectedDay.value = day
    }

    fun dismissDayDetails() { _selectedDay.value = null }

    fun selectNutrient(key: NutrientKey?) {
        _selectedNutrient.value = key
    }
}
