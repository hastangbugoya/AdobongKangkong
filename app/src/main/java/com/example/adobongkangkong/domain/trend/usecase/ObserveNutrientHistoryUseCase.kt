package com.example.adobongkangkong.domain.trend.usecase

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.usecase.ObserveDailyNutritionTotalsUseCase
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

data class NutrientHistoryEntry(
    val date: LocalDate,
    val amount: Double?,
    val unit: String?
)

class ObserveNutrientHistoryUseCase @Inject constructor(
    private val observeDailyNutritionTotals: ObserveDailyNutritionTotalsUseCase,
    private val observeDashboardNutrients: ObserveDashboardNutrientsUseCase
) {
    operator fun invoke(
        nutrientCode: String,
        endDate: LocalDate,
        days: Int,
        zoneId: ZoneId
    ): Flow<List<NutrientHistoryEntry>> {
        val safeDays = days.coerceAtLeast(0)
        if (safeDays == 0) return flowOf(emptyList())

        val canonicalCode = nutrientCode.trim().uppercase()
        val key = NutrientKey(canonicalCode)
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
            val unit = specs
                .firstOrNull { it.code.equals(canonicalCode, ignoreCase = true) }
                ?.unit

            dates.zip(totalsList).map { (date, totals) ->
                NutrientHistoryEntry(
                    date = date,
                    amount = totals.totalsByCode[key],
                    unit = unit
                )
            }
        }
    }
}