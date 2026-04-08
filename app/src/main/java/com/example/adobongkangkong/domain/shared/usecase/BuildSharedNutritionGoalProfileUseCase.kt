package com.example.adobongkangkong.domain.shared.usecase

import com.example.adobongkangkong.domain.model.UserNutrientTarget
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.repository.NutrientRepository
import com.example.adobongkangkong.domain.repository.UserNutrientTargetRepository
import com.example.adobongkangkong.domain.shared.model.GoalMacroTargets
import com.example.adobongkangkong.domain.shared.model.GoalNutrientTarget
import com.example.adobongkangkong.domain.shared.model.GoalRange
import com.example.adobongkangkong.domain.shared.model.SharedNutritionGoalProfile
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * BuildSharedNutritionGoalProfileUseCase
 *
 * ## Purpose
 * Produces a one-shot [SharedNutritionGoalProfile] representing AdobongKangkong's
 * current active nutrition intent.
 *
 * This is the producer-side entry point for the AK nutrition goal export contract.
 *
 * ## Architecture
 * This use case is intentionally a thin adapter over existing trusted app state:
 * - [UserNutrientTargetRepository] for configured nutrient target ranges
 * - [NutrientRepository] for stable display metadata (name + unit)
 *
 * ## Critical rules
 * - Do NOT invent goal entities or fake history here.
 * - Do NOT introduce new nutrition math here.
 * - Do NOT persist anything here.
 * - Do NOT add HH-specific logic here.
 *
 * This layer must remain:
 *
 *     Existing AK target state -> Shared goal export contract
 *
 * ## Behavior
 * - Reads the latest configured nutrient targets
 * - Maps macro targets from the same target source
 * - Exports remaining target-configured nutrients as nutrient entries
 * - Resolves nutrient display metadata from the nutrient repository when available
 *
 * ## Notes
 * - The export intentionally represents a single current active profile snapshot,
 *   not a multi-goal system.
 * - Macro targets are exported separately as first-class fields.
 * - Nutrient entries exclude the dedicated macro nutrients already represented in
 *   the macro section to avoid duplicate consumer handling.
 * - Pinned metadata is intentionally left null for now unless/until a clean
 *   existing producer path is added.
 */
class BuildSharedNutritionGoalProfileUseCase @Inject constructor(
    private val targetsRepository: UserNutrientTargetRepository,
    private val nutrientRepository: NutrientRepository
) {

    suspend operator fun invoke(): SharedNutritionGoalProfile {
        val targets = targetsRepository.observeTargets().first()

        val macros = GoalMacroTargets(
            calories = targets.toGoalRange(NutrientKey.CALORIES_KCAL.value),
            protein = targets.toGoalRange(NutrientKey.PROTEIN_G.value),
            carbs = targets.toGoalRange(NutrientKey.CARBS_G.value),
            fat = targets.toGoalRange(NutrientKey.FAT_G.value)
        )

        val macroCodes = setOf(
            NutrientKey.CALORIES_KCAL.value,
            NutrientKey.PROTEIN_G.value,
            NutrientKey.CARBS_G.value,
            NutrientKey.FAT_G.value
        )

        val nutrients = mutableListOf<GoalNutrientTarget>()

        for ((code, target) in targets) {
            if (code in macroCodes) continue

            val nutrient = nutrientRepository.getByCode(code)

            nutrients += GoalNutrientTarget(
                code = code,
                name = nutrient?.displayName ?: code,
                unit = nutrient?.unit?.symbol.orEmpty(),
                min = target.minPerDay,
                target = target.targetPerDay,
                max = target.maxPerDay,
                isPinnedInAk = null
            )
        }

        val sortedNutrients = nutrients.sortedWith(
            compareBy<GoalNutrientTarget> { it.name.lowercase() }
                .thenBy { it.code }
        )

        return SharedNutritionGoalProfile(
            schemaVersion = SharedNutritionGoalProfile.CURRENT_SCHEMA_VERSION,
            exportedAtEpochMs = System.currentTimeMillis(),
            macros = macros,
            nutrients = sortedNutrients
        )
    }

    private fun Map<String, UserNutrientTarget>.toGoalRange(code: String): GoalRange {
        val target = this[code]
        return GoalRange(
            min = target?.minPerDay,
            target = target?.targetPerDay,
            max = target?.maxPerDay
        )
    }
}