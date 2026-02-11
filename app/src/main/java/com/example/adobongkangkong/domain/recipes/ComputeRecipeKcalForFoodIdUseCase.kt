package com.example.adobongkangkong.domain.recipes

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.repository.RecipeRepository
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Computes total kcal (batch) and kcal per serving for a recipe identified by foodId.
 *
 * - foodId must belong to a recipe food (RecipeRepository.getRecipeByFoodId != null)
 * - Does NOT require grams or batches
 * - Uses servingsYield for per-serving computation
 */
class ComputeRecipeKcalForFoodIdUseCase @Inject constructor(
    private val recipes: RecipeRepository,
    private val computeNutrition: ComputeRecipeNutritionForSnapshotUseCase
) {

    suspend operator fun invoke(foodId: Long): Result {
        val header = recipes.getRecipeByFoodId(foodId)
            ?: return Result.NotRecipeFood(foodId)

        if (header.servingsYield <= 0.0) {
            return Result.InvalidServings(foodId)
        }

        val ingredientLines = recipes.getIngredients(header.recipeId)

        val recipe = Recipe(
            id = header.recipeId,
            name = "", // not required for nutrition math
            ingredients = ingredientLines.map { line ->
                RecipeIngredient(
                    foodId = line.ingredientFoodId,
                    servings = line.ingredientServings
                )
            },
            servingsYield = header.servingsYield,
            totalYieldGrams = header.totalYieldGrams
        )

        val nutrition = computeNutrition(recipe)

        val totalKcal = nutrition.totals[NutrientKey.CALORIES_KCAL]
            ?: return Result.MissingCalories(foodId)

        val perServingKcal =
            (totalKcal / header.servingsYield).roundToInt()

        return Result.Success(
            totalKcal = totalKcal.roundToInt(),
            perServingKcal = perServingKcal
        )
    }

    sealed class Result {
        data class Success(
            val totalKcal: Int,
            val perServingKcal: Int
        ) : Result()

        data class NotRecipeFood(val foodId: Long) : Result()
        data class InvalidServings(val foodId: Long) : Result()
        data class MissingCalories(val foodId: Long) : Result()
    }
}
