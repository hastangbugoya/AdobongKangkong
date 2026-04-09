package com.example.adobongkangkong.ui.calendar

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.model.Nutrient
import com.example.adobongkangkong.domain.nutrition.MacroKeys
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedDaysInMonthUseCase
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedFoodNeedsUseCase
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedFoodTotalsUseCase
import com.example.adobongkangkong.domain.planner.usecase.PlannedFoodNeed
import com.example.adobongkangkong.domain.planner.usecase.PlannedFoodTotalNeed
import com.example.adobongkangkong.domain.repository.CalendarSuccessNutrientRepository
import com.example.adobongkangkong.domain.repository.IouRepository
import com.example.adobongkangkong.domain.repository.NutrientRepository
import com.example.adobongkangkong.domain.trend.model.TargetStatus
import com.example.adobongkangkong.domain.trend.usecase.ObserveDashboardNutrientsUseCase
import com.example.adobongkangkong.domain.usecase.ObserveDailyNutrientStatusesUseCase
import com.example.adobongkangkong.domain.usecase.ObserveDailyNutritionTotalsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlin.math.roundToInt
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

/**
 * Status used by the Calendar month grid to render a tiny icon per day.
 */
enum class DayIconStatus {
    NO_TARGETS,
    NO_DATA,
    OK,
    MISSED
}

/**
 * Calendar-only settings option for monthly success nutrient selection.
 *
 * Scope guard:
 * - Used by Calendar settings UI only.
 * - Intentionally independent from dashboard pin option models.
 */
data class CalendarSuccessOption(
    val key: NutrientKey,
    val displayName: String,
    val unit: String,
    val sortCategoryOrdinal: Int
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val observePlannedDaysInMonth: ObservePlannedDaysInMonthUseCase,
    private val observePlannedFoodNeeds: ObservePlannedFoodNeedsUseCase,
    private val observePlannedFoodTotals: ObservePlannedFoodTotalsUseCase,
    private val observeDailyNutrientStatuses: ObserveDailyNutrientStatusesUseCase,
    private val observeDailyNutritionTotals: ObserveDailyNutritionTotalsUseCase,
    private val iouRepository: IouRepository,
    private val calendarSuccessNutrientRepository: CalendarSuccessNutrientRepository,
    private val nutrientRepository: NutrientRepository,
    private val observeDashboardNutrients: ObserveDashboardNutrientsUseCase,
) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    private val _currentDate = MutableStateFlow(LocalDate.now())
    val currentDate: StateFlow<LocalDate> = _currentDate

    fun onScreenResumed() {
        _currentDate.value = LocalDate.now()
    }

    private val zoneId = ZoneId.systemDefault()

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate

    private val _graphWeekStart = MutableStateFlow(startOfWeek(LocalDate.now()))
    val graphWeekStart: StateFlow<LocalDate> = _graphWeekStart

    private val _settingsSheetOpen = MutableStateFlow(false)
    val settingsSheetOpen: StateFlow<Boolean> = _settingsSheetOpen

    private val dashboardNutrientKeys: StateFlow<Set<String>> =
        observeDashboardNutrients()
            .map { specs ->
                specs
                    .map { it.code.trim().uppercase() }
                    .toSet()
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptySet()
            )

    val calendarSuccessOptions: StateFlow<List<CalendarSuccessOption>> =
        combine(
            nutrientRepository.observeAllNutrients(),
            dashboardNutrientKeys
        ) { nutrients, dashboardKeys ->
            nutrients
                .filter { nutrient ->
                    nutrient.code.trim().uppercase() in dashboardKeys
                }
                .map { it.toCalendarSuccessOption() }
                .sortedWith(
                    compareBy<CalendarSuccessOption> { it.sortCategoryOrdinal }
                        .thenBy { it.displayName.lowercase() }
                )
        }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptyList()
            )

    val selectedCalendarSuccessKeys: StateFlow<Set<String>> =
        calendarSuccessNutrientRepository
            .observeSelectedKeys()
            .map { keys ->
                keys
                    .map { it.value.trim().uppercase() }
                    .toSet()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

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
     *
     * Calendar Success Revamp note:
     * - Monthly calendar success is filtered by calendar-selected nutrient keys only.
     * - If no keys are selected, default behavior uses the current dashboard nutrient set.
     * - Weekly graph and other calendar surfaces remain unchanged.
     */
    val dayIconStatusByDate: StateFlow<Map<LocalDate, DayIconStatus>> =
        _month
            .flatMapLatest { ym ->
                val days = (1..ym.lengthOfMonth()).map { day -> ym.atDay(day) }
                if (days.isEmpty()) return@flatMapLatest flowOf(emptyMap())

                val perDayFlows = days.map { date ->
                    combine(
                        observeDailyNutrientStatuses(date = date, zoneId = zoneId),
                        selectedCalendarSuccessKeys,
                        dashboardNutrientKeys
                    ) { statuses, selectedKeys, dashboardKeys ->
                        val filtered = statuses.filterForCalendarSuccess(
                            selectedKeys = selectedKeys,
                            defaultDashboardKeys = dashboardKeys
                        )
                        date to filtered.toDayIconStatus()
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
                    combine(
                        observeDailyNutritionTotals(date = date, zoneId = zoneId),
                        observeDailyNutrientStatuses(date = date, zoneId = zoneId),
                        iouRepository.observeForDate(date.toString())
                    ) { totals, statuses, ious ->
                        val map = totals.totalsByCode
                        val caloriesStatus = statuses.firstOrNull { it.nutrientCode == MacroKeys.CALORIES.value }
                        val proteinStatus = statuses.firstOrNull { it.nutrientCode == MacroKeys.PROTEIN.value }
                        val carbsStatus = statuses.firstOrNull { it.nutrientCode == MacroKeys.CARBS.value }
                        val fatStatus = statuses.firstOrNull { it.nutrientCode == MacroKeys.FAT.value }

                        CalendarWeeklyMacroDayUi(
                            date = date,
                            totalCalories = map[MacroKeys.CALORIES] ?: 0.0,
                            proteinG = map[MacroKeys.PROTEIN] ?: 0.0,
                            carbsG = map[MacroKeys.CARBS] ?: 0.0,
                            fatG = map[MacroKeys.FAT] ?: 0.0,

                            caloriesMinKcal = caloriesStatus?.min,
                            caloriesTargetKcal = caloriesStatus?.target,
                            caloriesMaxKcal = caloriesStatus?.max,

                            proteinMinG = proteinStatus?.min,
                            proteinTargetG = proteinStatus?.target,
                            proteinMaxG = proteinStatus?.max,

                            carbsMinG = carbsStatus?.min,
                            carbsTargetG = carbsStatus?.target,
                            carbsMaxG = carbsStatus?.max,

                            fatMinG = fatStatus?.min,
                            fatTargetG = fatStatus?.target,
                            fatMaxG = fatStatus?.max,

                            proteinStatus = proteinStatus?.status ?: TargetStatus.NO_TARGET,
                            carbsStatus = carbsStatus?.status ?: TargetStatus.NO_TARGET,
                            fatStatus = fatStatus?.status ?: TargetStatus.NO_TARGET,
                            iouCaloriesKcal = ious.sumOf { it.estimatedCaloriesKcal ?: 0.0 },
                            iouProteinG = ious.sumOf { it.estimatedProteinG ?: 0.0 },
                            iouCarbsG = ious.sumOf { it.estimatedCarbsG ?: 0.0 },
                            iouFatG = ious.sumOf { it.estimatedFatG ?: 0.0 },
                        )
                    }
                }

                combine(perDayFlows) { barsAny ->
                    @Suppress("UNCHECKED_CAST")
                    barsAny.toList() as List<CalendarWeeklyMacroDayUi>
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val graphCaloriesReference: StateFlow<CalendarCaloriesReferenceUi?> =
        _graphWeekStart
            .flatMapLatest { weekStart ->
                observeDailyNutrientStatuses(date = weekStart, zoneId = zoneId)
                    .map { statuses ->
                        val calories = statuses.firstOrNull { it.nutrientCode == MacroKeys.CALORIES.value }
                        when {
                            calories?.target != null -> CalendarCaloriesReferenceUi(
                                kind = CalendarCaloriesReferenceKind.TARGET,
                                calories = calories.target.roundToInt()
                            )

                            calories?.min != null -> CalendarCaloriesReferenceUi(
                                kind = CalendarCaloriesReferenceKind.MINIMUM,
                                calories = calories.min.roundToInt()
                            )

                            calories?.max != null -> CalendarCaloriesReferenceUi(
                                kind = CalendarCaloriesReferenceKind.MAXIMUM,
                                calories = calories.max.roundToInt()
                            )

                            else -> null
                        }
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

    fun openSettingsSheet() {
        _settingsSheetOpen.value = true
    }

    fun dismissSettingsSheet() {
        _settingsSheetOpen.value = false
    }

    fun onCalendarSuccessToggle(key: NutrientKey, enabled: Boolean) {
        viewModelScope.launch {
            val canonical = key.value.trim().uppercase()
            val updated = selectedCalendarSuccessKeys.value.toMutableSet()

            if (enabled) {
                updated.add(canonical)
            } else {
                updated.remove(canonical)
            }

            calendarSuccessNutrientRepository.setSelectedKeys(
                updated
                    .map { NutrientKey(it) }
                    .sortedBy { it.value }
            )
        }
    }

    fun clearCalendarSuccessSelectedKeys() {
        viewModelScope.launch {
            calendarSuccessNutrientRepository.clearSelectedKeys()
        }
    }

    fun goPrevGraphWeek() {
        _graphWeekStart.update { it.minusWeeks(1) }
    }

    fun goNextGraphWeek() {
        _graphWeekStart.update { it.plusWeeks(1) }
    }

    fun goToCurrentGraphWeek() {
        _graphWeekStart.value = startOfWeek(_currentDate.value)
    }

    // DEBUG
    private var debugJob: Job? = null
    fun debugFetchNextNDays(days: Int = 7) {
        debugJob?.cancel()

        debugJob = viewModelScope.launch {
            val start = _currentDate.value

            // --- NEEDS ---
            observePlannedFoodNeeds(
                startDate = start,
                days = days
            )
                .dropWhile { it.isEmpty() }
                .take(1)
                .collect { list ->
                    Log.d("PlannerNeeds", "NOT TOTALLED>")
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
                    Log.d("PlannerNeeds", "TOTALLED>")
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

private fun Nutrient.toCalendarSuccessOption(): CalendarSuccessOption =
    CalendarSuccessOption(
        key = NutrientKey(code),
        displayName = displayName,
        unit = unit.name.lowercase(),
        sortCategoryOrdinal = category.ordinal
    )

private fun List<com.example.adobongkangkong.domain.model.DailyNutrientStatus>.filterForCalendarSuccess(
    selectedKeys: Set<String>,
    defaultDashboardKeys: Set<String>
): List<com.example.adobongkangkong.domain.model.DailyNutrientStatus> {
    val activeKeys = if (selectedKeys.isEmpty()) {
        defaultDashboardKeys
    } else {
        selectedKeys
    }

    return filter { status ->
        status.nutrientCode.trim().uppercase() in activeKeys
    }
}

private fun List<com.example.adobongkangkong.domain.model.DailyNutrientStatus>.toDayIconStatus(): DayIconStatus {
    if (isEmpty()) return DayIconStatus.NO_TARGETS

    val targeted = filter { s -> (s.min != null) || (s.target != null) || (s.max != null) }
    if (targeted.isEmpty()) return DayIconStatus.NO_TARGETS

    val hasAnyConsumed = targeted.any { it.consumed != 0.0 }
    if (!hasAnyConsumed) return DayIconStatus.NO_DATA

    val allOk = targeted.all { it.status == TargetStatus.OK }
    return if (allOk) DayIconStatus.OK else DayIconStatus.MISSED
}

private fun startOfWeek(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))