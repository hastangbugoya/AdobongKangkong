package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Observes which dates in a given [YearMonth] have at least one planned meal.
 *
 * Why this exists:
 * - Heatmap is consumption (logged totals).
 * - Planner is schedule (planned meals).
 * - We keep these domains separate and only expose a small "marker" surface to the UI/VM:
 *   "which days have planned meals?"
 *
 * Notes based strictly on the uploaded contracts:
 * - We rely ONLY on [PlannedMealRepository.observeMealsInRange] and the stored [date] field on entities
 *   being ISO yyyy-MM-dd (as documented in PlannedMealEntity). :contentReference[oaicite:0]{index=0}
 * - This use case does *not* do any UI merging; it returns data suitable for UI indicators.
 */
class ObservePlannedDaysInMonthUseCase @Inject constructor(
    private val mealRepo: PlannedMealRepository
) {

    /**
     * @return A stream of [Set] of dates that have >= 1 planned meal in the given month.
     */
    operator fun invoke(month: YearMonth): Flow<Set<LocalDate>> {
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        return observePlannedDatesInRange(start, end)
    }

    /**
     * Range variant (useful for testing or non-month UIs).
     *
     * @return A stream of [Set] of dates that have >= 1 planned meal within [start]..[end].
     */
    fun observePlannedDatesInRange(
        start: LocalDate,
        end: LocalDate
    ): Flow<Set<LocalDate>> {
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val startIso = start.format(fmt)
        val endIso = end.format(fmt)

        return mealRepo
            .observeMealsInRange(startDateIso = startIso, endDateIso = endIso)
            .map { meals ->
                meals.mapNotNull { entity ->
                    // PlannedMealEntity.date is stored as yyyy-MM-dd per docs in the uploaded entity. :contentReference[oaicite:1]{index=1}
                    runCatching { LocalDate.parse(entity.date, fmt) }.getOrNull()
                }.toSet()
            }
            .distinctUntilChanged()
    }
}
