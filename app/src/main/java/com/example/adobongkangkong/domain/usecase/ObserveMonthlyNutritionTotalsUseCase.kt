package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.DailyNutritionTotals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * ObserveMonthlyNutritionTotalsUseCase
 *
 * ## Purpose
 * Provides a neutral month-level view of daily nutrition totals.
 *
 * This is intentionally a small addon use case:
 * - it does NOT change existing calendar behavior
 * - it does NOT know about UI models
 * - it does NOT know about shared/export JSON models
 * - it does NOT introduce new nutrition math
 *
 * ## Architecture
 * Reuses the trusted day-level totals source:
 *
 *     ObserveDailyNutritionTotalsUseCase(day, zoneId)
 *
 * and assembles one ordered list covering every day in the requested month.
 *
 * ## Design intent
 * This creates a stable month reader that other layers can map from:
 * - shared month JSON export
 * - future calendar refactor
 *
 * while leaving the current working calendar pipeline untouched for now.
 */
class ObserveMonthlyNutritionTotalsUseCase @Inject constructor(
    private val observeDailyNutritionTotalsUseCase: ObserveDailyNutritionTotalsUseCase
) {

    operator fun invoke(
        month: YearMonth,
        zoneId: ZoneId
    ): Flow<List<DailyNutritionTotals>> {
        val dailyFlows = (1..month.lengthOfMonth()).map { dayOfMonth ->
            val date = month.atDay(dayOfMonth)
            observeDailyNutritionTotalsUseCase(date, zoneId)
        }

        return combine(dailyFlows) { dailyTotalsArray ->
            dailyTotalsArray.toList().sortedBy { it.date }
        }
    }
}