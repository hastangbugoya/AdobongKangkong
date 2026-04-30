package com.example.adobongkangkong.domain.trend.usecase

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.trend.model.TargetStatus
import com.example.adobongkangkong.domain.usecase.ObserveDailyNutritionTotalsUseCase
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

data class DashboardNutrientHistory(
    val columns: List<DashboardNutrientHistoryColumn>,
    val rows: List<DashboardNutrientHistoryRow>
)

data class DashboardNutrientHistoryColumn(
    val code: String,
    val displayName: String,
    val unit: String?
)

data class DashboardNutrientHistoryRow(
    val date: LocalDate,
    val valuesByCode: Map<String, Double?>,
    val status: TargetStatus
)

class ObserveDashboardNutrientHistoryUseCase @Inject constructor(
    private val observeDailyNutritionTotals: ObserveDailyNutritionTotalsUseCase,
    private val observeDashboardNutrients: ObserveDashboardNutrientsUseCase
) {
    operator fun invoke(
        endDate: LocalDate,
        days: Int,
        zoneId: ZoneId
    ): Flow<DashboardNutrientHistory> {
        val safeDays = days.coerceAtLeast(0)
        if (safeDays == 0) {
            return flowOf(
                DashboardNutrientHistory(
                    columns = emptyList(),
                    rows = emptyList()
                )
            )
        }

        val dates = (0 until safeDays).map { offset ->
            endDate.minusDays(offset.toLong())
        }

        val totalsFlows = dates.map { date ->
            observeDailyNutritionTotals(
                date = date,
                zoneId = zoneId
            )
        }

        return combine(
            observeDashboardNutrients(),
            combine(totalsFlows) { totalsArray -> totalsArray.toList() }
        ) { specs, totalsList ->
            val columns = specs.map { spec ->
                DashboardNutrientHistoryColumn(
                    code = spec.code,
                    displayName = spec.displayName,
                    unit = spec.unit
                )
            }

            val rows = dates.zip(totalsList).map { (date, totals) ->
                val valuesByCode = specs.associate { spec ->
                    val key = NutrientKey(spec.code.trim().uppercase())
                    spec.code to totals.totalsByCode[key]
                }

                DashboardNutrientHistoryRow(
                    date = date,
                    valuesByCode = valuesByCode,
                    status = evaluateRowStatus(
                        specs = specs,
                        valuesByCode = valuesByCode
                    )
                )
            }

            DashboardNutrientHistory(
                columns = columns,
                rows = rows
            )
        }
    }

    private fun evaluateRowStatus(
        specs: List<com.example.adobongkangkong.domain.trend.model.DashboardNutrientSpec>,
        valuesByCode: Map<String, Double?>
    ): TargetStatus {
        var hasAnyTarget = false

        for (spec in specs) {
            val value = valuesByCode[spec.code] ?: continue

            val status = evaluateStatus(
                consumed = value,
                min = spec.minPerDay,
                target = spec.targetPerDay,
                max = spec.maxPerDay
            )

            when (status) {
                TargetStatus.LOW,
                TargetStatus.HIGH -> return status

                TargetStatus.OK -> hasAnyTarget = true
                TargetStatus.NO_TARGET -> Unit
            }
        }

        return if (hasAnyTarget) TargetStatus.OK else TargetStatus.NO_TARGET
    }

    private fun evaluateStatus(
        consumed: Double,
        min: Double?,
        target: Double?,
        max: Double?
    ): TargetStatus {
        if (min == null && target == null && max == null) {
            return TargetStatus.NO_TARGET
        }

        if (min != null && consumed < min) return TargetStatus.LOW
        if (max != null && consumed > max) return TargetStatus.HIGH

        if (min != null || max != null) return TargetStatus.OK

        if (target != null) {
            return if (consumed < target) TargetStatus.LOW else TargetStatus.OK
        }

        return TargetStatus.NO_TARGET
    }
}