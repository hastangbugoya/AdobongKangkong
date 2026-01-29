package com.example.adobongkangkong.domain.trend.usecase

import com.example.adobongkangkong.domain.model.TargetStatus
import com.example.adobongkangkong.domain.trend.model.ComplianceScore
import com.example.adobongkangkong.domain.usecase.ObserveDailyNutrientStatusesUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Computes rolling compliance scores over a sliding N-day window ending at [endDate].
 *
 * Compliance definition:
 * - For each nutrientCode with a target:
 *   okDays    = number of days where status == OK
 *   days      = total observed days (usually == N)
 *   percentOk = okDays / days
 *
 * Fully N-day flexible via [days].
 */
class ObserveRollingComplianceScoresUseCase @Inject constructor(
    private val observeDailyStatuses: ObserveDailyNutrientStatusesUseCase
) {
    operator fun invoke(
        endDate: LocalDate,
        days: Int,
        zoneId: ZoneId
    ): Flow<List<ComplianceScore>> {
        require(days >= 1) { "days must be >= 1" }

        // Oldest -> newest window ending at endDate
        val dateList = (0 until days).map { i ->
            endDate.minusDays((days - 1 - i).toLong())
        }

        val flows = dateList.map { d ->
            observeDailyStatuses(d, zoneId)
        }

        return combine(flows) { perDayStatusesArray ->
            val okCounts = mutableMapOf<String, Int>()
            val totalCounts = mutableMapOf<String, Int>()

            for (dayStatuses in perDayStatusesArray) {
                for (status in dayStatuses) {
                    val code = status.nutrientCode
                    totalCounts[code] = (totalCounts[code] ?: 0) + 1
                    if (status.status == TargetStatus.OK) {
                        okCounts[code] = (okCounts[code] ?: 0) + 1
                    }
                }
            }

            totalCounts.keys.sorted().map { code ->
                val total = totalCounts[code] ?: 0
                val ok = okCounts[code] ?: 0

                ComplianceScore(
                    nutrientCode = code,
                    days = total,
                    okDays = ok,
                    percentOk = if (total > 0) ok.toDouble() / total.toDouble() else 0.0
                )
            }
        }
    }
}
