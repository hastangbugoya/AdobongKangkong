package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.MealTemplateItemRepository
import com.example.adobongkangkong.domain.repository.RecipeBatchLookupRepository
import com.example.adobongkangkong.domain.repository.RecipeRepository
import javax.inject.Inject

/**
 * Builds lightweight, UI-friendly details for meal template picker rows.
 *
 * ## For developers
 * Responsibilities:
 * - bulk-load template items for the supplied template ids
 * - resolve display names for FOOD / RECIPE / RECIPE_BATCH references
 * - return compact row metadata for picker rendering
 *
 * Design notes:
 * - this is intentionally read-only and best-effort
 * - missing foods / recipes / batches are skipped rather than failing the picker
 * - output is stable by template item ordering `(sortOrder, id)`
 * - the picker uses this to show a short preview without moving business logic into Compose
 */
class BuildMealTemplatePickerDetailsUseCase @Inject constructor(
    private val templateItems: MealTemplateItemRepository,
    private val foods: FoodRepository,
    private val recipeBatches: RecipeBatchLookupRepository,
    private val recipes: RecipeRepository
) {
    suspend operator fun invoke(templateIds: List<Long>): Map<Long, MealTemplatePickerDetails> {
        if (templateIds.isEmpty()) return emptyMap()

        val items = templateItems.getItemsForTemplates(templateIds)
            .sortedWith(compareBy({ it.templateId }, { it.sortOrder }, { it.id }))

        val batchIds = linkedSetOf<Long>()
        val recipeIds = linkedSetOf<Long>()
        val directFoodIds = linkedSetOf<Long>()

        for (item in items) {
            when (item.type) {
                PlannedItemSource.FOOD -> directFoodIds += item.refId
                PlannedItemSource.RECIPE -> recipeIds += item.refId
                PlannedItemSource.RECIPE_BATCH -> batchIds += item.refId
            }
        }

        val batchFoodIdsByBatchId = recipeBatches.getBatchFoodIds(batchIds)
        val recipeFoodIdsByRecipeId = recipes.getFoodIdsByRecipeIds(recipeIds)

        val allFoodIds = linkedSetOf<Long>().apply {
            addAll(directFoodIds)
            addAll(batchFoodIdsByBatchId.values)
            addAll(recipeFoodIdsByRecipeId.values)
        }

        val foodNamesById = buildMap<Long, String> {
            for (foodId in allFoodIds) {
                val name = foods.getById(foodId)?.name?.trim().orEmpty()
                if (name.isNotBlank()) put(foodId, name)
            }
        }

        val grouped = items.groupBy { it.templateId }
        return buildMap {
            for (templateId in templateIds) {
                val templateItems = grouped[templateId].orEmpty()
                val names = templateItems.mapNotNull { item ->
                    val foodId = when (item.type) {
                        PlannedItemSource.FOOD -> item.refId
                        PlannedItemSource.RECIPE -> recipeFoodIdsByRecipeId[item.refId]
                        PlannedItemSource.RECIPE_BATCH -> batchFoodIdsByBatchId[item.refId]
                    } ?: return@mapNotNull null

                    foodNamesById[foodId]
                }

                put(
                    templateId,
                    MealTemplatePickerDetails(
                        itemCount = templateItems.size,
                        previewLine = names.toPreviewLine()
                    )
                )
            }
        }
    }
}

data class MealTemplatePickerDetails(
    val itemCount: Int,
    val previewLine: String?
)

private fun List<String>.toPreviewLine(maxShown: Int = 3): String? {
    if (isEmpty()) return null

    val shown = take(maxShown)
    val remaining = size - shown.size
    val base = shown.joinToString(" • ")
    return if (remaining > 0) "$base +$remaining more" else base
}

/**
 * Bottom KDoc for future AI assistant.
 *
 * This use case exists because the picker now needs more than a name + macro line:
 * - item count
 * - short ingredient preview
 *
 * Keep it small and best-effort.
 * - Do not throw when a referenced food / recipe / batch is missing.
 * - Do not format long prose here.
 * - If richer picker cards are added later, extend this return model instead of teaching
 *   the composable to resolve repository-backed names on recomposition.
 */
