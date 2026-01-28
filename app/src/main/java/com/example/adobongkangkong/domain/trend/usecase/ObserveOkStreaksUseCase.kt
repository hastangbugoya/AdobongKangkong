package com.example.adobongkangkong.domain.trend.usecase

import com.example.adobongkangkong.domain.model.TargetStatus
import com.example.adobongkangkong.domain.trend.model.NutrientStreak
import com.example.adobongkangkong.domain.usecase.ObserveDailyNutrientStatusesUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class ObserveOkStreaksUseCase @Inject constructor(
    private val observeDailyStatuses: ObserveDailyNutrientStatusesUseCase
) {
    operator fun invoke(
        endDate: LocalDate,
        maxLookbackDays: Int,
        zoneId: ZoneId
    ): Flow<List<NutrientStreak>> {
        require(maxLookbackDays >= 1)

        val dateList = (0 until maxLookbackDays).map { i -> endDate.minusDays(i.toLong()) }
        val flows = dateList.map { d -> observeDailyStatuses(d, zoneId) }

        // combine returns statuses for each day; index 0 = endDate, 1 = endDate-1, ...
        return combine(flows) { perDayStatuses ->
            // Build map: nutrientCode -> list of TargetStatus in reverse chronological order
            val statusByCode = mutableMapOf<String, MutableList<TargetStatus>>()

            perDayStatuses.forEach { dayList ->
                dayList.forEach { s ->
                    statusByCode.getOrPut(s.nutrientCode) { mutableListOf() }.add(s.status)
                }
            }

            statusByCode.map { (code, statuses) ->
                // Count consecutive OK from day 0 backwards
                var streak = 0
                for (st in statuses) {
                    if (st == TargetStatus.OK) streak++ else break
                }
                NutrientStreak(
                    nutrientCode = code,
                    days = streak,
                    status = TargetStatus.OK
                )
            }
        }
    }
}