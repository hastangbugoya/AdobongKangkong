package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.DailyNutritionSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * ObserveDailyNutritionSummaryUseCase
 *
 * ## Purpose
 * Produces a unified, reactive [DailyNutritionSummary] for a given date by combining:
 * - Daily nutrient totals (consumed amounts), and
 * - Daily nutrient statuses (LOW / OK / HIGH relative to user targets).
 *
 * ## Rationale
 * Many UI surfaces (dashboard, day view, trends) need both:
 * - Raw totals (calories, macros, micronutrients), and
 * - Evaluated target compliance state.
 *
 * Rather than requiring callers to subscribe to multiple flows and manually synchronize them,
 * this use case provides a single compositional stream that emits a coherent summary object.
 *
 * This ensures:
 * - Consistent combination logic,
 * - Single reactive pipeline,
 * - Clean ViewModel wiring.
 *
 * ## Behavior
 * - Subscribes to:
 *   1) [ObserveDailyNutritionTotalsUseCase] → actual consumption totals
 *   2) [ObserveDailyNutrientStatusesUseCase] → evaluated target statuses
 * - Emits a new [DailyNutritionSummary] whenever either totals OR statuses change.
 *
 * ## Important rules
 * - Day membership is defined by the underlying totals use case (logDateIso-based).
 * - This use case does not compute totals or statuses itself; it only combines them.
 * - Ordering and evaluation rules are delegated to their respective use cases.
 *
 * ## Parameters
 * @param date The calendar day being summarized.
 * @param zoneId Used by downstream totals logic to determine correct day boundaries.
 *
 * ## Return
 * @return Flow<DailyNutritionSummary>
 * Emits whenever:
 * - A log entry changes totals,
 * - User nutrient targets change,
 * - Any underlying reactive source updates.
 *
 * ## Edge cases
 * - If no logs exist for the day → totals may be zeroed, statuses may indicate LOW or NO_TARGET.
 * - If no nutrient targets are configured → statuses may be empty.
 * - If targets change mid-day → summary updates immediately.
 */
class ObserveDailyNutritionSummaryUseCase @Inject constructor(
    private val totals: ObserveDailyNutritionTotalsUseCase,
    private val statuses: ObserveDailyNutrientStatusesUseCase
) {

    operator fun invoke(
        date: LocalDate,
        zoneId: ZoneId
    ): Flow<DailyNutritionSummary> =
        combine(
            totals(date, zoneId),
            statuses(date, zoneId)
        ) { t, s ->
            DailyNutritionSummary(
                totals = t,
                statuses = s
            )
        }
}

/**
 * FUTURE AI ASSISTANT NOTES
 *
 * - Two-KDoc standard:
 *   - Top: dev-facing purpose, composition rationale, invariants.
 *   - Bottom: constraints for automated edits.
 *
 * - This use case is purely compositional.
 *   Do NOT:
 *   - Add business logic here,
 *   - Recompute totals,
 *   - Recompute statuses,
 *   - Introduce filtering or transformation rules.
 *
 * - If new fields are added to DailyNutritionSummary:
 *   - Extend this combine block in a backward-compatible way.
 *
 * - Keep this reactive.
 *   If converting to non-Flow architecture in the future, ensure atomic consistency between
 *   totals and statuses is preserved.
 *
 * - Day boundaries must remain controlled by ObserveDailyNutritionTotalsUseCase.
 *   Do not introduce timestamp filtering logic here.
 */