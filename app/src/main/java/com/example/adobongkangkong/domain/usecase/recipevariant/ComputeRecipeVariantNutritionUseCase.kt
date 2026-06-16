package com.example.adobongkangkong.domain.usecase.recipevariant

import com.example.adobongkangkong.domain.nutrition.NutrientMap
import com.example.adobongkangkong.domain.recipes.RecipeNutritionResult
import com.example.adobongkangkong.domain.recipes.RecipeNutritionWarning
import com.example.adobongkangkong.domain.recipes.nutrientsForGrams
import com.example.adobongkangkong.domain.recipes.nutrientsForMilliliters
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import javax.inject.Inject

/**
 * Computes the immutable nutrition basis for one recipe variant batch.
 *
 * This deliberately works from the assembled variant and immutable food nutrition snapshots.
 * It does not convert the variant into RecipeDraft, and it does not guess grams <-> mL density.
 */
class ComputeRecipeVariantNutritionUseCase @Inject constructor(
    private val assembleRecipeVariant: AssembleRecipeVariantUseCase,
    private val snapshotRepository: FoodNutritionSnapshotRepository,
) {

    suspend operator fun invoke(
        variantId: Long,
    ): RecipeNutritionResult {
        val assembled = assembleRecipeVariant(variantId = variantId)

        val warnings = mutableListOf<RecipeNutritionWarning>()

        val foodIds = assembled.finalIngredientLines
            .map { it.food.id }
            .toSet()

        val snapshotsByFoodId = snapshotRepository.getSnapshots(foodIds)

        val totals = assembled.finalIngredientLines.fold(NutrientMap.EMPTY) { acc, line ->
            val foodId = line.food.id
            val snapshot = snapshotsByFoodId[foodId]

            if (snapshot == null) {
                warnings += RecipeNutritionWarning.MissingFood(foodId)
                return@fold acc
            }

            val grams = line.grams?.takeIf { it > 0.0 }
            if (grams != null) {
                if (snapshot.nutrientsPerGram == null) {
                    warnings += RecipeNutritionWarning.MissingNutrientsPerGram(foodId)
                    return@fold acc
                }

                return@fold acc + snapshot.nutrientsForGrams(grams)
            }

            val servings = line.servings?.takeIf { it > 0.0 }

            if (servings == null) {
                warnings += RecipeNutritionWarning.IngredientServingsNonPositive(
                    foodId = foodId,
                    servings = line.servings ?: line.grams ?: 0.0,
                )
                return@fold acc
            }

            val gramsPerServingUnit = snapshot.gramsPerServingUnit
            val mlPerServingUnit = snapshot.mlPerServingUnit

            when {
                gramsPerServingUnit != null && gramsPerServingUnit > 0.0 -> {
                    if (snapshot.nutrientsPerGram == null) {
                        warnings += RecipeNutritionWarning.MissingNutrientsPerGram(foodId)
                        return@fold acc
                    }

                    // Recipe ingredient "servings" are stored as an amount in the ingredient's
                    // serving unit, matching the existing recipe calculator. Do NOT multiply by
                    // food.servingSize here, or ingredients with servingSize > 1 are counted twice.
                    val lineGrams = servings * gramsPerServingUnit
                    acc + snapshot.nutrientsForGrams(lineGrams)
                }

                mlPerServingUnit != null && mlPerServingUnit > 0.0 -> {
                    if (snapshot.nutrientsPerMilliliter == null) {
                        warnings += RecipeNutritionWarning.MissingNutrientsPerMilliliter(foodId)
                        return@fold acc
                    }

                    // Same rule as mass: servings is already the serving-unit amount.
                    val lineMilliliters = servings * mlPerServingUnit
                    acc + snapshot.nutrientsForMilliliters(lineMilliliters)
                }

                else -> {
                    warnings += RecipeNutritionWarning.MissingGramsPerServing(foodId)
                    warnings += RecipeNutritionWarning.MissingMlPerServing(foodId)
                    acc
                }
            }
        }

        val servingsYield =
            assembled.variantServingsYieldOverride ?: assembled.baseServingsYield

        val perServing = when {
            servingsYield == null -> {
                warnings += RecipeNutritionWarning.MissingServingsYield
                null
            }

            servingsYield <= 0.0 -> {
                warnings += RecipeNutritionWarning.InvalidServingsYield(servingsYield)
                null
            }

            else -> totals.scaledBy(1.0 / servingsYield)
        }

        val totalYieldGrams =
            assembled.variantTotalYieldGramsOverride ?: assembled.baseTotalYieldGrams

        val perCookedGram = when {
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

        val gramsPerServingCooked =
            if (servingsYield != null &&
                servingsYield > 0.0 &&
                totalYieldGrams != null &&
                totalYieldGrams > 0.0
            ) {
                totalYieldGrams / servingsYield
            } else {
                null
            }

        return RecipeNutritionResult(
            totals = totals,
            perServing = perServing,
            perCookedGram = perCookedGram,
            gramsPerServingCooked = gramsPerServingCooked,
            warnings = warnings,
        )
    }
}
