package com.example.adobongkangkong.domain.recipes

import android.util.Log
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import com.example.adobongkangkong.domain.nutrition.dividedBy
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import javax.inject.Inject

/**
 * Computes recipe nutrition using domain nutrition snapshots.
 *
 * Design principles:
 * - Import is lax → warn instead of failing.
 * - Enforce correctness at point-of-use.
 * - All math is done using per-gram normalized snapshots.
 */
class ComputeRecipeNutritionForSnapshotUseCase @Inject constructor(
    private val snapshotRepo: FoodNutritionSnapshotRepository
) {

    /**
     * Loads food snapshots in batch and computes full recipe nutrition.
     */
    suspend operator fun invoke(recipe: Recipe): RecipeNutritionResult {
        val foodIds = recipe.ingredients.map { it.foodId }.toSet()
        val foodsById = snapshotRepo.getSnapshots(foodIds)

        return computeWithSnapshots(
            recipe = recipe,
            foodsById = foodsById
        )
    }

    /**
     * Pure computation step.
     *
     * This is split out for:
     * - unit testing
     * - preview / simulation
     * - reuse by other flows (import validation, etc.)
     */
    internal fun computeWithSnapshots(
        recipe: Recipe,
        foodsById: Map<Long, FoodNutritionSnapshot>
    ): RecipeNutritionResult {

        val warnings = mutableListOf<RecipeNutritionWarning>()

        val totals = recipe.ingredients.fold(NutrientMap.Companion.EMPTY) { acc, ingredient ->
            val foodId = ingredient.foodId
            // ⚠️ Default null servings to 1.0.
            // UI layer allows nullable servings during editing, but the domain draft
            // requires a non-null Double for nutrition scaling and planner expansion.
            // We intentionally default to 1.0 (NOT 0.0) to preserve recipe math integrity
            // and make unexpected null states visible in the UI.
            val servings = ingredient.servings ?: 1.0

            if (servings <= 0.0) {
                warnings += RecipeNutritionWarning.IngredientServingsNonPositive(foodId, servings)
                return@fold acc
            }

            val snapshot = foodsById[foodId]
            if (snapshot == null) {
                warnings += RecipeNutritionWarning.MissingFood(foodId)
                return@fold acc
            }

            val gramsPerServingUnit = snapshot.gramsPerServingUnit
            if (gramsPerServingUnit == null || gramsPerServingUnit <= 0.0) {
                warnings += RecipeNutritionWarning.MissingGramsPerServing(foodId)
                return@fold acc
            }

            val nutrientsPerGram = snapshot.nutrientsPerGram
            if (nutrientsPerGram == null) {
                warnings += RecipeNutritionWarning.MissingNutrientsPerGram(foodId)
                return@fold acc
            }

            val grams = servings * gramsPerServingUnit
            acc + nutrientsPerGram.scaledBy(grams)
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
        Log.d(
            "Meow",
            "Snapshot> recipeId=${recipe.id} ingredients=${recipe.ingredients.size} " +
                    "snapshotsLoaded=${foodsById.size} totalsSize=${totals.entries().size} warnings=${warnings.size}"
        )
        return RecipeNutritionResult(
            totals = totals,
            perServing = perServing,
            perCookedGram = perCookedGram,
            gramsPerServingCooked = gramsPerServingCooked,
            warnings = warnings.distinct()
        )
    }
}