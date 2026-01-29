package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import java.time.LocalDate

/**
 * Returns totals for a nutrient per day in an inclusive date range.
 * Missing days may be absent from the map (treated as NO_DATA).
 */
interface DailyNutrientTotalsRepository {
    suspend fun getDailyTotals(
        nutrientKey: NutrientKey,
        startDate: LocalDate,
        endDateInclusive: LocalDate
    ): Map<LocalDate, Double>
}
