package com.example.adobongkangkong.ui.calendar

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.nutrition.MacroKeys
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedDaysInMonthUseCase
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedFoodNeedsUseCase
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedFoodTotalsUseCase
import com.example.adobongkangkong.domain.planner.usecase.PlannedFoodNeed
import com.example.adobongkangkong.domain.planner.usecase.PlannedFoodTotalNeed
import com.example.adobongkangkong.domain.trend.model.TargetStatus
import com.example.adobongkangkong.domain.usecase.ObserveDailyNutritionTotalsUseCase
import com.example.adobongkangkong.domain.usecase.ObserveDailyNutrientStatusesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Status used by the Calendar month grid to render a tiny icon per day.
 */
enum class DayIconStatus {
    NO_TARGETS,
    NO_DATA,
    OK,
    MISSED
}

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val observePlannedDaysInMonth: ObservePlannedDaysInMonthUseCase,
    private val observePlannedFoodNeeds: ObservePlannedFoodNeedsUseCase,
    private val observePlannedFoodTotals: ObservePlannedFoodTotalsUseCase,
    private val observeDailyNutrientStatuses: ObserveDailyNutrientStatusesUseCase,
    private val observeDailyNutritionTotals: ObserveDailyNutritionTotalsUseCase,
) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    private val zoneId = ZoneId.systemDefault()

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate

    private val _graphWeekStart = MutableStateFlow(startOfWeek(LocalDate.now()))
    val graphWeekStart: StateFlow<LocalDate> = _graphWeekStart

    //DEBUG
    private val _debugNeeds = MutableStateFlow<List<PlannedFoodNeed>>(emptyList())
    val debugNeeds: StateFlow<List<PlannedFoodNeed>> = _debugNeeds

    private val _debugTotals = MutableStateFlow<List<PlannedFoodTotalNeed>>(emptyList())
    val debugTotals: StateFlow<List<PlannedFoodTotalNeed>> = _debugTotals
    //DEBUG


    /**
     * Keep this hot so UI can query quickly (dots/markers).
     */
    val plannedDates: StateFlow<Set<LocalDate>> =
        _month
            .flatMapLatest { m -> observePlannedDaysInMonth(m) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /**
     * Month grid icon status map.
     *
     * Computed as one flow per day (<=31) and then merged into a map.
     */
    val dayIconStatusByDate: StateFlow<Map<LocalDate, DayIconStatus>> =
        _month
            .flatMapLatest { ym ->
                val days = (1..ym.lengthOfMonth()).map { day -> ym.atDay(day) }
                if (days.isEmpty()) return@flatMapLatest flowOf(emptyMap())

                val perDayFlows = days.map { date ->
                    observeDailyNutrientStatuses(date = date, zoneId = zoneId)
                        .map { statuses ->
                            date to statuses.toDayIconStatus()
                        }
                }

                combine(perDayFlows) { pairsAny ->
                    @Suppress("UNCHECKED_CAST")
                    val pairs = pairsAny.toList() as List<Pair<LocalDate, DayIconStatus>>
                    pairs.toMap()
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val graphBars: StateFlow<List<CalendarWeeklyMacroDayUi>> =
        _graphWeekStart
            .flatMapLatest { weekStart ->
                val days = (0L..6L).map { weekStart.plusDays(it) }
                val perDayFlows = days.map { date ->
                    observeDailyNutritionTotals(date = date, zoneId = zoneId)
                        .map { totals ->
                            val map = totals.totalsByCode
                            CalendarWeeklyMacroDayUi(
                                date = date,
                                totalCalories = map[MacroKeys.CALORIES] ?: 0.0,
                                proteinG = map[MacroKeys.PROTEIN] ?: 0.0,
                                carbsG = map[MacroKeys.CARBS] ?: 0.0,
                                fatG = map[MacroKeys.FAT] ?: 0.0,
                            )
                        }
                }

                combine(perDayFlows) { barsAny ->
                    @Suppress("UNCHECKED_CAST")
                    barsAny.toList() as List<CalendarWeeklyMacroDayUi>
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val graphTargetCalories: StateFlow<Int?> =
        _graphWeekStart
            .flatMapLatest { weekStart ->
                observeDailyNutrientStatuses(date = weekStart, zoneId = zoneId)
                    .map { statuses ->
                        statuses.firstOrNull { it.nutrientCode == MacroKeys.CALORIES.value }
                            ?.target
                            ?.roundToInt()
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun goPrevMonth() { _month.update { it.minusMonths(1) } }
    fun goNextMonth() { _month.update { it.plusMonths(1) } }

    fun onDateClicked(date: LocalDate) {
        _selectedDate.value = date
    }

    fun dismissDayDetails() {
        _selectedDate.value = null
    }

    fun goPrevGraphWeek() {
        _graphWeekStart.update { it.minusWeeks(1) }
    }

    fun goNextGraphWeek() {
        _graphWeekStart.update { it.plusWeeks(1) }
    }

    fun goToCurrentGraphWeek() {
        _graphWeekStart.value = startOfWeek(LocalDate.now())
    }

    // DEBUG
    private var debugJob: Job? = null
    fun debugFetchNextNDays(days: Int = 7) {
        debugJob?.cancel()

        debugJob = viewModelScope.launch {
            val start = LocalDate.now()

            // --- NEEDS ---
            observePlannedFoodNeeds(
                startDate = start,
                days = days
            )
                .dropWhile { it.isEmpty() }
                .take(1)

                .collect { list ->
                    Log.d("PlannerNeeds","NOT TOTALLED>")
                    list.forEach {
                        Log.d(
                            "PlannerNeeds",
                            "date=${it.date} food=${it.foodName} g=${it.grams} s=${it.servings}"
                        )
                    }

                    _debugNeeds.value = list
                }

            // --- TOTALS ---
            observePlannedFoodTotals(
                startDate = start,
                days = days
            )
                .take(1)
                .collect { list ->
                    Log.d("PlannerNeeds","TOTALLED>")
                    list.forEach {
                        Log.d(
                            "PlannerNeeds",
                            "TOTALLED> food=${it.foodName} " +
                                    "next=${it.earliestNextPlannedDate} " +
                                    "g=${it.gramsTotal} " +
                                    "ml=${it.mlTotal} " +
                                    "s=${it.unconvertedServingsTotal}"
                        )
                    }

                    _debugTotals.value = list
                }
        }
    }
}

private fun List<com.example.adobongkangkong.domain.model.DailyNutrientStatus>.toDayIconStatus(): DayIconStatus {
    if (isEmpty()) return DayIconStatus.NO_TARGETS

    // Ignore entries that have no active min/target/max.
    val targeted = filter { s -> (s.min != null) || (s.target != null) || (s.max != null) }
    if (targeted.isEmpty()) return DayIconStatus.NO_TARGETS

    // Heuristic: if all consumed == 0, treat as no data.
    // If later you expose a "hasLogs" / "logCount" signal, switch to that.
    val hasAnyConsumed = targeted.any { it.consumed != 0.0 }
    if (!hasAnyConsumed) return DayIconStatus.NO_DATA

    val allOk = targeted.all { it.status == TargetStatus.OK }
    return if (allOk) DayIconStatus.OK else DayIconStatus.MISSED
}

private fun startOfWeek(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
