package com.example.adobongkangkong.domain.trend.usecase

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.repository.UserPinnedNutrientRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Produces the list of nutrient keys to display on the dashboard:
 * - Always shows: Calories, Protein, Carbs, Fat
 * - Plus: up to 2 user-pinned nutrients
 * - If no pinned nutrients exist yet, defaults to: Fiber + Sodium
 */
class ObserveDashboardNutrientKeysUseCase @Inject constructor(
    private val pinnedRepo: UserPinnedNutrientRepository
) {
    operator fun invoke(): Flow<List<NutrientKey>> =
        pinnedRepo.observePinnedKeys()
            .map { pinned ->
                val fixed = fixedDashboardKeys()
                val fixedSet = fixed.toSet()

                val cleanedPinned = pinned
                    .map { it.canonical() }
                    .distinct()
                    .filterNot { it in fixedSet }
                    .take(2)

                val defaults = listOf(
                    NutrientKey("FIBER_G"),
                    NutrientKey("SODIUM_MG")
                )

                val finalPinned =
                    if (cleanedPinned.isEmpty()) defaults
                    else cleanedPinned

                fixed + finalPinned.take(2)
            }

    private fun fixedDashboardKeys(): List<NutrientKey> = listOf(
        NutrientKey("CALORIES_KCAL"),
        NutrientKey("PROTEIN_G"),
        NutrientKey("CARBS_G"),
        NutrientKey("FAT_G")
    )

    private fun NutrientKey.canonical(): NutrientKey =
        NutrientKey(value.trim().uppercase())
}
