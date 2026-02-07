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
    val gramsPerServingUnit: Double?,
    val mlPerServingUnit: Double?,
    val nutrientsPerGram: NutrientMap?,
    val nutrientsPerMilliliter: NutrientMap?
)

/** Scales a mass-normalized snapshot by [grams]. Missing nutrients are treated as zero. */
fun FoodNutritionSnapshot.nutrientsForGrams(grams: Double): NutrientMap =
    (nutrientsPerGram ?: NutrientMap.EMPTY).scaledBy(grams)

/** Scales a volume-normalized snapshot by [milliliters]. Missing nutrients are treated as zero. */
fun FoodNutritionSnapshot.nutrientsForMilliliters(milliliters: Double): NutrientMap =
    (nutrientsPerMilliliter ?: NutrientMap.EMPTY).scaledBy(milliliters)



/** 2026-2-6 4:11pm
 * Minimal nutrition view of a food needed to compute recipes and list sorting.
 *
 * Canonical conventions:
 * - Mass-grounded foods:
 *     - nutrientsPerGram expresses nutrient amounts for 1 gram of the food.
 * - Volume-grounded foods:
 *     - nutrientsPerMilliliter expresses nutrient amounts for 1 mL of the food.
 *
 * Bridges:
 * - gramsPerServingUnit: grams in one servingUnit (when ingredients are in servings)
 * - mlPerServingUnit: milliliters in one servingUnit (when ingredients are in servings)
 *
 * Import is intentionally lax:
 * - Either density map may be null (caller decides warnings).
 *
 * FUTURE-YOU NOTE (2026-02-06):
 * - Never convert grams <-> mL here (no density guessing).
 */

/** 2026-2-6 12:00pm
 * Immutable, canonical nutrition snapshot for a food.
 *
 * Exactly ONE of the following must be non-null:
 * - per100g  (mass-grounded foods)
 * - per100ml (volume-grounded foods)
 *
 * FUTURE-YOU NOTE (2026-02-06):
 * - Never allow both bases to be non-null.
 * - Never convert grams <-> mL without an explicit density field.
 * - USDA_REPORTED_SERVING must be resolved BEFORE creating a snapshot.
 */