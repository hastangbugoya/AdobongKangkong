package com.example.adobongkangkong.domain.compliance.usecase

import com.example.adobongkangkong.domain.compliance.evaluateMarker
import com.example.adobongkangkong.domain.compliance.model.WeeklyCompliance
import com.example.adobongkangkong.domain.compliance.model.WeeklyNutrientRow
import com.example.adobongkangkong.domain.model.DailyNutritionTotals
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.trend.model.TargetStatus
import com.example.adobongkangkong.domain.usecase.ObserveDailyNutritionTotalsUseCase
import com.example.adobongkangkong.ui.calendar.model.TargetRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class BuildWeeklyComplianceUseCase @Inject constructor(
    private val observeDailyTotals: ObserveDailyNutritionTotalsUseCase
) {

    operator fun invoke(
        weekStart: LocalDate,
        keys: List<NutrientKey>,
        targets: Map<NutrientKey, TargetRange>,
        zoneId: ZoneId
    ): Flow<WeeklyCompliance> {

        val weekDates = (0..6).map { weekStart.plusDays(it.toLong()) }

        val perDayFlows: List<Flow<DailyNutritionTotals>> =
            weekDates.map { date -> observeDailyTotals(date, zoneId) }

        return combine(perDayFlows) { totalsArray ->
            // totalsArray lines up with weekDates by index
            val totalsByDate: Map<LocalDate, DailyNutritionTotals> =
                weekDates.zip(totalsArray.toList()).toMap()

            val rows: List<WeeklyNutrientRow> =
                keys.map { key ->
                    val statusesByDate: Map<LocalDate, TargetStatus> =
                        weekDates.associateWith { date ->
                            val totalsForDay = totalsByDate[date]
                            val consumed = totalsForDay?.totalsByCode[key] ?: 0.0
                            val range = targets[key]
                            if (range == null) TargetStatus.NO_TARGET
                            else evaluateMarker(consumed, range)
                        }

                    WeeklyNutrientRow(
                        key = key,
                        statuses = statusesByDate
                    )
                }

            WeeklyCompliance(
                weekStart = weekStart,
                weekDates,
                rows = rows
            )
        }

    }
}