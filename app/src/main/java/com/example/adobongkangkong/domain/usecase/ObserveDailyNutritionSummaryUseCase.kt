package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.DailyNutritionSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class ObserveDailyNutritionSummaryUseCase @Inject constructor(
    private val totals: ObserveDailyNutritionTotalsUseCase,
    private val statuses: ObserveDailyNutrientStatusesUseCase
) {
    operator fun invoke(
        date: LocalDate,
        zoneId: ZoneId
    ): Flow<DailyNutritionSummary> =
        combine(
            totals(date, zoneId),
            statuses(date, zoneId)
        ) { t, s ->
            DailyNutritionSummary(
                totals = t,
                statuses = s
            )
        }
}
