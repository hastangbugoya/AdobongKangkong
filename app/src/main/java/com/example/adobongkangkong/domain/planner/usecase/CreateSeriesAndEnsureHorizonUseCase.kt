package com.example.adobongkangkong.domain.planner.usecase

import java.time.LocalDate
import javax.inject.Inject

/**
 * Creates a Planned Series and immediately materializes (ensures) its occurrences within a horizon.
 *
 * ----------------------------------------------------------------------------
 * Purpose
 * ----------------------------------------------------------------------------
 *
 * A **Planned Series** is only the recurrence *definition* (rules + end condition + template source).
 * It does not automatically produce concrete PlannedMeal rows.
 *
 * This use case provides the common “one button” workflow:
 *
 * 1) Create the series definition (PlannedSeriesEntity + slot rules)
 * 2) Ensure concrete occurrences exist for an upcoming window (horizon)
 *
 * This is typically what the UI wants when the user taps:
 * “Make this a series” or “Create recurring meals”.
 *
 * ----------------------------------------------------------------------------
 * Rationale (why this use case exists)
 * ----------------------------------------------------------------------------
 *
 * Without this wrapper, callers would need to coordinate two separate operations:
 *
 * - CreatePlannedSeriesUseCase (definition write)
 * - EnsureSeriesOccurrencesWithinHorizonUseCase (materialization / expansion)
 *
 * That sequencing must remain consistent, because:
 *
 * - A newly created series should “show up” in the planner immediately.
 * - Planner UX should not depend on a separate manual refresh step.
 * - We want a single domain entry point that guarantees:
 *   “create series → you can see meals generated in the near future”.
 *
 * This wrapper also standardizes the default horizon window (180 days) so all call sites
 * behave consistently.
 *
 * ----------------------------------------------------------------------------
 * Behavior
 * ----------------------------------------------------------------------------
 *
 * - Creates the series via [CreatePlannedSeriesUseCase.execute].
 * - Builds a horizon range based on [anchorDate]:
 *   - startDateIso = anchorDate.toString()
 *   - endDateIso = anchorDate.plusDays(horizonDays).toString()
 * - Calls [EnsureSeriesOccurrencesWithinHorizonUseCase.execute] to generate/ensure the
 *   concrete PlannedMeal occurrences in that window.
 * - Returns the created seriesId.
 *
 * ----------------------------------------------------------------------------
 * Parameters
 * ----------------------------------------------------------------------------
 * @param input The recurrence definition and slot rules used to create the series.
 * @param anchorDate The LocalDate used as the start of the ensure horizon window.
 * @param horizonDays Length of the ensure window, in days. Defaults to 180.
 *
 * ----------------------------------------------------------------------------
 * Return
 * ----------------------------------------------------------------------------
 * @return seriesId of the newly created series.
 *
 * ----------------------------------------------------------------------------
 * Edge cases
 * ----------------------------------------------------------------------------
 *
 * - horizonDays == 0:
 *   Ensures occurrences only for anchorDate (start == end if plusDays(0) is used by callers).
 *
 * - Negative horizonDays:
 *   Not explicitly guarded here; callers should not pass negative values.
 *   (If needed later, add require(horizonDays >= 0) as a behavioral contract change.)
 *
 * - anchorDate is the UI-selected day:
 *   This use case assumes the caller has already chosen the correct calendar day.
 *   It must not derive dates from timestamps or time zones.
 *
 * ----------------------------------------------------------------------------
 * Pitfalls / gotchas
 * ----------------------------------------------------------------------------
 *
 * - Series definition vs occurrences:
 *   Do not assume CreatePlannedSeriesUseCase alone makes meals appear.
 *   This wrapper exists specifically because occurrences must be ensured separately.
 *
 * - ISO date model:
 *   Horizon boundaries are computed using LocalDate.toString() (yyyy-MM-dd).
 *   Do not switch to time-window filtering; planner membership is ISO-date-based.
 *
 * ----------------------------------------------------------------------------
 * Architectural rules
 * ----------------------------------------------------------------------------
 *
 * - This is an orchestration use case (domain-level composition).
 * - It may call other use cases, but must not:
 *   - mutate UI state
 *   - navigate
 *   - create logs/snapshots
 */
class CreateSeriesAndEnsureHorizonUseCase @Inject constructor(
    private val createSeries: CreatePlannedSeriesUseCase,
    private val ensureSeries: EnsureSeriesOccurrencesWithinHorizonUseCase,
) {
    suspend fun execute(
        input: CreatePlannedSeriesUseCase.Input,
        anchorDate: LocalDate,
        horizonDays: Long = 180
    ): Long {
        val seriesId = createSeries.execute(input)
        val startIso = anchorDate.toString()
        val endIso = anchorDate.plusDays(horizonDays).toString()
        ensureSeries.execute(seriesId = seriesId, startDateIso = startIso, endDateIso = endIso)
        return seriesId
    }
}

/**
 * =============================================================================
 * FOR FUTURE AI ASSISTANT — CreateSeriesAndEnsureHorizonUseCase invariants
 * =============================================================================
 *
 * Invariants (MUST NOT CHANGE)
 * - Must always create the series first, then ensure occurrences using the returned seriesId.
 * - Must compute horizon bounds using LocalDate.toString() ISO yyyy-MM-dd strings.
 * - Must not introduce timestamp/time-zone window logic for membership.
 * - Default horizonDays is 180 (planner UX expectation).
 *
 * Do not refactor / do not “improve”
 * - Do NOT fold EnsureSeriesOccurrencesWithinHorizonUseCase into CreatePlannedSeriesUseCase.
 *   Keeping “definition” vs “materialization” separate preserves clarity and enables
 *   future “regenerate occurrences” operations without rewriting series definitions.
 *
 * - Do NOT add logging/snapshot behavior here. Planner is intent, not immutable history.
 *
 * Architectural boundaries
 * - This file is orchestration-only: it coordinates existing domain use cases.
 * - Persistence boundaries remain inside the repositories called by the underlying use cases.
 *
 * Migration notes
 * - If migrating to KMP date APIs later, keep the persisted string identity:
 *   ISO yyyy-MM-dd must remain the canonical planner day key.
 *
 * Performance notes
 * - Keep this lightweight: no extra reads, no extra validation passes.
 * - The heavy work belongs inside EnsureSeriesOccurrencesWithinHorizonUseCase.
 */