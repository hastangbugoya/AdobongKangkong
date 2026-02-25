package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.domain.model.MacroTotals
import com.example.adobongkangkong.domain.nutrition.MacroKeys
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import com.example.adobongkangkong.domain.repository.LogRepository
import com.example.adobongkangkong.domain.repository.SummaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

/**
 * Computes summary totals from immutable snapshot logs.
 *
 * Logs store nutrition totals at log-time, so summaries are derived by summing the
 * logged [com.example.adobongkangkong.domain.nutrition.NutrientMap] values.
 *
 * Benefits:
 * - Deleting/editing foods does not affect historical logs or summaries.
 * - No joins, no recomputation from food tables.
 *
 * Implementation of [SummaryRepository] backed by [LogRepository].
 *
 * ## Behavior
 * - Reads log entries via date-based range queries.
 * - Sums immutable nutrient snapshots stored on each log entry.
 * - Avoids joins so historical totals remain stable if foods/recipes change later.
 */
class SummaryRepositoryImpl @Inject constructor(
    private val logRepository: LogRepository
) : SummaryRepository {

    /**
     * Observes total nutrients across the inclusive ISO date range.
     *
     * Delegates to [LogRepository.observeRangeByDateIso] and folds each entry's snapshot nutrients
     * into a single [NutrientMap].
     */
    override fun observeNutrientTotalsByDateIso(
        startDateIsoInclusive: String,
        endDateIsoInclusive: String
    ): Flow<NutrientMap> {
        return logRepository.observeRangeByDateIso(startDateIsoInclusive, endDateIsoInclusive)
            .map { logs ->
                logs.fold(NutrientMap.EMPTY) { acc, log -> acc + log.nutrients }
            }
    }

    /**
     * Observes macro totals across the inclusive ISO date range.
     *
     * This maps the full [NutrientMap] totals into [MacroTotals] using [MacroKeys].
     */
    override fun observeMacroTotalsByDateIso(
        startDateIsoInclusive: String,
        endDateIsoInclusive: String
    ): Flow<MacroTotals> {
        return observeNutrientTotalsByDateIso(startDateIsoInclusive, endDateIsoInclusive)
            .map { totals ->
                MacroTotals(
                    caloriesKcal = totals[MacroKeys.CALORIES],
                    proteinG = totals[MacroKeys.PROTEIN],
                    carbsG = totals[MacroKeys.CARBS],
                    fatG = totals[MacroKeys.FAT]
                )
            }
    }
}

