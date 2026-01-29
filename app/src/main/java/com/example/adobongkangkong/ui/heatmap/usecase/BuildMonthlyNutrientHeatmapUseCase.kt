package com.example.adobongkangkong.ui.heatmap.usecase

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.trend.model.TargetStatus
import com.example.adobongkangkong.domain.usecase.ObserveDailyNutritionTotalsUseCase
import com.example.adobongkangkong.ui.dashboard.pinned.usecase.ObservePinnedNutrientsUseCase
import com.example.adobongkangkong.ui.heatmap.model.HeatmapDay
import com.example.adobongkangkong.ui.heatmap.model.TargetRange
import kotlinx.coroutines.flow.first
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

class BuildMonthlyNutrientHeatmapUseCase @Inject constructor(
    private val observeDailyTotals: ObserveDailyNutritionTotalsUseCase,
    private val observePinnedNutrients: ObservePinnedNutrientsUseCase
) {
    /**
     * If [nutrientKey] is null, the use case will pick slot0 pinned nutrient if present.
     * If no pinned nutrients exist, it will return an empty list (caller should decide fallback).
     *
     * [targetRange] is supplied by the caller (VM), since target observing is already part of your dashboard pipeline
     * and we’re not inventing a non-existent ObserveUserTargetsUseCase.
     */
    suspend operator fun invoke(
        month: YearMonth,
        zoneId: ZoneId,
        targetRange: TargetRange,
        nutrientKey: NutrientKey? = null
    ): List<HeatmapDay> {

        val resolvedKey = nutrientKey ?: observePinnedNutrients().first().firstOrNull()
        ?: return emptyList()

        val min = targetRange.min
        val target = targetRange.target
        val max = targetRange.max

        val days = month.lengthOfMonth()
        return (1..days).map { day ->
            val date = month.atDay(day)

            val totals = observeDailyTotals(date, zoneId).first()
            val value = totals.totalsByCode[resolvedKey.value]

            HeatmapDay(
                date = date,
                nutrientKey = resolvedKey,
                value = value,
                min = min,
                target = target,
                max = max,
                status = computeStatus(value, min, max)
            )
        }
    }

    private fun computeStatus(value: Double?, min: Double?, max: Double?): TargetStatus {
        if (value == null) return TargetStatus.NO_TARGET
        if (min != null && value < min) return TargetStatus.LOW
        if (max != null && value > max) return TargetStatus.HIGH
        return TargetStatus.OK
    }
}
