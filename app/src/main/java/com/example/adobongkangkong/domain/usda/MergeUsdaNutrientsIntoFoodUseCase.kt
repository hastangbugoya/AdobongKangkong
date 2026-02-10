package com.example.adobongkangkong.domain.usda

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity
import com.example.adobongkangkong.domain.model.FoodNutrientRow
import com.example.adobongkangkong.domain.repository.FoodNutrientRepository
import javax.inject.Inject

/**
 * Merges USDA nutrients into an existing food.
 *
 * Rules:
 * 1) If USDA and MANUAL collide on (nutrientId + basisType), USDA wins.
 * 2) MANUAL nutrients not present in USDA are preserved.
 *
 * No UI concerns. No deletes. Deterministic merge.
 */
class MergeUsdaNutrientsIntoFoodUseCase @Inject constructor(
    private val foodNutrientRepository: FoodNutrientRepository
) {

    suspend operator fun invoke(
        foodId: Long,
        usdaNutrients: List<FoodNutrientRow>
    ) {
        // Read existing nutrients
        val existing = foodNutrientRepository.getForFood(foodId)

        // Index USDA nutrients by collision key
        val usdaByKey = usdaNutrients.associateBy {
            NutrientKey(
                nutrientId = it.nutrient.id,
                basisType = it.basisType
            )
        }

        val merged = mutableListOf<FoodNutrientRow>()

        // 1) USDA always wins on collision
        usdaByKey.values.forEach { usda ->
            merged.add(usda)
        }

        // 2) Keep MANUAL nutrients only if USDA does not contain them
        existing.forEach { manual ->
            val key = NutrientKey(
                nutrientId = manual.nutrient.id,
                basisType = manual.basisType
            )

            if (key !in usdaByKey) {
                merged += manual
            }
        }

        // Persist merged result
        foodNutrientRepository.replaceForFood(
            foodId = foodId,
            rows = merged
        )
    }

    private data class NutrientKey(
        val nutrientId: Long,
        val basisType: BasisType
    )
}
