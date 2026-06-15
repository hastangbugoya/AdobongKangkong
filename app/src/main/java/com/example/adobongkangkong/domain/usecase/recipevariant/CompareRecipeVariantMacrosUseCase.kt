package com.example.adobongkangkong.domain.usecase.recipevariant

import com.example.adobongkangkong.data.local.db.entity.FoodEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeVariantIngredientChangeEntity
import com.example.adobongkangkong.domain.model.RecipeVariantMacroComparison
import com.example.adobongkangkong.domain.model.dividedBy
import com.example.adobongkangkong.domain.model.minus
import com.example.adobongkangkong.domain.model.toGrams
import com.example.adobongkangkong.domain.repository.FoodNutrientRepository
import javax.inject.Inject

class CompareRecipeVariantMacrosUseCase @Inject constructor(
    private val assembleRecipeVariant: AssembleRecipeVariantUseCase,
    private val foodNutrientRepository: FoodNutrientRepository,
) {

    suspend operator fun invoke(
        variantId: Long,
        draftChanges: List<RecipeVariantIngredientChangeEntity>? = null,
        draftServingsYieldOverride: Double? = null,
    ): RecipeVariantMacroComparison {
        val assembled = assembleRecipeVariant(
            variantId = variantId,
            draftChanges = draftChanges,
        )
        val warnings = assembled.warnings.toMutableList()

        val recipeInputs = buildList {
            assembled.finalIngredientLines
                .filter { it.baseRecipeIngredientId != null }
                .forEach { line ->
                    val resolvedServings = resolveServings(
                        food = line.food,
                        servings = line.originalServings,
                        grams = line.originalGrams,
                    )

                    if (resolvedServings != null && resolvedServings > 0.0) {
                        add(line.food.id to resolvedServings)
                    } else {
                        warnings += "Could not calculate base recipe macros for ${line.food.name}."
                    }
                }

            assembled.removedIngredientLines.forEach { line ->
                val food = line.food

                if (food == null) {
                    warnings += "Could not calculate base recipe macros for a removed missing food."
                    return@forEach
                }

                val resolvedServings = resolveServings(
                    food = food,
                    servings = line.servings,
                    grams = line.grams,
                )

                if (resolvedServings != null && resolvedServings > 0.0) {
                    add(food.id to resolvedServings)
                } else {
                    warnings += "Could not calculate base recipe macros for removed ${food.name}."
                }
            }
        }

        val variantInputs = buildList {
            assembled.finalIngredientLines.forEach { line ->
                val resolvedServings = resolveServings(
                    food = line.food,
                    servings = line.servings,
                    grams = line.grams,
                )

                if (resolvedServings != null && resolvedServings > 0.0) {
                    add(line.food.id to resolvedServings)
                } else {
                    warnings += "Could not calculate variant macros for ${line.food.name}."
                }
            }
        }

        val recipePreview = foodNutrientRepository.computeRecipeMacroPreview(recipeInputs)
        val variantPreview = foodNutrientRepository.computeRecipeMacroPreview(variantInputs)

        val baseServingsYield = assembled.baseServingsYield
        val variantServingsYieldOverride =
            draftServingsYieldOverride ?: assembled.variantServingsYieldOverride
        val variantServingsYield = variantServingsYieldOverride ?: baseServingsYield

        val recipePerServing = recipePreview.dividedBy(baseServingsYield)
        val variantPerServing = variantPreview.dividedBy(variantServingsYield)

        if (baseServingsYield == null || baseServingsYield <= 0.0) {
            warnings += "Could not calculate recipe per-serving macros because recipe servings yield is missing."
        }

        if (variantServingsYield == null || variantServingsYield <= 0.0) {
            warnings += "Could not calculate variant per-serving macros because variant servings yield is missing."
        }

        return RecipeVariantMacroComparison(
            recipe = recipePreview,
            variant = variantPreview,
            delta = variantPreview.minus(recipePreview),
            recipePerServing = recipePerServing,
            variantPerServing = variantPerServing,
            perServingDelta = variantPerServing.minus(recipePerServing),
            baseServingsYield = baseServingsYield,
            variantServingsYield = variantServingsYield,
            variantServingsYieldOverride = variantServingsYieldOverride,
            warnings = warnings,
        )
    }

    private fun resolveServings(
        food: FoodEntity,
        servings: Double?,
        grams: Double?,
    ): Double? {
        servings
            ?.takeIf { it > 0.0 }
            ?.let { return it }

        val gramsValue = grams?.takeIf { it > 0.0 } ?: return null
        val gramsPerServing = gramsPerCurrentServingResolved(food) ?: return null

        if (gramsPerServing <= 0.0) return null

        return gramsValue / gramsPerServing
    }

    /**
     * Mirrors the Recipe Builder rule:
     * - direct mass units use servingUnit.toGrams(servingSize)
     * - custom units use servingSize * gramsPerServingUnit
     * - no density guessing
     */
    private fun gramsPerCurrentServingResolved(
        food: FoodEntity,
    ): Double? {
        val directMass = food.servingUnit.toGrams(food.servingSize)

        if (directMass != null && directMass > 0.0) {
            return directMass
        }

        val gramsPerOneUnit = food.gramsPerServingUnit

        if (
            gramsPerOneUnit != null &&
            gramsPerOneUnit > 0.0 &&
            food.servingSize > 0.0
        ) {
            return food.servingSize * gramsPerOneUnit
        }

        return null
    }
}
