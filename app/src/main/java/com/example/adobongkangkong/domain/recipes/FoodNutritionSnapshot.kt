package com.example.adobongkangkong.domain.recipes

import com.example.adobongkangkong.domain.nutrition.NutrientMap

/**
 * Minimal nutrition view of a food needed to compute recipes.
 *
 * Domain convention:
 * - nutrientsPerGram: nutrient amounts for 1 gram of the food
 * - gramsPerServingUnit: grams in one serving (required because ingredients are in servings)
 *
 * Import is lax: either may be null, and the calculator will warn + treat missing as zero.
 */
data class FoodNutritionSnapshot(
    val foodId: Long,
    val gramsPerServingUnit: Double?,     // e.g. 30g per serving
    val nutrientsPerGram: NutrientMap? // e.g. protein_g per gram, etc.
)

fun FoodNutritionSnapshot.nutrientsForGrams(grams: Double): NutrientMap =
    (nutrientsPerGram ?: NutrientMap.EMPTY).scaledBy(grams)