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
 * Observes which calendar days contain ≥ 1 PlannedMeal occurrence within a given month or date range.
 *
 * =============================================================================
 * WHAT THIS RETURNS
 * =============================================================================
 *
 * This use case exposes a reactive stream of:
 *
 *     Set<LocalDate>
 *
 * where each date represents a day that has at least one PlannedMealEntity persisted.
 *
 * This is intentionally a **marker-level abstraction**, not a full planner model.
 *
 *
 * =============================================================================
 * WHY THIS EXISTS
 * =============================================================================
 *
 * The planner and logging systems represent two different concepts:
 *
 * Planner → future intent (scheduled meals)
 * Logging → historical reality (consumed meals)
 *
 * The UI often needs a lightweight signal such as:
 *
 *     "Which days in this month have planned meals?"
 *
 * Typical consumers:
 *
 * • Planner month calendar view (dots under days)
 * • Calendar overview screens
 * • Planner heatmap / density indicators
 * • Quick navigation to planned days
 *
 * Returning only dates instead of full PlannedMealEntity objects provides:
 *
 * • minimal memory footprint
 * • fast recomposition in Compose
 * • reduced database → UI mapping overhead
 * • stable equality comparisons via Set
 *
 *
 * =============================================================================
 * ARCHITECTURAL ROLE
 * =============================================================================
 *
 * Repository layer:
 *     PlannedMealRepository.observeMealsInRange(...)
 *
 * Domain layer:
 *     This use case converts entities → LocalDate markers.
 *
 * UI / ViewModel layer:
 *     Observes returned Flow<Set<LocalDate>> for rendering indicators.
 *
 *
 * =============================================================================
 * DATA SOURCE CONTRACT
 * =============================================================================
 *
 * PlannedMealEntity.date is stored as ISO:
 *
 *     yyyy-MM-dd
 *
 * This use case parses that into LocalDate safely.
 *
 * Invalid dates are ignored instead of crashing.
 *
 *
 * =============================================================================
 * IMPORTANT BEHAVIOR GUARANTEES
 * =============================================================================
 *
 * Reactive:
 *
 * Emits updates automatically when planned meals change.
 *
 *
 * Deduplicated:
 *
 * Multiple meals on the same day still produce only one LocalDate.
 *
 *
 * Stable emissions:
 *
 * distinctUntilChanged() prevents redundant UI recompositions.
 *
 *
 * Fault-tolerant parsing:
 *
 * Invalid ISO strings are ignored safely.
 *
 *
 * Range-bounded:
 *
 * Queries only the requested month or date range.
 *
 *
 * =============================================================================
 * PERFORMANCE CHARACTERISTICS
 * =============================================================================
 *
 * Efficient because:
 *
 * • repository query is range-bounded
 * • mapping is O(N)
 * • returned data structure is small
 *
 *
 * =============================================================================
 * LIMITATIONS
 * =============================================================================
 *
 * This use case does NOT provide:
 *
 * • meal slots
 * • meal names
 * • meal counts per day
 * • planned item details
 *
 * It is intentionally marker-only.
 *
 *
 * =============================================================================
 * WHEN TO USE OTHER USE CASES INSTEAD
 * =============================================================================
 *
 * Need full day planner:
 *
 *     ObservePlannedDayUseCase
 *
 *
 * Need full series expansion:
 *
 *     EnsureSeriesOccurrencesWithinHorizonUseCase
 *
 *
 * Need planner items:
 *
 *     PlannedItemRepository.observeItemsForMeal(...)
 *
 */
class ObservePlannedDaysInMonthUseCase @Inject constructor(
    private val mealRepo: PlannedMealRepository
) {

    /**
     * Observes planned meal days for an entire calendar month.
     *
     * Convenience wrapper around observePlannedDatesInRange().
     *
     * Example:
     *
     * Month = 2026-02
     *
     * Returns:
     *
     *     {2026-02-03, 2026-02-05, 2026-02-17}
     *
     */
    operator fun invoke(month: YearMonth): Flow<Set<LocalDate>> {
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        return observePlannedDatesInRange(start, end)
    }

    /**
     * Observes planned meal days within an arbitrary inclusive date range.
     *
     * Useful for:
     *
     * • testing
     * • custom calendar ranges
     * • planner horizon previews
     * • weekly planner UIs
     *
     * Safe behavior:
     *
     * • Invalid entity.date values are ignored
     * • Duplicate days collapse into a single LocalDate
     * • Emits only when the resulting Set changes
     *
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
                    runCatching { LocalDate.parse(entity.date, fmt) }.getOrNull()
                }.toSet()
            }
            .distinctUntilChanged()
    }
}

/**
 * =============================================================================
 * FOR FUTURE AI / FUTURE DEVELOPER — invariants, gotchas, and evolution notes
 * =============================================================================
 *
 * WHY this exists separately from planner day loading
 *
 * PlannerDay loading is heavier and loads full entities and items.
 *
 * Month calendar indicators need only:
 *
 *     "Does anything exist on this day?"
 *
 * This avoids unnecessary DB joins and UI recompositions.
 *
 *
 * IMPORTANT invariant
 *
 * PlannedMealEntity.date MUST remain ISO yyyy-MM-dd.
 *
 * If date storage format changes, parsing here must be updated.
 *
 *
 * DO NOT add heavy logic here
 *
 * This use case must remain lightweight.
 *
 * Avoid:
 *
 * • loading planned items
 * • computing nutrition totals
 * • resolving recipes
 *
 *
 * FUTURE possible improvements
 *
 * 1) Add count per day:
 *
 *     Map<LocalDate, Int>
 *
 *
 * 2) Add Flow caching at repository level
 *
 *
 * 3) Merge planner + logging indicator layer for unified calendar view
 *
 *
 * EDGE CASES HANDLED
 *
 * • empty planner → emits empty set
 * • malformed date strings → ignored safely
 * • multiple meals same day → deduplicated
 *
 *
 * SAFE TO CALL FREQUENTLY
 *
 * This use case is designed for UI subscription.
 */