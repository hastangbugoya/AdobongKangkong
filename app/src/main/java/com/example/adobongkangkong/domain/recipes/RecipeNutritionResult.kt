package com.example.adobongkangkong.domain.recipes

import com.example.adobongkangkong.domain.nutrition.NutrientMap

/**
 * Output of recipe nutrition computation.
 *
 * - totals: nutrition for the entire recipe batch (sum of all ingredients)
 * - perServing: totals / servingsYield (only if servingsYield is valid)
 * - perCookedGram: totals / totalYieldGrams (only if totalYieldGrams is valid)
 *
 * Notes:
 * - perCookedGram assumes totals reflect the batch nutrient content (which they do),
 *   and cooked yield represents final cooked weight.
 */
data class RecipeNutritionResult(
    val totals: NutrientMap,
    val perServing: NutrientMap?,
    val perCookedGram: NutrientMap?,
    val gramsPerServingCooked: Double?, // (totalYieldGrams / servingsYield) when available
    val warnings: List<RecipeNutritionWarning>
) {
    val supportsServingView: Boolean get() = perServing != null
    val supportsCookedWeightLogging: Boolean get() = perCookedGram != null
}
