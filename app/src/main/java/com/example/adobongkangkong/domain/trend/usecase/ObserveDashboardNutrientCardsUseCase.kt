package com.example.adobongkangkong.domain.trend.usecase

import com.example.adobongkangkong.domain.model.UserNutrientTarget
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.trend.model.DashboardNutrientCard
import com.example.adobongkangkong.domain.trend.model.DashboardNutrientSpec
import com.example.adobongkangkong.domain.trend.model.TargetStatus
import com.example.adobongkangkong.domain.usecase.ObserveDailyNutritionSummaryUseCase
import com.example.adobongkangkong.domain.repository.IouRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Produces ordered dashboard nutrient cards for a given day + rolling window.
 *
 * Composition:
 * 1) ObserveDashboardNutrientsUseCase -> ordered nutrient specs (fixed macros + pinned)
 * 2) ObserveDailyNutritionSummaryUseCase(date) -> today's totalsByCode
 * 3) ObserveRollingNutritionStatsUseCase(endDate=date, days) -> rolling average + ok streaks
 * 4) ObserveDashboardTargetsUseCase -> user targets keyed by nutrientCode
 *
 * Output order is stable and comes from ObserveDashboardNutrientsUseCase.
 */
class ObserveDashboardNutrientCardsUseCase @Inject constructor(
    private val observeDashboardNutrients: ObserveDashboardNutrientsUseCase,
    private val observeDailyNutritionSummary: ObserveDailyNutritionSummaryUseCase,
    private val observeRollingNutritionStats: ObserveRollingNutritionStatsUseCase,
    private val observeDashboardTargets: ObserveDashboardTargetsUseCase,
    private val iouRepository: IouRepository
) {
    operator fun invoke(
        date: LocalDate,
        rollingDays: Int,
        zoneId: ZoneId
    ): Flow<List<DashboardNutrientCard>> =
        combine(
            observeDashboardNutrients(), // Flow<List<DashboardNutrientSpec>>
            observeDailyNutritionSummary(date, zoneId),
            observeRollingNutritionStats(endDate = date, days = rollingDays, zoneId = zoneId),
            observeDashboardTargets(), // Flow<Map<String, UserNutrientTarget>>
            iouRepository.observeForDate(date.toString())
        ) { specs, dailySummary, rollingStats, targetsByCode, ious ->

            val totalsByCode = dailySummary.totals.totalsByCode
            val avgByCode = rollingStats.averages.averageByCode
            val okStreakByCode = rollingStats.okStreaks.associate { it.nutrientCode to it.days }
            val iouByCode = mapOf(
                NutrientKey.CALORIES_KCAL.value to ious.sumOf { it.estimatedCaloriesKcal ?: 0.0 },
                NutrientKey.PROTEIN_G.value to ious.sumOf { it.estimatedProteinG ?: 0.0 },
                NutrientKey.CARBS_G.value to ious.sumOf { it.estimatedCarbsG ?: 0.0 },
                NutrientKey.FAT_G.value to ious.sumOf { it.estimatedFatG ?: 0.0 }
            )
            android.util.Log.d("Meow", "targets keys=${targetsByCode.keys}")
            android.util.Log.d("Meow", "spec codes=${specs.map { it.code }}")
            specs.map { spec: DashboardNutrientSpec ->
                val code = spec.code.trim().uppercase()
                val key1 = NutrientKey(code)
                val key2 = NutrientKey(spec.code)
                val consumed = totalsByCode[key1] ?: totalsByCode[key2] ?: 0.0

                val target = targetsByCode[code] // now canonical because repo normalizes

                DashboardNutrientCard(
                    code = code,
                    displayName = spec.displayName,
                    unit = spec.unit,
                    consumedToday = consumed,
                    minPerDay = target?.minPerDay,
                    targetPerDay = target?.targetPerDay,
                    maxPerDay = target?.maxPerDay,
                    status = evaluateStatus(consumed, target),
                    rollingAverage = avgByCode[code],
                    okStreakDays = okStreakByCode[code] ?: 0,
                    iouEstimate = iouByCode[code]?.takeIf { it > 0.0 }
                )
            }
        }

    private fun evaluateStatus(consumed: Double, t: UserNutrientTarget?): TargetStatus {
        if (t == null) return TargetStatus.NO_TARGET

        val min = t.minPerDay
        val max = t.maxPerDay
        val goal = t.targetPerDay

        if (min != null && consumed < min) return TargetStatus.LOW
        if (max != null && consumed > max) return TargetStatus.HIGH

        // If min/max exist and we’re within bounds, that’s OK.
        if (min != null || max != null) return TargetStatus.OK

        if (goal != null) return if (consumed < goal) TargetStatus.LOW else TargetStatus.OK

        return TargetStatus.NO_TARGET
    }
}