package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.data.local.db.dao.LaxRuleDayDao
import com.example.adobongkangkong.domain.model.DailyNutrientStatus
import com.example.adobongkangkong.domain.nutrition.MacroKeys
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.repository.UserNutrientTargetRepository
import com.example.adobongkangkong.domain.settings.UserPreferencesRepository
import com.example.adobongkangkong.domain.trend.model.TargetStatus
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * ObserveDailyNutrientStatusesUseCase
 *
 * ## Purpose
 * Produces a reactive list of [DailyNutrientStatus] for a given date, combining:
 * - actual consumed nutrient totals for the day,
 * - user-configured daily nutrient targets, and
 * - lax rules day markers/settings when the date is marked as a lax rules day.
 *
 * ## What are nutrition targets?
 * A nutrition target defines desired intake bounds for a specific nutrient code:
 * - `minPerDay` → lower bound (optional)
 * - `targetPerDay` → ideal target (optional, informational)
 * - `maxPerDay` → upper bound (optional)
 *
 * These are user-configurable and stored via [UserNutrientTargetRepository].
 *
 * This use case evaluates whether the user is:
 * - BELOW minimum → [TargetStatus.LOW]
 * - ABOVE maximum → [TargetStatus.HIGH]
 * - Within range → [TargetStatus.OK]
 * - No bounds defined → [TargetStatus.NO_TARGET]
 *
 * ## Lax rules day behavior
 * When the date is marked in `lax_rule_days`, this use case keeps the logged nutrient
 * totals unchanged but evaluates supported nutrients against alternate lax-day values.
 * This is intentionally only an evaluation/rules change. It does not rewrite logs,
 * recipe snapshots, meal entries, or normal daily targets.
 *
 * Initial lax-day supported nutrients:
 * - Calories → alternate maximum/target
 * - Protein → alternate minimum/target
 * - Carbs → alternate maximum/target
 * - Fat → alternate maximum/target
 * - Sodium → alternate maximum/target
 * - Total sugar → alternate maximum/target
 *
 * ## Rationale
 * Daily nutrient statuses power:
 * - Trend views
 * - Daily dashboards
 * - Target compliance indicators
 * - Highlighting nutrients that need attention
 *
 * Centralizing this logic ensures:
 * - Consistent evaluation rules across UI
 * - Single definition of LOW/HIGH/OK semantics
 * - Reactive updates when totals, targets, lax-day marks, or lax-day settings change
 *
 * ## Behavior
 * - Combines:
 *   1) [ObserveDailyNutritionTotalsUseCase] (consumed totals for the date)
 *   2) [UserNutrientTargetRepository.observeTargets] (all configured nutrient targets)
 *   3) [LaxRuleDayDao.observeForDate] (whether the date is marked as lax rules day)
 *   4) [UserPreferencesRepository] lax-day alternate goal values
 * - Produces a list of [DailyNutrientStatus] for each nutrient that has a normal target.
 *
 * ## Important rule
 * Only nutrients that have configured normal targets are included.
 * This use case does NOT:
 * - Automatically include all nutrients
 * - Include pinned nutrients unless they also have targets
 * - Add lax-day-only nutrients that the user has not otherwise targeted
 *
 * (Pinned nutrients are a separate UI concern unless target-configured.)
 *
 * ## Parameters
 * @param date The calendar day being evaluated.
 * @param zoneId Used by downstream totals calculation for day boundaries.
 *
 * ## Return
 * @return Flow<List<DailyNutrientStatus>>
 * Emits new values whenever:
 * - consumed totals change (e.g., new log entry),
 * - user nutrient targets change,
 * - lax rules day marker for this date changes, OR
 * - lax rules day alternate goal values change.
 *
 * ## Edge cases
 * - If a nutrient has a target but no consumption for the day → consumed defaults to 0.0.
 * - If both min and max are null → status = NO_TARGET.
 * - If only min is defined → only LOW detection applies.
 * - If only max is defined → only HIGH detection applies.
 * - If the day is lax but a nutrient is not one of the initial supported lax nutrients,
 *   its normal target bounds are used unchanged.
 */
class ObserveDailyNutrientStatusesUseCase @Inject constructor(
    private val observeTotals: ObserveDailyNutritionTotalsUseCase,
    private val targetsRepo: UserNutrientTargetRepository,
    private val laxRuleDayDao: LaxRuleDayDao,
    private val userPreferencesRepository: UserPreferencesRepository,
) {

    operator fun invoke(date: LocalDate, zoneId: ZoneId): Flow<List<DailyNutrientStatus>> =
        combine(
            observeTotals(date, zoneId),
            targetsRepo.observeTargets(),
            laxRuleDayDao.observeForDate(date.toEpochDay()),
            observeLaxDayGoalSettings()
        ) { totals, targets, laxRuleDay, laxSettings ->
            val result = mutableListOf<DailyNutrientStatus>()
            val isLaxRuleDay = laxRuleDay != null

            // Only show nutrients that have targets (logic first).
            for ((code, target) in targets) {
                val consumed = totals.totalsByCode[NutrientKey(code)] ?: 0.0
                val effectiveTarget = if (isLaxRuleDay) {
                    laxSettings.effectiveTargetFor(code)
                } else {
                    null
                } ?: EffectiveNutrientTarget(
                    min = target.minPerDay,
                    target = target.targetPerDay,
                    max = target.maxPerDay
                )

                result += DailyNutrientStatus(
                    nutrientCode = code,
                    consumed = consumed,
                    min = effectiveTarget.min,
                    target = effectiveTarget.target,
                    max = effectiveTarget.max,
                    status = computeStatus(
                        consumed = consumed,
                        min = effectiveTarget.min,
                        max = effectiveTarget.max
                    )
                )
            }
            result
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

    /**
     * Computes the status of a nutrient based on consumed amount and bounds.
     *
     * Rules:
     * - If min != null AND consumed < min → LOW
     * - If max != null AND consumed > max → HIGH
     * - If no bounds defined → NO_TARGET
     * - Otherwise → OK
     */
    private fun computeStatus(consumed: Double, min: Double?, max: Double?): TargetStatus =
        when {
            min != null && consumed < min -> TargetStatus.LOW
            max != null && consumed > max -> TargetStatus.HIGH
            min == null && max == null -> TargetStatus.NO_TARGET
            else -> TargetStatus.OK
        }
}

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
            normalized == MacroKeys.CALORIES.value.normalizedNutrientCode() -> {
                EffectiveNutrientTarget(
                    min = null,
                    target = caloriesLimitKcal,
                    max = caloriesLimitKcal
                )
            }

            normalized == MacroKeys.PROTEIN.value.normalizedNutrientCode() -> {
                EffectiveNutrientTarget(
                    min = proteinGoalG,
                    target = proteinGoalG,
                    max = null
                )
            }

            normalized == MacroKeys.CARBS.value.normalizedNutrientCode() -> {
                EffectiveNutrientTarget(
                    min = null,
                    target = carbsLimitG,
                    max = carbsLimitG
                )
            }

            normalized == MacroKeys.FAT.value.normalizedNutrientCode() -> {
                EffectiveNutrientTarget(
                    min = null,
                    target = fatLimitG,
                    max = fatLimitG
                )
            }

            normalized in SodiumCodeAliases -> {
                EffectiveNutrientTarget(
                    min = null,
                    target = sodiumLimitMg,
                    max = sodiumLimitMg
                )
            }

            normalized in TotalSugarCodeAliases -> {
                EffectiveNutrientTarget(
                    min = null,
                    target = sugarLimitG,
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
 * - This file follows the standard two-KDoc pattern:
 *   1) Top KDoc: dev-facing purpose, rationale, target semantics, edge rules.
 *   2) Bottom KDoc: constraints/invariants for automated edits.
 *
 * - Do NOT change inclusion rules silently.
 *   This use case intentionally only returns nutrients with normal configured targets.
 *   If pinned nutrients must be included regardless of target, or if lax-day-only
 *   nutrients must appear without normal targets, create a new use case or explicitly
 *   extend behavior.
 *
 * - Do NOT move target evaluation logic into UI.
 *   computeStatus(...) is the canonical definition of LOW/HIGH/OK/NO_TARGET.
 *
 * - Lax rules day handling must not edit or reinterpret logged food amounts.
 *   It only changes the effective goal bounds used for status calculation on marked dates.
 *
 * - Sodium and total sugar alias matching is intentionally conservative.
 *   If the nutrient catalog uses different canonical codes, add aliases here rather than
 *   changing stored nutrient codes.
 *
 * - Keep this reactive.
 *   If converting to non-Flow implementation, ensure callers are updated accordingly.
 *
 * - Day membership logic is determined by ObserveDailyNutritionTotalsUseCase.
 *   This use case must not introduce timestamp-based filtering.
 */
