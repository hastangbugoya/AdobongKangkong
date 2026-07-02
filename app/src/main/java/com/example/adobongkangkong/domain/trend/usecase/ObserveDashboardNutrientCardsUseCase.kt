package com.example.adobongkangkong.domain.trend.usecase

import com.example.adobongkangkong.data.local.db.dao.LaxRuleDayDao
import com.example.adobongkangkong.domain.model.UserNutrientTarget
import com.example.adobongkangkong.domain.nutrition.MacroKeys
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import com.example.adobongkangkong.domain.repository.IouRepository
import com.example.adobongkangkong.domain.settings.UserPreferencesRepository
import com.example.adobongkangkong.domain.trend.model.DashboardNutrientCard
import com.example.adobongkangkong.domain.trend.model.DashboardNutrientSpec
import com.example.adobongkangkong.domain.trend.model.TargetStatus
import com.example.adobongkangkong.domain.usecase.ObserveDailyNutritionSummaryUseCase
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Produces ordered dashboard nutrient cards for a given day + rolling window.
 *
 * Composition:
 * 1) ObserveDashboardNutrientsUseCase -> ordered nutrient specs (fixed macros + pinned)
 * 2) ObserveDailyNutritionSummaryUseCase(date) -> today's totalsByCode
 * 3) ObserveRollingNutritionStatsUseCase(endDate=date, days) -> rolling average + ok streaks
 * 4) ObserveDashboardTargetsUseCase -> user targets keyed by nutrientCode
 * 5) LaxRuleDayDao + UserPreferencesRepository -> alternate goals for marked lax rules days
 *
 * Output order is stable and comes from ObserveDashboardNutrientsUseCase.
 *
 * Lax rules day behavior:
 * - Logged nutrient totals are not changed.
 * - Normal daily targets are not changed.
 * - If the selected date is marked in lax_rule_days, supported dashboard nutrients use
 *   the alternate lax-day bounds stored in preferences.
 * - Unsupported nutrients keep their normal target bounds.
 *
 * Initial supported lax-day nutrients:
 * - Calories: alternate maximum
 * - Protein: alternate minimum
 * - Carbs: alternate maximum
 * - Fat: alternate maximum
 * - Sodium: alternate maximum
 * - Total sugar: alternate maximum
 */
class ObserveDashboardNutrientCardsUseCase @Inject constructor(
    private val observeDashboardNutrients: ObserveDashboardNutrientsUseCase,
    private val observeDailyNutritionSummary: ObserveDailyNutritionSummaryUseCase,
    private val observeRollingNutritionStats: ObserveRollingNutritionStatsUseCase,
    private val observeDashboardTargets: ObserveDashboardTargetsUseCase,
    private val iouRepository: IouRepository,
    private val laxRuleDayDao: LaxRuleDayDao,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    operator fun invoke(
        date: LocalDate,
        rollingDays: Int,
        zoneId: ZoneId
    ): Flow<List<DashboardNutrientCard>> {
        val baseInputsFlow = combine(
            observeDashboardNutrients(), // Flow<List<DashboardNutrientSpec>>
            observeDailyNutritionSummary(date, zoneId),
            observeRollingNutritionStats(endDate = date, days = rollingDays, zoneId = zoneId),
            observeDashboardTargets(), // Flow<Map<String, UserNutrientTarget>>
            iouRepository.observeForDate(date.toString())
        ) { specs, dailySummary, rollingStats, targetsByCode, ious ->
            DashboardCardInputs(
                specs = specs,
                totalsByCode = dailySummary.totals.totalsByCode,
                avgByCode = rollingStats.averages.averageByCode,
                okStreakByCode = rollingStats.okStreaks.associate { it.nutrientCode to it.days },
                targetsByCode = targetsByCode,
                iouByCode = mapOf(
                    NutrientKey.CALORIES_KCAL.value to ious.sumOf { it.estimatedCaloriesKcal ?: 0.0 },
                    NutrientKey.PROTEIN_G.value to ious.sumOf { it.estimatedProteinG ?: 0.0 },
                    NutrientKey.CARBS_G.value to ious.sumOf { it.estimatedCarbsG ?: 0.0 },
                    NutrientKey.FAT_G.value to ious.sumOf { it.estimatedFatG ?: 0.0 }
                )
            )
        }

        return combine(
            baseInputsFlow,
            laxRuleDayDao.observeForDate(date.toEpochDay()),
            observeLaxDayGoalSettings()
        ) { inputs, laxRuleDay, laxSettings ->
            val isLaxRuleDay = laxRuleDay != null
            inputs.specs.map { spec: DashboardNutrientSpec ->
                val code = spec.code.trim().uppercase()
                val key1 = NutrientKey(code)
                val key2 = NutrientKey(spec.code)
                val consumed = inputs.totalsByCode[key1] ?: inputs.totalsByCode[key2] ?: 0.0

                val normalTarget = inputs.targetsByCode[code]
                val effectiveTarget = if (isLaxRuleDay) {
                    laxSettings.effectiveTargetFor(code)
                } else {
                    null
                } ?: normalTarget.toEffectiveNutrientTarget()

                DashboardNutrientCard(
                    code = code,
                    displayName = spec.displayName,
                    unit = spec.unit,
                    consumedToday = consumed,
                    minPerDay = effectiveTarget.min,
                    targetPerDay = effectiveTarget.target,
                    maxPerDay = effectiveTarget.max,
                    status = evaluateStatus(consumed, effectiveTarget),
                    rollingAverage = inputs.avgByCode[code],
                    okStreakDays = inputs.okStreakByCode[code] ?: 0,
                    iouEstimate = inputs.iouByCode[code]?.takeIf { it > 0.0 }
                )
            }
        }
    }

    private fun observeLaxDayGoalSettings(): Flow<LaxDayGoalSettings> {
        val laxMacroSettings = combine(
            userPreferencesRepository.laxDayCaloriesLimitKcal,
            userPreferencesRepository.laxDayProteinGoalG,
            userPreferencesRepository.laxDayCarbsLimitG,
            userPreferencesRepository.laxDayFatLimitG
        ) { caloriesLimitKcal, proteinGoalG, carbsLimitG, fatLimitG ->
            LaxDayMacroSettings(
                caloriesLimitKcal = caloriesLimitKcal,
                proteinGoalG = proteinGoalG,
                carbsLimitG = carbsLimitG,
                fatLimitG = fatLimitG
            )
        }

        return combine(
            laxMacroSettings,
            userPreferencesRepository.laxDaySodiumLimitMg,
            userPreferencesRepository.laxDaySugarLimitG
        ) { macroSettings, sodiumLimitMg, sugarLimitG ->
            LaxDayGoalSettings(
                caloriesLimitKcal = macroSettings.caloriesLimitKcal,
                proteinGoalG = macroSettings.proteinGoalG,
                carbsLimitG = macroSettings.carbsLimitG,
                fatLimitG = macroSettings.fatLimitG,
                sodiumLimitMg = sodiumLimitMg,
                sugarLimitG = sugarLimitG
            )
        }
    }

    private fun evaluateStatus(consumed: Double, target: EffectiveNutrientTarget): TargetStatus {
        if (target.min != null && consumed < target.min) return TargetStatus.LOW
        if (target.max != null && consumed > target.max) return TargetStatus.HIGH

        // If min/max exist and we're within bounds, that's OK.
        if (target.min != null || target.max != null) return TargetStatus.OK

        if (target.target != null) {
            return if (consumed < target.target) TargetStatus.LOW else TargetStatus.OK
        }

        return TargetStatus.NO_TARGET
    }
}

private data class DashboardCardInputs(
    val specs: List<DashboardNutrientSpec>,
    val totalsByCode: NutrientMap,
    val avgByCode: Map<String, Double>,
    val okStreakByCode: Map<String, Int>,
    val targetsByCode: Map<String, UserNutrientTarget>,
    val iouByCode: Map<String, Double>,
)

private data class LaxDayMacroSettings(
    val caloriesLimitKcal: Double,
    val proteinGoalG: Double,
    val carbsLimitG: Double,
    val fatLimitG: Double,
)

private data class LaxDayGoalSettings(
    val caloriesLimitKcal: Double,
    val proteinGoalG: Double,
    val carbsLimitG: Double,
    val fatLimitG: Double,
    val sodiumLimitMg: Double,
    val sugarLimitG: Double,
) {
    fun effectiveTargetFor(code: String): EffectiveNutrientTarget? {
        val normalized = code.normalizedNutrientCode()

        return when {
            normalized == MacroKeys.CALORIES.value.normalizedNutrientCode() ||
                    normalized == NutrientKey.CALORIES_KCAL.value.normalizedNutrientCode() -> {
                EffectiveNutrientTarget(
                    min = null,
                    target = null,
                    max = caloriesLimitKcal
                )
            }

            normalized == MacroKeys.PROTEIN.value.normalizedNutrientCode() ||
                    normalized == NutrientKey.PROTEIN_G.value.normalizedNutrientCode() -> {
                EffectiveNutrientTarget(
                    min = proteinGoalG,
                    target = null,
                    max = null
                )
            }

            normalized == MacroKeys.CARBS.value.normalizedNutrientCode() ||
                    normalized == NutrientKey.CARBS_G.value.normalizedNutrientCode() -> {
                EffectiveNutrientTarget(
                    min = null,
                    target = null,
                    max = carbsLimitG
                )
            }

            normalized == MacroKeys.FAT.value.normalizedNutrientCode() ||
                    normalized == NutrientKey.FAT_G.value.normalizedNutrientCode() -> {
                EffectiveNutrientTarget(
                    min = null,
                    target = null,
                    max = fatLimitG
                )
            }

            normalized in SodiumCodeAliases -> {
                EffectiveNutrientTarget(
                    min = null,
                    target = null,
                    max = sodiumLimitMg
                )
            }

            normalized in TotalSugarCodeAliases -> {
                EffectiveNutrientTarget(
                    min = null,
                    target = null,
                    max = sugarLimitG
                )
            }

            else -> null
        }
    }
}

private data class EffectiveNutrientTarget(
    val min: Double?,
    val target: Double?,
    val max: Double?,
)

private fun UserNutrientTarget?.toEffectiveNutrientTarget(): EffectiveNutrientTarget =
    EffectiveNutrientTarget(
        min = this?.minPerDay,
        target = this?.targetPerDay,
        max = this?.maxPerDay
    )

private val SodiumCodeAliases = setOf(
    "NA",
    "SODIUM",
    "SODIUM_MG",
    "1093"
)

private val TotalSugarCodeAliases = setOf(
    "SUGAR",
    "SUGARS",
    "SUGAR_TOTAL",
    "SUGARS_TOTAL",
    "TOTAL_SUGAR",
    "TOTAL_SUGARS",
    "SUGAR_TOT",
    "SUGARS_TOT",
    "SUGARSTOT",
    "2000"
)

private fun String.normalizedNutrientCode(): String =
    trim()
        .uppercase()
        .replace(Regex("[^A-Z0-9]+"), "_")
        .trim('_')

/**
 * FUTURE AI ASSISTANT NOTES
 *
 * - Dashboard nutrient cards do not use ObserveDailyNutrientStatusesUseCase directly.
 *   If lax-day dashboard values stop working, check this use case first.
 *
 * - Lax rules day handling must not edit logs, foods, recipes, normal targets, or snapshots.
 *   It only swaps the effective dashboard evaluation bounds for marked dates.
 *
 * - Keep rolling averages unchanged here for now. Report-level include/exclude lax-day
 *   filters should be implemented in report-specific use cases, not by mutating daily logs.
 *
 * - Sodium and total sugar alias matching is intentionally conservative.
 *   If the nutrient catalog uses different canonical codes, add aliases here rather than
 *   changing stored nutrient codes.
 */
