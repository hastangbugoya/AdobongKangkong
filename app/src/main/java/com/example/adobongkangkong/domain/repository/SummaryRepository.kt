package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.domain.model.MacroTotals
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import kotlinx.coroutines.flow.Flow

/**
 * Provides aggregated nutrition summaries over log entries.
 *
 * ## Important
 * This repository is now **date-driven** and aggregates by `logDateIso` ranges (yyyy-MM-dd),
 * delegating to [LogRepository.observeRangeByDateIso]. It no longer queries logs by timestamp
 * windows.
 *
 * ## Why
 * Log day membership is defined by `logDateIso`, not timestamps. Using date ranges prevents
 * accidental cross-day leakage and keeps summary behavior aligned with Day Log / calendar views.
 */
interface SummaryRepository {

    /**
     * Observes full nutrient totals across an inclusive ISO date range.
     *
     * Aggregation:
     * - For every log entry in the range, sum its immutable `nutrients` snapshot into a single
     *   [NutrientMap].
     *
     * @param startDateIsoInclusive ISO date (yyyy-MM-dd), inclusive start.
     * @param endDateIsoInclusive ISO date (yyyy-MM-dd), inclusive end.
     * @return A stream of summed nutrients across the date range.
     */
    fun observeNutrientTotalsByDateIso(
        startDateIsoInclusive: String,
        endDateIsoInclusive: String
    ): Flow<NutrientMap>

    /**
     * Observes macro totals (calories/protein/carbs/fat) across an inclusive ISO date range.
     *
     * This is a convenience wrapper over [observeNutrientTotalsByDateIso].
     *
     * @param startDateIsoInclusive ISO date (yyyy-MM-dd), inclusive start.
     * @param endDateIsoInclusive ISO date (yyyy-MM-dd), inclusive end.
     * @return A stream of macro totals across the date range.
     */
    fun observeMacroTotalsByDateIso(
        startDateIsoInclusive: String,
        endDateIsoInclusive: String
    ): Flow<MacroTotals>
}