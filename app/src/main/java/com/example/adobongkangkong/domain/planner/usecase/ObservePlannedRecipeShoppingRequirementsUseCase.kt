package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.repository.FoodLookupRepository
import com.example.adobongkangkong.domain.repository.PlannedItemsRangeRepository
import com.example.adobongkangkong.domain.repository.RecipeRepository
import com.example.adobongkangkong.domain.shopping.usecase.ComputeRecipeShoppingRequirementsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import java.time.LocalDate
import javax.inject.Inject

/**
 * Observes recipe-scoped shopping requirements across a forward-looking window.
 *
 * This preserves recipe ownership so the shopping feature can render:
 * - Totalled: one group per recipe across the next N days
 * - Not totalled: one group per recipe occurrence
 *
 * Important:
 * - Ingredients remain attached to their parent recipe.
 * - Ingredients are NOT merged across recipes.
 * - Duplicate ingredients across recipes are only marked.
 * - Direct FOOD shopping remains handled by the existing food-based shopping use cases.
 *
 * IMPORTANT COMPATIBILITY NOTE:
 * - Some planner rows may arrive as PlannedItemSource.FOOD even when the refId is actually a
 *   recipe-backed food entry.
 * - This use case therefore treats a row as recipe-owned if either:
 *   1) row.type == RECIPE
 *   2) RecipeRepository.getRecipeByFoodId(row.refId) resolves successfully
 */
class ObservePlannedRecipeShoppingRequirementsUseCase @Inject constructor(
    private val plannedItemsRangeRepository: PlannedItemsRangeRepository,
    private val foodLookupRepository: FoodLookupRepository,
    private val recipeRepository: RecipeRepository,
    private val computeRecipeShoppingRequirements: ComputeRecipeShoppingRequirementsUseCase
) {

    operator fun invoke(
        startDate: LocalDate,
        days: Int
    ): Flow<Result> {
        require(days > 0) { "days must be > 0" }

        val startIso = startDate.toString()
        val endIso = startDate.plusDays(days.toLong() - 1).toString()

        return plannedItemsRangeRepository
            .observePlannedItemsInRange(startIso, endIso)
            .mapLatest { rows ->
                val candidateRows = ArrayList<CandidateRecipeRow>()

                rows.forEachIndexed { index, row ->
                    val plannedServings = row.servings?.takeIf { it > 0.0 } ?: return@forEachIndexed

                    if (row.type == PlannedItemSource.RECIPE_BATCH) {
                        return@forEachIndexed
                    }

                    val header = recipeRepository.getRecipeByFoodId(row.refId) ?: run {
                        if (row.type == PlannedItemSource.RECIPE) {
                            // Explicit RECIPE row but no header found; skip just like the
                            // current domain behavior for incomplete recipe references.
                        }
                        return@forEachIndexed
                    }

                    val servingsYield = header.servingsYield.takeIf { it > 0.0 } ?: return@forEachIndexed

                    candidateRows += CandidateRecipeRow(
                        index = index,
                        dateIso = row.dateIso,
                        recipeFoodId = row.refId,
                        plannedServings = plannedServings,
                        recipeId = header.recipeId,
                        servingsYield = servingsYield
                    )
                }

                if (candidateRows.isEmpty()) {
                    return@mapLatest Result(
                        totalled = emptyList(),
                        notTotalled = emptyList(),
                        issues = emptyList()
                    )
                }

                val recipeFoodIds = candidateRows.map { it.recipeFoodId }.distinct()
                val recipeNamesByFoodId = foodLookupRepository.getFoodNamesByIds(recipeFoodIds)

                val definitionInputs = LinkedHashMap<Long, DefinitionInput>()
                val demandEntries = ArrayList<ComputeRecipeShoppingRequirementsUseCase.RecipeDemandEntry>()

                candidateRows.forEach { row ->
                    val ingredients = recipeRepository.getIngredients(row.recipeId)
                    if (ingredients.isEmpty()) return@forEach

                    definitionInputs.putIfAbsent(
                        row.recipeFoodId,
                        DefinitionInput(
                            recipeFoodId = row.recipeFoodId,
                            servingsYield = row.servingsYield,
                            ingredients = ingredients.mapNotNull { ing ->
                                val amountPerBatch = ing.ingredientServings ?: ing.ingredientGrams
                                val unit = when {
                                    ing.ingredientServings != null && ing.ingredientServings > 0.0 -> ServingUnit.PIECE
                                    ing.ingredientGrams != null && ing.ingredientGrams > 0.0 -> ServingUnit.G
                                    else -> null
                                }

                                if (amountPerBatch == null || amountPerBatch <= 0.0 || unit == null) {
                                    return@mapNotNull null
                                }

                                IngredientInput(
                                    foodId = ing.ingredientFoodId,
                                    amountPerBatch = amountPerBatch,
                                    unit = unit
                                )
                            }
                        )
                    )

                    demandEntries += ComputeRecipeShoppingRequirementsUseCase.RecipeDemandEntry(
                        recipeId = row.recipeFoodId,
                        occurrenceKey = "${row.dateIso}#${row.index}",
                        requiredYield = row.plannedServings,
                        // This use case currently operates in recipe-serving space.
                        // The compute use case only uses basis as an equality guard, so we keep it
                        // stable and identical across both demand + definition inputs.
                        basis = BasisType.PER_100G
                    )
                }

                if (demandEntries.isEmpty() || definitionInputs.isEmpty()) {
                    return@mapLatest Result(
                        totalled = emptyList(),
                        notTotalled = emptyList(),
                        issues = emptyList()
                    )
                }

                val ingredientFoodIds = definitionInputs.values
                    .flatMap { input -> input.ingredients.map { it.foodId } }
                    .distinct()

                val ingredientNamesByFoodId = foodLookupRepository.getFoodNamesByIds(ingredientFoodIds)

                val recipeDefinitions = definitionInputs.values.map { input ->
                    ComputeRecipeShoppingRequirementsUseCase.RecipeDefinition(
                        recipeId = input.recipeFoodId,
                        recipeName = recipeNamesByFoodId[input.recipeFoodId] ?: "Recipe #${input.recipeFoodId}",
                        batchYield = input.servingsYield,
                        basis = BasisType.PER_100G,
                        ingredients = input.ingredients.map { ing ->
                            ComputeRecipeShoppingRequirementsUseCase.RecipeIngredientDefinition(
                                foodId = ing.foodId,
                                foodName = ingredientNamesByFoodId[ing.foodId] ?: "Food #${ing.foodId}",
                                amountPerBatch = ing.amountPerBatch,
                                unit = ing.unit
                            )
                        }
                    )
                }

                val computed = computeRecipeShoppingRequirements(
                    demandEntries = demandEntries,
                    recipeDefinitions = recipeDefinitions
                )

                Result(
                    totalled = computed.totalled.map { total ->
                        RecipeTotalRequirement(
                            recipeFoodId = total.recipeId,
                            recipeName = total.recipeName,
                            totalRequiredServings = total.totalRequiredYield,
                            batchesRequired = total.batchesRequired,
                            ingredients = total.ingredients.map { ingredient ->
                                RecipeIngredientRequirement(
                                    foodId = ingredient.foodId,
                                    foodName = ingredient.foodName,
                                    amountRequired = ingredient.amountRequired,
                                    unit = ingredient.unit,
                                    isDuplicateAcrossRecipes = ingredient.isDuplicateAcrossRecipes
                                )
                            }
                        )
                    },
                    notTotalled = computed.notTotalled.map { occurrence ->
                        val dateText = occurrence.occurrenceKey.substringBefore('#')
                        RecipeOccurrenceRequirement(
                            recipeFoodId = occurrence.recipeId,
                            recipeName = occurrence.recipeName,
                            dateIso = dateText,
                            requiredServings = occurrence.requiredYield,
                            batchesRequired = occurrence.batchesRequired,
                            ingredients = occurrence.ingredients.map { ingredient ->
                                RecipeIngredientRequirement(
                                    foodId = ingredient.foodId,
                                    foodName = ingredient.foodName,
                                    amountRequired = ingredient.amountRequired,
                                    unit = ingredient.unit,
                                    isDuplicateAcrossRecipes = ingredient.isDuplicateAcrossRecipes
                                )
                            }
                        )
                    },
                    issues = computed.issues
                )
            }
    }

    private data class CandidateRecipeRow(
        val index: Int,
        val dateIso: String,
        val recipeFoodId: Long,
        val plannedServings: Double,
        val recipeId: Long,
        val servingsYield: Double
    )

    private data class DefinitionInput(
        val recipeFoodId: Long,
        val servingsYield: Double,
        val ingredients: List<IngredientInput>
    )

    private data class IngredientInput(
        val foodId: Long,
        val amountPerBatch: Double,
        val unit: ServingUnit
    )

    data class Result(
        val totalled: List<RecipeTotalRequirement>,
        val notTotalled: List<RecipeOccurrenceRequirement>,
        val issues: List<ComputeRecipeShoppingRequirementsUseCase.Issue>
    )

    data class RecipeTotalRequirement(
        val recipeFoodId: Long,
        val recipeName: String,
        val totalRequiredServings: Double,
        val batchesRequired: Double,
        val ingredients: List<RecipeIngredientRequirement>
    )

    data class RecipeOccurrenceRequirement(
        val recipeFoodId: Long,
        val recipeName: String,
        val dateIso: String,
        val requiredServings: Double,
        val batchesRequired: Double,
        val ingredients: List<RecipeIngredientRequirement>
    )

    data class RecipeIngredientRequirement(
        val foodId: Long,
        val foodName: String,
        val amountRequired: Double,
        val unit: ServingUnit,
        val isDuplicateAcrossRecipes: Boolean
    )
}