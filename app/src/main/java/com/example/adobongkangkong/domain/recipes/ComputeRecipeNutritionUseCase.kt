package com.example.adobongkangkong.domain.recipes

import com.example.adobongkangkong.domain.nutrition.NutrientMap
import com.example.adobongkangkong.domain.nutrition.dividedBy
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import javax.inject.Inject

/**
 * Computes recipe nutrition from ingredient servings using food nutrition snapshots.
 *
 * Import is lax:
 * - Missing foods, grams-per-serving, or nutrients become warnings.
 * - Computation continues using 0 for missing pieces.
 *
 * Correctness is enforced at point-of-use:
 * - perServing is only produced if servingsYield is valid.
 * - perCookedGram is only produced if totalYieldGrams is valid.
 */
class ComputeRecipeNutritionUseCase @Inject constructor(
    private val snapshotRepo: FoodNutritionSnapshotRepository
) {

    /**
     * Loads snapshots for all ingredient foods and computes recipe totals + derived views.
     *
     * Warnings are returned for any missing or invalid data.
     */
    suspend operator fun invoke(recipe: Recipe): RecipeNutritionResult {
        // Batch load snapshots for all food IDs referenced by ingredients.
        val foodIds = recipe.ingredients.map { it.foodId }.distinct()
        val foodsById: Map<Long, FoodNutritionSnapshot> = snapshotRepo.getSnapshots(foodIds.toSet())

        return computeWithSnapshots(recipe = recipe, foodsById = foodsById)
    }

    /**
     * Pure computation step that keeps the existing warning behavior.
     *
     * This is useful for tests (you can pass a fake foodsById map),
     * while [invoke] remains the production path that loads snapshots.
     */
    internal fun computeWithSnapshots(
        recipe: Recipe,
        foodsById: Map<Long, FoodNutritionSnapshot>
    ): RecipeNutritionResult {

        val warnings = mutableListOf<RecipeNutritionWarning>()

        val totals = recipe.ingredients.fold(NutrientMap.EMPTY) { acc, ingredient ->
            val foodId = ingredient.foodId
            val servings = ingredient.servings

            if (servings <= 0.0) {
                warnings += RecipeNutritionWarning.IngredientServingsNonPositive(foodId, servings)
                return@fold acc
            }

            val food = foodsById[foodId]
            if (food == null) {
                warnings += RecipeNutritionWarning.MissingFood(foodId)
                return@fold acc
            }

            val gramsPerServing = food.gramsPerServing
            if (gramsPerServing == null || gramsPerServing <= 0.0) {
                warnings += RecipeNutritionWarning.MissingGramsPerServing(foodId)
                return@fold acc
            }

            val nutrientsPerGram = food.nutrientsPerGram
            if (nutrientsPerGram == null) {
                warnings += RecipeNutritionWarning.MissingNutrientsPerGram(foodId)
                return@fold acc
            }

            val ingredientGrams = servings * gramsPerServing
            acc + nutrientsPerGram.scaledBy(ingredientGrams)
        }

        val servingsYield = recipe.servingsYield
        val totalYieldGrams = recipe.totalYieldGrams

        val perServing: NutrientMap? = when {
            servingsYield == null -> {
                warnings += RecipeNutritionWarning.MissingServingsYield
                null
            }
            servingsYield <= 0 -> {
                warnings += RecipeNutritionWarning.InvalidServingsYield(servingsYield)
                null
            }
            else -> totals.dividedBy(servingsYield.toDouble())
        }

        val perCookedGram: NutrientMap? = when {
            totalYieldGrams == null -> {
                warnings += RecipeNutritionWarning.MissingTotalYieldGrams
                null
            }
            totalYieldGrams <= 0 -> {
                warnings += RecipeNutritionWarning.InvalidTotalYieldGrams(totalYieldGrams)
                null
            }
            else -> totals.dividedBy(totalYieldGrams.toDouble())
        }

        val gramsPerServingCooked: Double? = when {
            servingsYield != null && servingsYield > 0 &&
                    totalYieldGrams != null && totalYieldGrams > 0 ->
                totalYieldGrams.toDouble() / servingsYield.toDouble()
            else -> null
        }

        return RecipeNutritionResult(
            totals = totals,
            perServing = perServing,
            perCookedGram = perCookedGram,
            gramsPerServingCooked = gramsPerServingCooked,
            warnings = warnings.distinct()
        )
    }
}
