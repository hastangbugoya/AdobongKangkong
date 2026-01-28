package com.example.adobongkangkong.domain.trend.usecase

import com.example.adobongkangkong.domain.trend.model.RollingNutritionStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Composes rolling trend analytics into one stream:
 * - Rolling averages over [days] ending at [endDate]
 * - OK streaks (consecutive OK days ending at [endDate]) with lookback [streakLookbackDays]
 *
 * "Rolling days knob" = [days]. Change it and this stream recomputes automatically.
 */
class ObserveRollingNutritionStatsUseCase @Inject constructor(
    private val observeAverages: ObserveRollingNutritionAveragesUseCase,
    private val observeOkStreaks: ObserveOkStreaksUseCase
) {
    operator fun invoke(
        endDate: LocalDate,
        days: Int,
        zoneId: ZoneId,
        streakLookbackDays: Int = days
    ): Flow<RollingNutritionStats> {
        require(days >= 1)
        require(streakLookbackDays >= 1)

        return combine(
            observeAverages(
                endDate = endDate,
                days = days,
                zoneId = zoneId
            ),
            observeOkStreaks(
                endDate = endDate,
                maxLookbackDays = streakLookbackDays,
                zoneId = zoneId
            )
        ) { averages, okStreaks ->
            RollingNutritionStats(
                averages = averages,
                okStreaks = okStreaks
            )
        }
    }
}
