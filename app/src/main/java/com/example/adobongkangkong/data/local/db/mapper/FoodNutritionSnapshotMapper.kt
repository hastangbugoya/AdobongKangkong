package com.example.adobongkangkong.data.local.db.mapper

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import com.example.adobongkangkong.domain.recipes.FoodNutritionSnapshot

/**
 * Normalize persisted food nutrient rows into a domain snapshot expressed per 1 gram.
 *
 * Supported normalization:
 * - PER_100G              -> amount / 100
 * - USDA_REPORTED_SERVING -> amount / gramsPerServingUnit (only if gramsPerServingUnit is valid)
 *
 * Lax import: if normalization can't be done (e.g., USDA_REPORTED_SERVING but missing gramsPerServingUnit),
 * those rows are skipped; downstream use cases emit warnings when data is missing.
 *
 * Note: PER_100ML cannot be normalized to grams without density, so it is skipped here.
 */
fun toFoodNutritionSnapshot(
    foodId: Long,
    gramsPerServingUnit: Double?,
    rows: List<FoodNutrientEntity>,
    nutrientCodeById: Map<Long, String>
): FoodNutritionSnapshot {

    val grams = gramsPerServingUnit?.takeIf { it > 0.0 }

    val perGram = rows.mapNotNull { row ->
        val code = nutrientCodeById[row.nutrientId] ?: return@mapNotNull null
        val key = NutrientKey(code)

        val perGramValue: Double? = when (row.basisType) {
            BasisType.PER_100G -> {
                row.nutrientAmountPerBasis / 100.0
            }

            BasisType.USDA_REPORTED_SERVING -> {
                grams?.let { row.nutrientAmountPerBasis / it }
            }

            BasisType.PER_100ML -> {
                // Can't convert volume-normalized nutrients to grams without density.
                null
            }
        }

        perGramValue?.let { key to it }
    }.toMap()

    return FoodNutritionSnapshot(
        foodId = foodId,
        gramsPerServingUnit = gramsPerServingUnit,
        nutrientsPerGram = perGram.takeIf { it.isNotEmpty() }?.let { NutrientMap(it) }
    )
}
