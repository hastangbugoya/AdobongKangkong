package com.example.adobongkangkong.domain.recipes

import com.example.adobongkangkong.domain.nutrition.NutrientMap
import javax.inject.Inject

/**
 * Computes nutrition totals for a recipe log event.
 *
 * Point-of-use enforcement:
 * - Logging BY_COOKED_GRAMS requires cooked yield (totalYieldGrams > 0).
 * - Logging BY_SERVINGS requires servingsYield > 0.
 *
 * Import is lax:
 * - Missing nutrient data is surfaced via [RecipeNutritionWarning] (from the upstream computation).
 * - This use case adds "gating" warnings when the selected log mode isn't supported.
 */
class ComputeLoggedRecipeNutritionUseCase @Inject constructor() {

    operator fun invoke(
        recipeNutrition: RecipeNutritionResult,
        input: RecipeLogInput
    ): LoggedRecipeNutritionResult {
        val warnings = recipeNutrition.warnings.toMutableList()

        return when (input) {
            is RecipeLogInput.ByCookedGrams -> {
                val grams = input.grams
                if (grams <= 0.0) {
                    warnings += RecipeNutritionWarning.InvalidTotalYieldGrams(grams)
                    return LoggedRecipeNutritionResult(
                        totals = NutrientMap.EMPTY,
                        warnings = warnings,
                        isAllowed = false
                    )
                }

                val perCookedGram = recipeNutrition.perCookedGram
                if (perCookedGram == null) {
                    // Gate: can't log by grams without cooked yield
                    warnings += RecipeNutritionWarning.MissingTotalYieldGrams
                    return LoggedRecipeNutritionResult(
                        totals = NutrientMap.EMPTY,
                        warnings = warnings,
                        isAllowed = false
                    )
                }

                LoggedRecipeNutritionResult(
                    totals = perCookedGram.scaledBy(grams),
                    warnings = warnings,
                    isAllowed = true
                )
            }

            is RecipeLogInput.ByServings -> {
                val servings = input.servings
                if (servings <= 0.0) {
                    warnings += RecipeNutritionWarning.InvalidServingsYield(servings)
                    return LoggedRecipeNutritionResult(
                        totals = NutrientMap.EMPTY,
                        warnings = warnings,
                        isAllowed = false
                    )
                }

                val perServing = recipeNutrition.perServing
                if (perServing == null) {
                    // Gate: can't log by servings without servingsYield
                    warnings += RecipeNutritionWarning.MissingServingsYield
                    return LoggedRecipeNutritionResult(
                        totals = NutrientMap.EMPTY,
                        warnings = warnings,
                        isAllowed = false
                    )
                }

                LoggedRecipeNutritionResult(
                    totals = perServing.scaledBy(servings),
                    warnings = warnings,
                    isAllowed = true
                )
            }
        }
    }
}
