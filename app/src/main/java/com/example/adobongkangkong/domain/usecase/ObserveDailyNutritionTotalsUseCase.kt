package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.DailyNutritionTotals
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import com.example.adobongkangkong.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * ObserveDailyNutritionTotalsUseCase
 *
 * ## Purpose
 * Produces a reactive aggregate of all nutrients consumed for a single calendar day.
 *
 * ## Rationale
 * Daily totals power:
 * - Dashboard summaries
 * - Daily macro views
 * - Trend comparisons
 * - Target compliance calculations
 *
 * Instead of recalculating totals in UI or querying raw rows repeatedly, this use case:
 * - Observes log entries for a given ISO day,
 * - Sums immutable nutrient snapshots,
 * - Emits a normalized [DailyNutritionTotals] object.
 *
 * ## Important architectural rule
 * Day membership is determined strictly by `logDateIso`.
 *
 * - This use case calls `logRepository.observeDay(dateIso)`.
 * - It does NOT use timestamp range filtering.
 * - `logDateIso` is authoritative for which entries belong to a day.
 *
 * Timestamp is preserved only for ordering/posterity.
 *
 * ## Behavior
 * - Converts `date` → ISO string (yyyy-MM-dd).
 * - Observes all log entries for that ISO day.
 * - Iterates over each entry’s immutable `NutrientMap`.
 * - Sums nutrient amounts by nutrientCode.
 * - Emits a new [DailyNutritionTotals] whenever logs change.
 *
 * ## Parameters
 * @param date The local calendar day to aggregate.
 * @param zoneId Retained for API stability; currently unused because day membership is ISO-based.
 *
 * ## Return
 * @return Flow<DailyNutritionTotals>
 * Emits whenever:
 * - A log entry is added,
 * - A log entry is deleted,
 * - A log entry is modified (if supported).
 *
 * ## Edge cases
 * - If no logs exist → totalsByCode is empty.
 * - Missing nutrient codes simply do not appear in the map.
 * - Null values should never appear here; log snapshots are expected to contain resolved totals.
 *
 * ## Historical correctness
 * Totals are derived from immutable snapshot nutrients stored at log time.
 * Editing a Food later does NOT change historical totals.
 */
class ObserveDailyNutritionTotalsUseCase @Inject constructor(
    private val logRepository: LogRepository
) {

    operator fun invoke(
        date: LocalDate,
        zoneId: ZoneId // kept for API stability; not used for ISO-based grouping
    ): Flow<DailyNutritionTotals> {
        val dateIso = date.toString() // yyyy-MM-dd

        return logRepository.observeDay(dateIso)
            .map { entries ->
                val totals = mutableMapOf<NutrientKey, Double>()

                for (entry in entries) {
                    entry.nutrients.asMap().forEach { (code, amount) ->
                        val key = NutrientKey(code)
                        totals[key] = (totals[key] ?: 0.0) + amount
                    }
                }

                DailyNutritionTotals(
                    date = date,
                    totalsByCode = NutrientMap(totals.toMap())
                )
            }
    }
}

/**
 * FUTURE AI ASSISTANT NOTES
 *
 * - Two-KDoc standard:
 *   - Top: dev-facing explanation of ISO-day grouping, immutability, aggregation rules.
 *   - Bottom: invariants and constraints for automated edits.
 *
 * - DO NOT switch this back to timestamp-range filtering.
 *   ISO-based grouping is intentional and fixes previous cross-day display bugs.
 *
 * - `zoneId` parameter exists for API compatibility.
 *   Do not remove unless all callers are updated.
 *
 * - Keep this use case PURE aggregation:
 *   - No filtering logic
 *   - No target evaluation
 *   - No UI concerns
 *
 * - If performance optimizations are introduced later:
 *   - Consider DB-side aggregation,
 *   - But preserve snapshot-based correctness.
 *
 * - If migrating to KMP:
 *   - Replace java.time types with kotlinx.datetime equivalents.
 */
private fun dayBounds(
    date: LocalDate,
    zoneId: ZoneId
): Pair<Instant, Instant> {
    val start = date.atStartOfDay(zoneId).toInstant()
    val end = date.plusDays(1).atStartOfDay(zoneId).toInstant()
    return start to end
}