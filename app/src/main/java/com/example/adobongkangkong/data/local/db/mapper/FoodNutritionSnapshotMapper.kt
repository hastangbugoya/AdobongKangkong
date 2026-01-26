package com.example.adobongkangkong.data.local.db.mapper

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import com.example.adobongkangkong.domain.recipes.FoodNutritionSnapshot

/**
 * Normalize persisted food nutrient rows into a domain snapshot expressed per 1 gram.
 *
 * - PER_100G    -> amount/100
 * - PER_SERVING -> amount/gramsPerServing (only if gramsPerServing is valid)
 *
 * Lax import: if normalization can't be done (e.g., PER_SERVING but missing gramsPerServing),
 * those rows are skipped; downstream use cases emit warnings when data is missing.
 */
fun toFoodNutritionSnapshot(
    foodId: Long,
    gramsPerServing: Double?,
    rows: List<FoodNutrientEntity>,
    nutrientCodeById: Map<Long, String>
): FoodNutritionSnapshot {
    val grams = gramsPerServing?.takeIf { it > 0.0 }

    val perGram = rows.mapNotNull { row ->
        val code = nutrientCodeById[row.nutrientId] ?: return@mapNotNull null
        val key = NutrientKey(code)

        val perGramValue = when (row.basisType) {
            BasisType.PER_100G -> row.nutrientAmountPerBasis / 100.0
            BasisType.PER_SERVING -> grams?.let { row.nutrientAmountPerBasis / it }
        }

        perGramValue?.let { key to it }
    }.toMap()

    return FoodNutritionSnapshot(
        foodId = foodId,
        gramsPerServing = gramsPerServing,
        nutrientsPerGram = perGram.takeIf { it.isNotEmpty() }?.let { NutrientMap(it) }
    )
}
