package com.example.adobongkangkong.ui.heatmap

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedDaysInMonthUseCase
import com.example.adobongkangkong.domain.usecase.ObserveUserNutrientTargetRangeUseCase
import com.example.adobongkangkong.ui.dashboard.pinned.model.DashboardPinOption
import com.example.adobongkangkong.ui.dashboard.pinned.usecase.ObserveDashboardPinOptionsUseCase
import com.example.adobongkangkong.ui.dashboard.pinned.usecase.ObservePinnedNutrientsUseCase
import com.example.adobongkangkong.ui.heatmap.model.HeatmapDay
import com.example.adobongkangkong.ui.heatmap.model.TargetRange
import com.example.adobongkangkong.ui.heatmap.usecase.BuildMonthlyNutrientHeatmapUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class HeatmapViewModel @Inject constructor(
    private val buildMonthlyHeatmap: BuildMonthlyNutrientHeatmapUseCase,
    private val observePinnedNutrients: ObservePinnedNutrientsUseCase,
    private val observePinOptions: ObserveDashboardPinOptionsUseCase,
    private val observeUserNutrientTargetRange: ObserveUserNutrientTargetRangeUseCase,
    private val observePlannedDaysInMonth: ObservePlannedDaysInMonthUseCase,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    private val _selectedNutrient = MutableStateFlow<NutrientKey?>(null)
    val selectedNutrient: StateFlow<NutrientKey?> = _selectedNutrient

    private val _selectedDay = MutableStateFlow<HeatmapDay?>(null)
    val selectedDay: StateFlow<HeatmapDay?> = _selectedDay

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate

    val nutrientOptions: StateFlow<List<DashboardPinOption>> =
        observePinOptions()
            .stateIn(viewModelScope, WhileSubscribed(5_000), emptyList())

    /**
     * Nullable resolved nutrient:
     * - user selection if set
     * - else first pinned nutrient if present
     * - else null (heatmap stays empty until a nutrient is available)
     */
    private val resolvedNutrient: StateFlow<NutrientKey?> =
        combine(_selectedNutrient, observePinnedNutrients()) { selected, pinned ->
            selected ?: pinned.firstOrNull()
        }
            .distinctUntilChanged()
            .stateIn(viewModelScope, WhileSubscribed(5_000), null)

    /**
     * Non-null resolved nutrient flow used for downstream pipelines that require a key.
     */
    private val resolvedNutrientFlow =
        resolvedNutrient
            .filterNotNull()
            .distinctUntilChanged()

    /**
     * Targets for the resolved nutrient (min/target/max).
     */
    private val targetRangeFlow: StateFlow<TargetRange> =
        resolvedNutrientFlow
            .flatMapLatest { key -> observeUserNutrientTargetRange(key) }
            .stateIn(
                viewModelScope,
                WhileSubscribed(5_000),
                TargetRange(min = null, target = null, max = null)
            )

    val heatmapDays: StateFlow<List<HeatmapDay>> =
        combine(_month, resolvedNutrientFlow, targetRangeFlow) { m, k, tr -> Triple(m, k, tr) }
            .flatMapLatest { (m, k, tr) ->
                kotlinx.coroutines.flow.flow {
                    emit(
                        buildMonthlyHeatmap(
                            month = m,
                            zoneId = zoneId,
                            targetRange = tr,
                            nutrientKey = k
                        )
                    )
                }
            }
            .stateIn(viewModelScope, WhileSubscribed(5_000), emptyList())

    /**
     * IMPORTANT: this must be active so plannedDates.value is real when onDayClicked runs.
     * Using Eagerly avoids the “value stays emptySet until UI subscribes” pitfall.
     */
    val plannedDates: StateFlow<Set<LocalDate>> =
        _month
            .flatMapLatest { m -> observePlannedDaysInMonth(m) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun goPrevMonth() { _month.update { it.minusMonths(1) } }
    fun goNextMonth() { _month.update { it.plusMonths(1) } }

    fun onDayClicked(day: HeatmapDay) {
        val hasPlanner = plannedDates.value.contains(day.date)
        Log.d("Meow","HeatmapViewModel > onDayClicked $day hasPlanner: $hasPlanner")
        // Preserve old behavior: consumption-only days require explicit nutrient selection.
        // New behavior: planned-meal days can open even without a nutrient selected.
        if (_selectedNutrient.value == null && !hasPlanner) return
        _selectedDay.value = day
    }

    fun onDateClicked(date: LocalDate) {
        Log.d("Meow", "HeatmapViewModel > onDateClicked $date")
        if (!plannedDates.value.contains(date)) return
        _selectedDate.value = date
    }

    fun dismissDayDetails() {
        _selectedDay.value = null
        _selectedDate.value = null
    }

    fun selectNutrient(key: NutrientKey?) {
        _selectedNutrient.value = key
    }
}
