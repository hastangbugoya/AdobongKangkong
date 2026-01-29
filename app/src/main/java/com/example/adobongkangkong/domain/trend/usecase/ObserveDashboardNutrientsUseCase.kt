package com.example.adobongkangkong.domain.trend.usecase

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.repository.NutrientRepository
import com.example.adobongkangkong.domain.repository.UserNutrientTargetRepository
import com.example.adobongkangkong.domain.trend.model.DashboardNutrientSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * Produces the dashboard nutrient "spec" list (metadata + targets) in stable display order:
 *
 * - Always includes: CALORIES_KCAL, PROTEIN_G, CARBS_G, FAT_G
 * - Plus: up to 2 user-pinned nutrient keys (already sanitized upstream)
 *
 * Output is config-only (no consumed amounts).
 * Consumption totals + LOW/OK/HIGH statuses are computed by the dashboard aggregation pipeline.
 */
class ObserveDashboardNutrientsUseCase @Inject constructor(
    private val observeDashboardNutrientKeys: ObserveDashboardNutrientKeysUseCase,
    private val nutrientRepo: NutrientRepository,
    private val targetRepo: UserNutrientTargetRepository
) {
    operator fun invoke(): Flow<List<DashboardNutrientSpec>> =
        combine(
            observeDashboardNutrientKeys(),      // Flow<List<NutrientKey>>
            nutrientRepo.observeAllNutrients(),  // Flow<List<Nutrient>> (adapt type if yours differs)
            targetRepo.observeTargets()          // Flow<Map<String, UserNutrientTarget>>
        ) { keys, nutrients, targetsByCode ->

            // Normalize repo nutrients into NutrientKey lookups (keeps domain consistent even if repo model uses String codes).
            val nutrientByKey = nutrients.associateBy { it.code.toNutrientKey() }

            keys.map { key ->
                val meta = nutrientByKey[key]
                val target = targetsByCode[key.value] // targets map is keyed by canonical nutrient code string

                DashboardNutrientSpec(
                    code = key.value,
                    displayName = meta?.displayName ?: key.value,
                    unit = meta?.unit?.name,
                    targetPerDay = target?.targetPerDay,
                    minPerDay = target?.minPerDay,
                    maxPerDay = target?.maxPerDay
                )
            }
        }

    private fun String.toNutrientKey(): NutrientKey =
        NutrientKey(trim().uppercase())
}
