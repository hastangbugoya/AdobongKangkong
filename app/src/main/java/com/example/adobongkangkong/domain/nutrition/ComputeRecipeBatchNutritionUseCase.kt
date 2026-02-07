package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.domain.recipes.FoodNutritionSnapshot
import com.example.adobongkangkong.domain.recipes.Recipe
import com.example.adobongkangkong.domain.recipes.RecipeNutritionResult
import com.example.adobongkangkong.domain.recipes.RecipeNutritionWarning
import com.example.adobongkangkong.domain.recipes.nutrientsForGrams
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import com.example.adobongkangkong.domain.repository.RecipeIngredientLine
import com.example.adobongkangkong.domain.repository.RecipeRepository
import javax.inject.Inject

/**
 * Canonical recipe batch nutrition computation (servings-based ingredients).
 *
 * Computes:
 * - totals for the entire recipe batch
 * - perServing (if servingsYield valid)
 * - perCookedGram (if totalYieldGrams valid)
 * - gramsPerServingCooked (if both valid)
 *
 * Entry points:
 * - execute(recipeFoodId: Long): loads RecipeHeader + RecipeIngredientLine from RecipeRepository
 * - execute(recipe: Recipe): computes from an in-memory Recipe (used by logging after overrides)
 */
class ComputeRecipeBatchNutritionUseCase @Inject constructor(
    private val recipeRepo: RecipeRepository,
    private val snapshotRepo: FoodNutritionSnapshotRepository
) {

    /**
     * Computes nutrition for a recipe identified by its recipe "foodId" (editable recipe definition).
     */
    suspend fun execute(recipeFoodId: Long): RecipeNutritionResult {
        val header = recipeRepo.getRecipeByFoodId(recipeFoodId)
            ?: return RecipeNutritionResult(
                totals = NutrientMap.EMPTY,
                perServing = null,
                perCookedGram = null,
                gramsPerServingCooked = null,
                warnings = emptyList()
            )

        val ingredientLines: List<RecipeIngredientLine> = recipeRepo.getIngredients(header.recipeId)

        val ingredientFoodIds: Set<Long> = ingredientLines
            .map { it.ingredientFoodId }
            .toSet()

        val foodsById: Map<Long, FoodNutritionSnapshot> = snapshotRepo.getSnapshots(ingredientFoodIds)

        return computeFromServingsLines(
            servingsYield = header.servingsYield,
            totalYieldGrams = header.totalYieldGrams,
            lines = ingredientLines,
            foodsById = foodsById
        )
    }

    /**
     * Computes nutrition from an in-memory Recipe (used by CreateLogEntryUseCase after applying overrides).
     */
    suspend fun execute(recipe: Recipe): RecipeNutritionResult {
        val ingredientFoodIds: Set<Long> = recipe.ingredients
            .map { it.foodId }
            .toSet()

        val foodsById: Map<Long, FoodNutritionSnapshot> = snapshotRepo.getSnapshots(ingredientFoodIds)

        // Adapt RecipeIngredient -> RecipeIngredientLine shape (servings-only model)
        val lines: List<RecipeIngredientLine> = recipe.ingredients.map {
            RecipeIngredientLine(
                ingredientFoodId = it.foodId,
                ingredientServings = it.servings
            )
        }

        // NOTE: your Recipe model has nullable servingsYield/totalYieldGrams; compute expects Double/Double?
        // We preserve your warning behavior by supplying a sentinel for servingsYield if missing.
        val servingsYield: Double = recipe.servingsYield ?: run {
            // will be warned as MissingServingsYield below, but compute() needs a Double
            0.0
        }

        return computeFromServingsLines(
            servingsYield = servingsYield,
            totalYieldGrams = recipe.totalYieldGrams,
            lines = lines,
            foodsById = foodsById,
            includeMissingServingsYieldWarning = recipe.servingsYield == null
        )
    }

    /**
     * Optional: keeps old call sites working (computeUseCase(recipe)).
     */
    suspend operator fun invoke(recipe: Recipe): RecipeNutritionResult = execute(recipe)

    private fun computeFromServingsLines(
        servingsYield: Double,
        totalYieldGrams: Double?,
        lines: List<RecipeIngredientLine>,
        foodsById: Map<Long, FoodNutritionSnapshot>,
        includeMissingServingsYieldWarning: Boolean = false
    ): RecipeNutritionResult {

        val warnings = mutableListOf<RecipeNutritionWarning>()

        // totals for entire recipe batch
        val totals = lines.fold(NutrientMap.EMPTY) { acc, line ->
            val foodId = line.ingredientFoodId
            val servings = line.ingredientServings

            if (servings <= 0.0) {
                warnings += RecipeNutritionWarning.IngredientServingsNonPositive(foodId, servings)
                return@fold acc
            }

            val snapshot = foodsById[foodId]
            if (snapshot == null) {
                warnings += RecipeNutritionWarning.MissingFood(foodId)
                return@fold acc
            }

            val gpsu = snapshot.gramsPerServingUnit
            if (gpsu == null || gpsu <= 0.0) {
                warnings += RecipeNutritionWarning.MissingGramsPerServing(foodId)
                return@fold acc
            }

            if (snapshot.nutrientsPerGram == null) {
                warnings += RecipeNutritionWarning.MissingNutrientsPerGram(foodId)
                return@fold acc
            }

            val grams = servings * gpsu
            acc + snapshot.nutrientsForGrams(grams)
        }

        // per-serving
        val perServing: NutrientMap? = when {
            includeMissingServingsYieldWarning -> {
                warnings += RecipeNutritionWarning.MissingServingsYield
                null
            }
            servingsYield.isNaN() -> {
                warnings += RecipeNutritionWarning.InvalidServingsYield(servingsYield)
                null
            }
            servingsYield <= 0.0 -> {
                warnings += RecipeNutritionWarning.InvalidServingsYield(servingsYield)
                null
            }
            else -> totals.scaledBy(1.0 / servingsYield)
        }

        // per cooked gram
        val perCookedGram: NutrientMap? = when {
            totalYieldGrams == null -> {
                warnings += RecipeNutritionWarning.MissingTotalYieldGrams
                null
            }
            totalYieldGrams <= 0.0 -> {
                warnings += RecipeNutritionWarning.InvalidTotalYieldGrams(totalYieldGrams)
                null
            }
            else -> totals.scaledBy(1.0 / totalYieldGrams)
        }

        val gramsPerServingCooked: Double? =
            if (!includeMissingServingsYieldWarning && servingsYield > 0.0 && totalYieldGrams != null && totalYieldGrams > 0.0) {
                totalYieldGrams / servingsYield
            } else {
                null
            }

        return RecipeNutritionResult(
            totals = totals,
            perServing = perServing,
            perCookedGram = perCookedGram,
            gramsPerServingCooked = gramsPerServingCooked,
            warnings = warnings
        )
    }
}

/**
 * AI NOTE — READ BEFORE REFACTORING (2026-02-06)
 *
 * I previously broke this file by guessing repository APIs and snapshot fields.
 * The real RecipeRepository API here is:
 * - getRecipeByFoodId(recipeFoodId)
 * - getIngredients(recipeId)
 *
 * Do NOT add volume logic here until the domain.recipes.FoodNutritionSnapshot actually supports it in this codebase.
 * If I want recipe ingredients to support liquids later:
 * - First update FoodNutritionSnapshot + mapper to include mlPerServingUnit + nutrientsPerMilliliter
 * - Then update computeFromServingsLines to branch grams vs mL (still no density guessing).
 */
