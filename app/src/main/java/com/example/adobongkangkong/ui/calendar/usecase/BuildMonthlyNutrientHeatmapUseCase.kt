package com.example.adobongkangkong.ui.calendar.usecase

import android.util.Log
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.trend.model.TargetStatus
import com.example.adobongkangkong.domain.usecase.ObserveDailyNutritionTotalsUseCase
import com.example.adobongkangkong.ui.calendar.model.CalendarDay
import com.example.adobongkangkong.ui.calendar.model.TargetRange
import com.example.adobongkangkong.ui.dashboard.pinned.usecase.ObservePinnedNutrientsUseCase
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * Builds a month-long “nutrient heatmap” model by producing one [CalendarDay] per day in the month.
 *
 * ## Purpose
 * Convert per-day nutrition totals into a calendar-friendly data structure that supports:
 * - heatmap rendering (color/status per day),
 * - tooltips/details (value + target bounds),
 * - consistent nutrient selection (explicit nutrient key or pinned fallback).
 *
 * ## Rationale (why this use case exists)
 * The heatmap view needs a precomputed list of day models for a month. While daily totals are available
 * as reactive flows, the calendar UI typically renders month pages as a snapshot list (one item per day).
 *
 * Centralizing this in a use case:
 * - Keeps month iteration and “which nutrient are we showing?” rules consistent,
 * - Prevents ViewModels/UI from re-implementing per-day totals fetching and status classification,
 * - Keeps “pinned nutrient fallback” behavior in one place.
 *
 * Note: [TargetRange] is provided by the caller to avoid duplicating the dashboard’s target-observing
 * pipeline here; this use case is a builder/assembler, not a target-source-of-truth.
 *
 * ## Behavior
 * - Resolves the nutrient key to use:
 *   - If `nutrientKey` is provided → use it.
 *   - Else → use pinned nutrient slot0 (first pinned) if present.
 *   - If no pinned nutrient exists → returns an empty list.
 * - For each day in the [month]:
 *   - Fetches that day’s totals by collecting the first emission from [ObserveDailyNutritionTotalsUseCase].
 *   - Extracts the nutrient value for the resolved key.
 *   - Computes [TargetStatus] using the provided `min/max` bounds.
 *   - Creates a [CalendarDay] containing date, nutrient key, value, bounds, and status.
 *
 * ## Parameters
 * - `month`: Month to build (year + month). Determines the number of days to generate.
 * - `zoneId`: Timezone used by downstream “daily totals” computation and for identifying “today” for debug logs.
 * - `targetRange`: The min/target/max bounds used for the produced day models.
 * - `nutrientKey`: Optional explicit nutrient to display. If null, falls back to pinned slot0.
 *
 * ## Return
 * A list of [CalendarDay] of length `month.lengthOfMonth()`, or an empty list if no nutrient key can
 * be resolved (no explicit key and no pinned nutrients).
 *
 * ## Edge cases
 * - If the totals for a day do not include the resolved key → `value` is null and status may become NO_TARGET.
 * - If `targetRange` has null bounds → status logic degrades gracefully (only checks provided bounds).
 * - If there are no pinned nutrients and `nutrientKey` is null → returns empty list.
 *
 * ## Pitfalls / gotchas
 * - This function calls `.first()` for each day, which makes it a per-day fetch pattern.
 *   This is fine for small months but can be slow if daily totals observation is heavy.
 * - Status computation currently treats `value == null` as [TargetStatus.NO_TARGET]. This name is slightly
 *   overloaded (it also covers “no value recorded for that nutrient”). Do not change without auditing UI usage.
 * - This use case lives under `ui.*` because it assembles UI models ([CalendarDay], [TargetRange]) rather than
 *   returning domain primitives.
 *
 * ## Architectural rules
 * - Read-only builder: no DB writes, no mutations, no navigation.
 * - Uses domain read use cases as inputs and produces UI models as outputs.
 * - Must keep day membership semantics delegated to [ObserveDailyNutritionTotalsUseCase] (which should be ISO-date based).
 */
class BuildMonthlyNutrientHeatmapUseCase @Inject constructor(
    private val observeDailyTotals: ObserveDailyNutritionTotalsUseCase,
    private val observePinnedNutrients: ObservePinnedNutrientsUseCase
) {
    /**
     * If [nutrientKey] is null, the use case will pick slot0 pinned nutrient if present.
     * If no pinned nutrients exist, it will return an empty list (caller should decide fallback).
     *
     * [targetRange] is supplied by the caller (VM), since target observing is already part of your dashboard pipeline
     * and we’re not inventing a non-existent ObserveUserTargetsUseCase.
     */
    suspend operator fun invoke(
        month: YearMonth,
        zoneId: ZoneId,
        targetRange: TargetRange,
        nutrientKey: NutrientKey? = null
    ): List<CalendarDay> {

        val resolvedKey = nutrientKey ?: observePinnedNutrients().first().firstOrNull()
        ?: return emptyList()

        val min = targetRange.min
        val target = targetRange.target
        val max = targetRange.max
        Log.d("Meow", "Heatmap building month=$month resolvedKey=$resolvedKey")

        val days = month.lengthOfMonth()
        return (1..days).map { day ->
            val date = month.atDay(day)

            val totals = observeDailyTotals(date, zoneId).first()
            val value = totals.totalsByCode[resolvedKey]

            if (day <= 3) {
                Log.d(
                    "Meow",
                    "$day date=$date value=${totals.totalsByCode[resolvedKey]} keys=${totals.totalsByCode.keys()}"
                )
            }

            if (date == LocalDate.now(zoneId)) {
                Log.d(
                    "Meow",
                    "Available keys = ${totals.totalsByCode.keys()} Resolved key = $resolvedKey Value = ${totals.totalsByCode[resolvedKey]}"
                )
                println("Heatmap debug:")
                println("Available keys = ${totals.totalsByCode.keys()}")
                println("Resolved key   = $resolvedKey")
                println("Value          = ${totals.totalsByCode[resolvedKey]}")
            }
            CalendarDay(
                date = date,
                nutrientKey = resolvedKey,
                value = value,
                min = min,
                target = target,
                max = max,
                status = computeStatus(value, min, max)
            )
        }
    }

    private fun computeStatus(value: Double?, min: Double?, max: Double?): TargetStatus {
        if (value == null) return TargetStatus.NO_TARGET
        if (min != null && value < min) return TargetStatus.LOW
        if (max != null && value > max) return TargetStatus.HIGH
        return TargetStatus.OK
    }
}

/**
 * FUTURE-AI / MAINTENANCE KDoc (Do not remove)
 *
 * ## Invariants (must not change)
 * - Nutrient resolution priority must remain:
 *   1) explicit `nutrientKey` parameter
 *   2) pinned slot0 (first pinned)
 *   3) empty list if no key resolved
 * - Month iteration must produce exactly one [CalendarDay] per calendar day in [month].
 * - Day membership and totals correctness must remain delegated to [ObserveDailyNutritionTotalsUseCase]
 *   (assumed ISO-date authoritative).
 * - This builder must remain read-only: no writes, no edits, no side effects beyond logging.
 *
 * ## Do not refactor notes
 * - Do not move target observation into this use case; caller supplies [TargetRange] intentionally
 *   to keep dashboard pipelines centralized.
 * - Do not “optimize” by changing semantics of `.first()` without validating downstream flow behavior.
 * - Do not change status semantics (`value == null -> NO_TARGET`) without auditing UI expectations.
 * - Avoid expanding debug logging; if removing logs, confirm you aren’t losing needed diagnosis signals.
 *
 * ## Architectural boundaries
 * - This is UI-layer assembly (ui.*): returns UI models ([CalendarDay]) and consumes UI target models ([TargetRange]).
 * - It may depend on domain read use cases but should not reach directly into repositories.
 *
 * ## Migration notes (KMP / time APIs)
 * - Uses `java.time.YearMonth/LocalDate/ZoneId`. If migrating to KMP:
 *   - Preserve the concept of calendar-month iteration and zone-based “today”.
 *   - Ensure ISO date boundaries used by daily totals remain stable.
 *
 * ## Performance considerations
 * - Current implementation performs one `.first()` collection per day.
 *   If monthly build becomes slow, consider replacing with a batch totals use case (e.g., observe N days)
 *   or repository-side aggregation, but preserve per-day correctness and ISO date membership.
 * - This use case is typically run on a background dispatcher by the caller; do not introduce
 *   main-thread-only dependencies here.
 */