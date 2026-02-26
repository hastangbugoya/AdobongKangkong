package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesSlotRuleEntity
import com.example.adobongkangkong.domain.repository.PlannedSeriesRepository
import javax.inject.Inject

/**
 * Loads a “series bundle” (series definition + slot rules) as a single domain result.
 *
 * ----------------------------------------------------------------------------
 * Purpose
 * ----------------------------------------------------------------------------
 *
 * A Planned Series is stored across multiple tables:
 *
 * - planned_series (the core definition + end condition)
 * - planned_series_slot_rules (weekday + slot schedule rules)
 *
 * Callers frequently need both at the same time to render an editor screen,
 * show series details, or perform domain decisions that require the full series shape.
 *
 * This use case provides a single, predictable entry point that returns:
 *
 * - the series definition row
 * - the associated slot rules
 *
 * ----------------------------------------------------------------------------
 * Rationale (why this use case exists)
 * ----------------------------------------------------------------------------
 *
 * Without this wrapper, each call site would:
 *
 * - read the series
 * - then read slot rules
 * - then manually bundle them
 *
 * That repetition increases the chance of:
 *
 * - inconsistent null-handling (series missing vs rules empty)
 * - call sites forgetting to load rules (partial UI states)
 * - future migration churn if the “bundle” shape expands (e.g., series items)
 *
 * Centralizing the bundle keeps the UI and other domain use cases consistent and
 * makes future schema evolution cheaper.
 *
 * ----------------------------------------------------------------------------
 * Behavior
 * ----------------------------------------------------------------------------
 *
 * - If the series row does not exist, returns null.
 * - Otherwise loads slot rules (may be empty) and returns a [Result].
 *
 * ----------------------------------------------------------------------------
 * Parameters
 * ----------------------------------------------------------------------------
 * @param seriesId The primary key of the series definition row.
 *
 * ----------------------------------------------------------------------------
 * Return
 * ----------------------------------------------------------------------------
 * @return [Result] if the series exists, otherwise null.
 *
 * ----------------------------------------------------------------------------
 * Edge cases
 * ----------------------------------------------------------------------------
 *
 * - Slot rules empty:
 *   This can happen for partially-created or legacy series. Caller decides how to handle
 *   (e.g., treat as invalid series or show empty schedule UI).
 *
 * ----------------------------------------------------------------------------
 * Pitfalls / gotchas
 * ----------------------------------------------------------------------------
 *
 * - This is a read-only bundler.
 *   It does not validate that the series is “usable” (e.g., has rules).
 *
 * ----------------------------------------------------------------------------
 * Architectural rules
 * ----------------------------------------------------------------------------
 *
 * - Domain orchestration only: combines repository reads.
 * - Must not introduce UI logic, navigation, or write side effects here.
 */
class GetPlannedSeriesBundleUseCase @Inject constructor(
    private val repo: PlannedSeriesRepository
) {
    data class Result(
        val series: PlannedSeriesEntity,
        val slotRules: List<PlannedSeriesSlotRuleEntity>
    )

    suspend fun execute(seriesId: Long): Result? {
        val series = repo.getSeriesById(seriesId) ?: return null
        val rules = repo.getSlotRulesForSeries(seriesId)
        return Result(series, rules)
    }
}

/**
 * =============================================================================
 * FOR FUTURE AI ASSISTANT — GetPlannedSeriesBundleUseCase invariants
 * =============================================================================
 *
 * Invariants (MUST NOT CHANGE)
 * - If the series does not exist, return null (do not throw).
 * - Slot rules may be empty; this use case must not “fix” or infer rules.
 * - No writes. No transactions needed. No validation side-effects.
 *
 * Do not refactor notes
 * - Do NOT inline this logic into multiple ViewModels/screens again.
 *   The whole point is to keep the “bundle” shape stable in one place.
 *
 * Architectural boundaries
 * - Read-only domain helper that composes repository reads.
 *
 * Future evolution notes
 * - If/when series items are needed alongside rules, expand Result to include:
 *   - PlannedSeriesItemEntity list
 *   and update call sites once.
 *
 * Performance considerations
 * - Currently performs 2 repository reads. If this becomes hot, consider
 *   adding a repository-level query that fetches both in one call.
 */