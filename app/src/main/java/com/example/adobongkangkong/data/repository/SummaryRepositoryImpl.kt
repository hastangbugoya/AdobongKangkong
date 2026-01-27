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
 */
class SummaryRepositoryImpl @Inject constructor(
    private val logRepository: LogRepository
) : SummaryRepository {

    /**
     * Observes macro totals within the given time range [startInclusive, endExclusive).
     */
    override fun observeNutrientTotals(
        startInclusive: Instant,
        endExclusive: Instant
    ): Flow<NutrientMap> {
        return logRepository.observeRange(startInclusive, endExclusive)
            .map { logs ->
                logs.fold(NutrientMap.EMPTY) { acc, log -> acc + log.nutrients }
            }
    }

    override fun observeMacroTotals(
        startInclusive: Instant,
        endExclusive: Instant
    ): Flow<MacroTotals> {
        return observeNutrientTotals(startInclusive, endExclusive)
            .map { totals ->
                MacroTotals(
                    caloriesKcal = totals[MacroKeys.CALORIES],
                    proteinG     = totals[MacroKeys.PROTEIN],
                    carbsG       = totals[MacroKeys.CARBS],
                    fatG         = totals[MacroKeys.FAT]
                )
            }
    }
}

