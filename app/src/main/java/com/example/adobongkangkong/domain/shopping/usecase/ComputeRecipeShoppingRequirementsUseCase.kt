package com.example.adobongkangkong.domain.shopping.usecase

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.domain.model.ServingUnit
import javax.inject.Inject

/**
 * Computes recipe-scoped shopping requirements for a planning horizon.
 *
 * This use case intentionally separates recipe shopping math from:
 * - UI row shaping
 * - display formatting
 * - icon / badge decisions
 * - cross-recipe aggregation into a single grocery total
 *
 * Core rules:
 * 1. Recipes are demand generators, not final shopping rows.
 * 2. For each recipe occurrence, the required finished yield is converted into an exact
 *    fractional batch requirement:
 *      batchesRequired = requiredYield / batchYield
 * 3. Ingredient requirements scale linearly from batch count:
 *      ingredientAmount = amountPerBatch * batchesRequired
 * 4. Ingredients remain scoped to their parent recipe.
 * 5. Cross-recipe duplicate ingredients are marked, not merged.
 * 6. No rounding is performed in the computation layer.
 *
 * Current intended outputs:
 * - Totalled:
 *   per-recipe requirement aggregated across all occurrences in horizon
 * - Not totalled:
 *   per-occurrence requirement
 *
 * This use case is intentionally deterministic and regression-testable so the math can
 * evolve safely without drifting due to UI changes.
 */
class ComputeRecipeShoppingRequirementsUseCase @Inject constructor() {

    operator fun invoke(
        demandEntries: List<RecipeDemandEntry>,
        recipeDefinitions: List<RecipeDefinition>
    ): Result {
        if (demandEntries.isEmpty()) {
            return Result(
                totalled = emptyList(),
                notTotalled = emptyList(),
                issues = emptyList()
            )
        }

        val recipeById = recipeDefinitions.associateBy { it.recipeId }
        val occurrenceResults = mutableListOf<RecipeOccurrenceRequirement>()
        val issues = mutableListOf<Issue>()

        demandEntries
            .sortedWith(compareBy<RecipeDemandEntry>({ it.recipeId }, { it.occurrenceKey }))
            .forEach { demand ->
                val recipe = recipeById[demand.recipeId]
                if (recipe == null) {
                    issues += Issue.MissingRecipeDefinition(
                        recipeId = demand.recipeId,
                        occurrenceKey = demand.occurrenceKey
                    )
                    return@forEach
                }

                if (recipe.batchYield <= 0.0) {
                    issues += Issue.InvalidBatchYield(
                        recipeId = recipe.recipeId,
                        batchYield = recipe.batchYield
                    )
                    return@forEach
                }

                if (recipe.basis != demand.basis) {
                    issues += Issue.BasisMismatch(
                        recipeId = recipe.recipeId,
                        demandBasis = demand.basis,
                        recipeBasis = recipe.basis
                    )
                    return@forEach
                }

                val batchesRequired = demand.requiredYield / recipe.batchYield
                val rawIngredients = recipe.ingredients.map { ingredient ->
                    IngredientRequirement(
                        foodId = ingredient.foodId,
                        foodName = ingredient.foodName,
                        amountRequired = ingredient.amountPerBatch * batchesRequired,
                        unit = ingredient.unit,
                        isDuplicateAcrossRecipes = false
                    )
                }

                occurrenceResults += RecipeOccurrenceRequirement(
                    recipeId = recipe.recipeId,
                    recipeName = recipe.recipeName,
                    occurrenceKey = demand.occurrenceKey,
                    requiredYield = demand.requiredYield,
                    basis = demand.basis,
                    batchesRequired = batchesRequired,
                    ingredients = mergeIngredientsWithinRecipe(rawIngredients)
                )
            }

        val totalledResults = occurrenceResults
            .groupBy { it.recipeId }
            .values
            .map { occurrencesForRecipe ->
                val first = occurrencesForRecipe.first()
                val mergedIngredients = mergeIngredientsWithinRecipe(
                    occurrencesForRecipe.flatMap { it.ingredients }
                )

                RecipeTotalRequirement(
                    recipeId = first.recipeId,
                    recipeName = first.recipeName,
                    totalRequiredYield = occurrencesForRecipe.sumOf { it.requiredYield },
                    basis = first.basis,
                    batchesRequired = occurrencesForRecipe.sumOf { it.batchesRequired },
                    ingredients = mergedIngredients
                )
            }
            .sortedWith(compareBy<RecipeTotalRequirement>({ it.recipeName }, { it.recipeId }))

        val duplicateFoodIdsAcrossRecipes = buildDuplicateFoodIdsAcrossRecipes(
            totalledResults = totalledResults
        )

        val markedTotalled = totalledResults.map { total ->
            total.copy(
                ingredients = total.ingredients.map { ingredient ->
                    ingredient.copy(
                        isDuplicateAcrossRecipes = ingredient.foodId in duplicateFoodIdsAcrossRecipes
                    )
                }
            )
        }

        val markedNotTotalled = occurrenceResults.map { occurrence ->
            occurrence.copy(
                ingredients = occurrence.ingredients.map { ingredient ->
                    ingredient.copy(
                        isDuplicateAcrossRecipes = ingredient.foodId in duplicateFoodIdsAcrossRecipes
                    )
                }
            )
        }

        return Result(
            totalled = markedTotalled,
            notTotalled = markedNotTotalled,
            issues = issues.sortedBy { it.sortKey }
        )
    }

    private fun mergeIngredientsWithinRecipe(
        ingredients: List<IngredientRequirement>
    ): List<IngredientRequirement> {
        return ingredients
            .groupBy { IngredientMergeKey(foodId = it.foodId, unit = it.unit) }
            .map { (_, grouped) ->
                val first = grouped.first()
                first.copy(
                    amountRequired = grouped.sumOf { it.amountRequired }
                )
            }
            .sortedWith(compareBy<IngredientRequirement>({ it.foodName }, { it.foodId }, { it.unit.name }))
    }

    private fun buildDuplicateFoodIdsAcrossRecipes(
        totalledResults: List<RecipeTotalRequirement>
    ): Set<Long> {
        val recipeIdsByFoodId = mutableMapOf<Long, MutableSet<Long>>()

        totalledResults.forEach { total ->
            total.ingredients
                .map { it.foodId }
                .toSet()
                .forEach { foodId ->
                    recipeIdsByFoodId.getOrPut(foodId) { mutableSetOf() }.add(total.recipeId)
                }
        }

        return recipeIdsByFoodId
            .filterValues { it.size > 1 }
            .keys
    }

    private data class IngredientMergeKey(
        val foodId: Long,
        val unit: ServingUnit
    )

    data class RecipeDemandEntry(
        val recipeId: Long,
        val occurrenceKey: String,
        val requiredYield: Double,
        val basis: BasisType
    )

    data class RecipeDefinition(
        val recipeId: Long,
        val recipeName: String,
        val batchYield: Double,
        val basis: BasisType,
        val ingredients: List<RecipeIngredientDefinition>
    )

    data class RecipeIngredientDefinition(
        val foodId: Long,
        val foodName: String,
        val amountPerBatch: Double,
        val unit: ServingUnit
    )

    data class Result(
        val totalled: List<RecipeTotalRequirement>,
        val notTotalled: List<RecipeOccurrenceRequirement>,
        val issues: List<Issue>
    )

    data class RecipeTotalRequirement(
        val recipeId: Long,
        val recipeName: String,
        val totalRequiredYield: Double,
        val basis: BasisType,
        val batchesRequired: Double,
        val ingredients: List<IngredientRequirement>
    )

    data class RecipeOccurrenceRequirement(
        val recipeId: Long,
        val recipeName: String,
        val occurrenceKey: String,
        val requiredYield: Double,
        val basis: BasisType,
        val batchesRequired: Double,
        val ingredients: List<IngredientRequirement>
    )

    data class IngredientRequirement(
        val foodId: Long,
        val foodName: String,
        val amountRequired: Double,
        val unit: ServingUnit,
        val isDuplicateAcrossRecipes: Boolean
    )

    sealed interface Issue {
        val sortKey: String

        data class MissingRecipeDefinition(
            val recipeId: Long,
            val occurrenceKey: String
        ) : Issue {
            override val sortKey: String = "1_${recipeId}_$occurrenceKey"
        }

        data class InvalidBatchYield(
            val recipeId: Long,
            val batchYield: Double
        ) : Issue {
            override val sortKey: String = "2_$recipeId"
        }

        data class BasisMismatch(
            val recipeId: Long,
            val demandBasis: BasisType,
            val recipeBasis: BasisType
        ) : Issue {
            override val sortKey: String = "3_$recipeId"
        }
    }
}

/**
 * FUTURE-YOU / FUTURE-AI ASSISTANT NOTES
 * Timestamp: 2026-03-20
 *
 * Intent:
 * - Keep recipe shopping math isolated and regression-testable.
 * - Do not let UI model needs reshape domain math invariants.
 *
 * Locked behavioral rules:
 * - Ingredients belong to their recipe.
 * - Same ingredient across different recipes is NOT merged here.
 * - Duplicate-across-recipes is informational only.
 * - Exact fractional batches are preserved.
 * - App reports required batches; user decides how many batches to actually make.
 *
 * Current implementation details:
 * - Missing recipe definitions, invalid batch yields, and basis mismatches are reported
 *   as issues and skipped from computed outputs.
 * - Ingredient merging only happens within the same recipe scope and only when both
 *   food identity and unit match.
 * - Duplicate flags are computed from the totalled per-recipe results so one recipe
 *   using the same ingredient multiple times does not falsely count as cross-recipe
 *   duplication.
 *
 * Important guardrails:
 * - No Compose types here.
 * - No formatted strings here.
 * - No painter/icon logic here.
 * - No rounding for display here.
 * - No hidden cross-recipe aggregation here.
 *
 * If later you need:
 * - pantry subtraction
 * - package-size recommendation
 * - round-up batch strategies
 * - recursive sub-recipe flattening
 *
 * implement those as separate layers/use cases unless they are explicitly folded into
 * this contract with new regression tests.
 */