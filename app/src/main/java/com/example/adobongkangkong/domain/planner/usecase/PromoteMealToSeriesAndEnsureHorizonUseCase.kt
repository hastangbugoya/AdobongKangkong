package com.example.adobongkangkong.domain.planner.usecase

import javax.inject.Inject

/**
 * Promotes a single planned meal occurrence into a recurring series and ensures
 * future occurrences exist within a bounded horizon window.
 *
 * =============================================================================
 * WHAT THIS USE CASE DOES
 * =============================================================================
 *
 * This is a high-level orchestration use case that combines two lower-level operations:
 *
 * 1) Promote an existing planned meal into a series template
 *    via [CreateSeriesFromPlannedMealUseCase]
 *
 *    This:
 *    - creates planned_series
 *    - copies recurrence slot rules
 *    - copies planned items into planned_series_items (templates)
 *    - links the original meal as the anchor occurrence
 *
 * 2) Materialize future occurrences
 *    via [EnsureSeriesOccurrencesWithinHorizonUseCase]
 *
 *    This:
 *    - expands recurrence rules
 *    - creates planned_meal occurrences
 *    - copies template items into those meals
 *    - ensures planner UI has concrete future rows to display
 *
 *
 * =============================================================================
 * WHY THIS USE CASE EXISTS
 * =============================================================================
 *
 * Promotion and expansion are intentionally separate operations because:
 *
 * Promotion defines the recurrence rules.
 *
 * Expansion creates concrete planner rows.
 *
 * This separation allows:
 *
 * - lazy expansion
 * - partial expansion (window-based)
 * - safe re-expansion without duplication
 *
 * However, UI workflows almost always want BOTH immediately.
 *
 * Example:
 *
 * User taps:
 *
 *   "Repeat this meal every Monday"
 *
 * Expected behavior:
 *
 * - series created
 * - future planner days immediately populated
 *
 * This wrapper provides that behavior in one atomic-feeling operation.
 *
 *
 * =============================================================================
 * TERMINOLOGY
 * =============================================================================
 *
 * Anchor occurrence
 *
 * The original planned meal the user selected to become recurring.
 *
 *
 * Series template
 *
 * Stored in:
 *
 * planned_series
 * planned_series_slot_rules
 * planned_series_items
 *
 *
 * Occurrence
 *
 * Concrete planned_meal rows generated from the template.
 *
 *
 * Horizon window
 *
 * The forward-looking window where occurrences are ensured to exist.
 *
 * Default = 180 days.
 *
 *
 * =============================================================================
 * FLOW OVERVIEW
 * =============================================================================
 *
 * execute(mealId)
 *
 * ├─ CreateSeriesFromPlannedMealUseCase
 * │     creates template
 * │     returns (seriesId, anchorDate)
 * │
 * ├─ EnsureSeriesOccurrencesWithinHorizonUseCase
 * │     expands occurrences from anchorDate → horizon
 * │
 * └─ return seriesId
 *
 *
 * =============================================================================
 * IDEMPOTENCY AND SAFETY
 * =============================================================================
 *
 * Safe to call repeatedly.
 *
 * Occurrence creation is deduplicated at EnsureSeries layer.
 *
 * Will never duplicate planned_meal rows.
 *
 *
 * =============================================================================
 * ARCHITECTURAL ROLE
 * =============================================================================
 *
 * This is a domain orchestration use case.
 *
 * It does NOT:
 *
 * - directly access database
 * - build template itself
 * - expand occurrences itself
 *
 * Instead it coordinates specialized use cases.
 *
 *
 * =============================================================================
 * UI USAGE EXAMPLES
 * =============================================================================
 *
 * Planner screen:
 *
 * "Make this meal repeat weekly"
 *
 *
 * Context menu:
 *
 * "Convert to recurring"
 *
 *
 * Batch operations:
 *
 * Promote multiple meals.
 */
class PromoteMealToSeriesAndEnsureHorizonUseCase @Inject constructor(
    private val promote: CreateSeriesFromPlannedMealUseCase,
    private val ensure: EnsureSeriesOccurrencesWithinHorizonUseCase,
) {
    suspend fun execute(mealId: Long, horizonDays: Long = 180): Long {
        val result = promote.execute(mealId)
        val startIso = result.anchorDate.toString()
        val endIso = result.anchorDate.plusDays(horizonDays).toString()

        ensure.execute(
            seriesId = result.seriesId,
            startDateIso = startIso,
            endDateIso = endIso,
        )

        return result.seriesId
    }
}


/**
 * =============================================================================
 * FOR FUTURE AI ASSISTANT / FUTURE DEVELOPER
 * =============================================================================
 *
 * THIS USE CASE IS AN ORCHESTRATION LAYER.
 *
 * DO NOT merge logic from:
 *
 * - CreateSeriesFromPlannedMealUseCase
 * - EnsureSeriesOccurrencesWithinHorizonUseCase
 *
 * into this class.
 *
 * Each has separate responsibilities.
 *
 *
 * WHY THE HORIZON EXISTS
 *
 * We do NOT expand infinite occurrences.
 *
 * Doing so would:
 *
 * - explode database size
 * - slow queries
 * - break planner performance
 *
 *
 * Horizon expansion strategy enables:
 *
 * - predictable performance
 * - lazy expansion
 * - safe background expansion later
 *
 *
 * FUTURE IMPROVEMENTS
 *
 * Possible:
 *
 * Background worker periodically extends horizon.
 *
 *
 * User-configurable horizon window.
 *
 *
 * Partial expansion on demand (when navigating planner).
 *
 *
 * POTENTIAL PITFALL
 *
 * Do NOT remove EnsureSeries call here unless UI handles expansion explicitly.
 *
 * Otherwise newly created series will appear empty.
 *
 *
 * ARCHITECTURAL PRINCIPLE
 *
 * Series = template layer
 * Occurrences = materialized layer
 *
 * This use case bridges the two.
 *
 * Keep it thin and deterministic.
 */