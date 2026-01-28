package com.example.adobongkangkong.domain.trend.usecase

import com.example.adobongkangkong.domain.trend.model.RollingNutritionAverages
import com.example.adobongkangkong.domain.usecase.ObserveDailyNutritionTotalsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.collections.iterator

class ObserveRollingNutritionAveragesUseCase @Inject constructor(
    private val observeDailyTotals: ObserveDailyNutritionTotalsUseCase
) {
    operator fun invoke(
        endDate: LocalDate,
        days: Int,
        zoneId: ZoneId
    ): Flow<RollingNutritionAverages> {
        require(days >= 1)

        val dateList = (0 until days).map { i -> endDate.minusDays((days - 1 - i).toLong()) }

        val flows = dateList.map { d -> observeDailyTotals(d, zoneId) }

        return combine(flows) { totalsArray ->
            val sum = mutableMapOf<String, Double>()

            for (daily in totalsArray) {
                for ((code, value) in daily.totalsByCode) {
                    sum[code] = (sum[code] ?: 0.0) + value
                }
            }

            val avg = sum.mapValues { (_, v) -> v / days.toDouble() }

            RollingNutritionAverages(
                endDate = endDate,
                days = days,
                averageByCode = avg
            )
        }
    }
}