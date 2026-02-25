package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.MacroTotals
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import com.example.adobongkangkong.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Observes macro totals for a single calendar day from immutable snapshot logs.
 *
 * ## Purpose
 * Provide a reactive stream of the day’s macro totals (calories / protein / carbs / fat) by aggregating
 * the nutrient snapshots stored on log entries for one ISO calendar date.
 *
 * ## Rationale (why this use case exists)
 * Day-level macro totals are a core UI primitive (Today screen, planner summaries, etc.). This logic
 * must live in the domain layer so:
 * - day membership is consistently and correctly defined,
 * - totals remain historically correct even if foods/recipes change later,
 * - call sites do not re-implement (and potentially break) aggregation rules.
 *
 * ## Behavior
 * - Reads log entries for exactly one ISO date (`yyyy-MM-dd`).
 * - Sums each entry’s stored `nutrients: NutrientMap` (snapshot-at-log-time).
 * - Emits a [MacroTotals] derived from the summed nutrient map.
 * - If there are no logs for that day, emits an “all zeros” [MacroTotals].
 *
 * ## Parameters
 * - `date`: Calendar day to summarize. This is authoritative for day selection.
 * - `zoneId`: Kept for call-site compatibility; **does not** affect which logs are included when
 *   `date` is explicitly provided. Used only by the overload that computes “today”.
 *
 * ## Return
 * A [Flow] emitting [MacroTotals] for the requested day, updating whenever the underlying day’s
 * log rows change.
 *
 * ## Edge cases
 * - No logs → returns zero totals.
 * - Logs missing a given macro key → that macro is treated as `0.0`.
 * - Negative or unexpected nutrient values (if they exist in persisted snapshots) are summed as-is.
 *
 * ## Pitfalls / gotchas
 * - **Day membership MUST be determined only by `logDateIso`** (yyyy-MM-dd). Do not use timestamp
 *   windows, start/end-of-day ranges, or device-local conversions here—those reintroduce “Day Log”
 *   bugs around DST and timezone offsets.
 * - This use case aggregates immutable snapshots; it must not join back to Foods/Recipes or recompute
 *   nutrients from mutable sources.
 *
 * ## Architectural rules
 * - Domain-layer aggregation only: depends on [LogRepository] and snapshot nutrient maps.
 * - No DB schema knowledge beyond the repository contract.
 * - No UI state, no navigation, no side effects beyond observing repository flows.
 */
class ObserveTodayMacrosUseCase @Inject constructor(
    private val logRepository: LogRepository,
) {

    /**
     * Observes macro totals for the provided calendar day.
     *
     * Day membership is defined as:
     * `log_entries.logDateIso == date.toString()` (yyyy-MM-dd)
     *
     * @param date The calendar day to summarize (authoritative for day selection).
     * @param zoneId Kept for call-site compatibility; does not affect day membership when [date] is provided.
     * @return A stream of [MacroTotals] for that day, updated whenever underlying log rows change.
     */
    @Suppress("UNUSED_PARAMETER")
    operator fun invoke(
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Flow<MacroTotals> {
        val dateIso = date.toString() // yyyy-MM-dd

        return logRepository.observeDay(dateIso)
            .map { logs ->
                if (logs.isEmpty()) return@map MacroTotals()

                val totals: NutrientMap =
                    logs.fold(NutrientMap.EMPTY) { acc, log -> acc + log.nutrients }

                MacroTotals(
                    caloriesKcal = totals[NutrientKey.CALORIES_KCAL] ?: 0.0,
                    proteinG = totals[NutrientKey.PROTEIN_G] ?: 0.0,
                    carbsG = totals[NutrientKey.CARBS_G] ?: 0.0,
                    fatG = totals[NutrientKey.FAT_G] ?: 0.0,
                )
            }
    }

    /**
     * Observes macro totals for "today" in the provided [zoneId].
     *
     * @param zoneId The timezone used to compute "today" as a [LocalDate].
     * @return A stream of [MacroTotals] for today.
     */
    operator fun invoke(
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Flow<MacroTotals> = invoke(LocalDate.now(zoneId), zoneId)
}

/**
 * FUTURE-AI / MAINTENANCE KDoc (Do not remove)
 *
 * ## Invariants (must not change)
 * - **`logDateIso` is authoritative** for day membership. This use case must continue to query by
 *   `date.toString()` (yyyy-MM-dd) and observe exactly that date’s logs.
 * - Aggregation must use `log.nutrients` (snapshot-at-log-time). Snapshot logs are immutable and
 *   must not rejoin Foods/Recipes or recompute nutrients from current entities.
 * - Empty day must produce `MacroTotals()` (all zeros), not null and not an error.
 *
 * ## Do not refactor notes
 * - Do not replace the `observeDay(dateIso)` contract with timestamp range queries.
 * - Do not “optimize” by introducing joins to food tables, nutrient definitions, or recipe graphs.
 * - Keep the overload pattern (`invoke(date, zoneId)` + `invoke(zoneId)`), even if `zoneId` is unused
 *   in the explicit-date overload; call sites may rely on the signature.
 *
 * ## Architectural boundaries
 * - Domain use case may depend on repositories and domain models only.
 * - Repository is the single source of truth for selecting day rows; do not leak SQL / Room details here.
 *
 * ## Migration notes (KMP / time APIs)
 * - If migrating off `java.time`, preserve behavior: `LocalDate -> ISO yyyy-MM-dd` string must remain
 *   identical to what the database stores in `logDateIso`.
 * - Do not introduce locale-dependent formatting; ISO date string must remain stable.
 *
 * ## Performance considerations
 * - This is O(N) per emission over the day’s logs. That is acceptable because day log counts are
 *   typically small; do not introduce caching that risks stale totals or breaks snapshot correctness.
 * - If logs become large, optimize inside the repository (e.g., DB-side pre-aggregation) while
 *   keeping `logDateIso`-based membership and snapshot immutability intact.
 */