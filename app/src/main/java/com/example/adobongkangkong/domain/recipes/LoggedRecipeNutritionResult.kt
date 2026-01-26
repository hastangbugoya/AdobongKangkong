package com.example.adobongkangkong.domain.recipes

import com.example.adobongkangkong.domain.nutrition.NutrientMap

/**
 * Result of computing nutrition for a single recipe log event.
 *
 * - totals: nutrients for the logged amount (grams or servings).
 * - warnings: includes gating warnings when the requested logging mode is not supported.
 * - isAllowed: false means "block at point-of-use" (e.g., trying to log by grams without cooked yield).
 */
data class LoggedRecipeNutritionResult(
    val totals: NutrientMap,
    val warnings: List<RecipeNutritionWarning>,
    val isAllowed: Boolean
)
