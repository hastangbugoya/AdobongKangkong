package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.planner.model.PlannedItem
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.planner.model.QuickAddPlannedItemCandidate
import com.example.adobongkangkong.domain.repository.RecipeBatchLookupRepository
import com.example.adobongkangkong.domain.repository.RecipeRepository
import javax.inject.Inject

/**
 * Resolves a planner item into the exact Quick Add candidate needed to prefill logging.
 *
 * Typed planner refs:
 * - FOOD -> planned_items.refId = foods.id
 * - RECIPE -> planned_items.refId = recipes.id
 * - RECIPE_BATCH -> planned_items.refId = recipe_batches.id
 *
 * Quick Add needs:
 * - FOOD -> direct foodId
 * - RECIPE -> recipeId + resolved recipe.foodId
 * - RECIPE_BATCH -> batchId + recipeId + resolved recipe.foodId
 */
class ResolvePlannedItemToQuickAddCandidateUseCase @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val recipeBatchLookupRepository: RecipeBatchLookupRepository,
) {

    suspend operator fun invoke(
        item: PlannedItem,
        slot: MealSlot,
    ): QuickAddPlannedItemCandidate? {
        return when (item.sourceType) {
            PlannedItemSource.FOOD -> {
                QuickAddPlannedItemCandidate(
                    id = item.id,
                    title = item.title.orEmpty(),
                    slot = slot,
                    type = QuickAddPlannedItemCandidate.Type.FOOD,
                    foodId = item.sourceId,
                    recipeId = null,
                    batchId = null,
                    plannedServings = item.qtyServings,
                    plannedGrams = item.qtyGrams,
                )
            }

            PlannedItemSource.RECIPE -> {
                val recipeId = item.sourceId
                val recipeHeader = recipeRepository.getHeaderByRecipeId(recipeId) ?: return null

                QuickAddPlannedItemCandidate(
                    id = item.id,
                    title = item.title.orEmpty(),
                    slot = slot,
                    type = QuickAddPlannedItemCandidate.Type.RECIPE,
                    foodId = recipeHeader.foodId,
                    recipeId = recipeId,
                    batchId = null,
                    plannedServings = item.qtyServings,
                    plannedGrams = item.qtyGrams,
                )
            }

            PlannedItemSource.RECIPE_BATCH -> {
                val batchId = item.sourceId
                val batch = recipeBatchLookupRepository.getBatchById(batchId) ?: return null
                val recipeHeader = recipeRepository.getHeaderByRecipeId(batch.recipeId) ?: return null

                QuickAddPlannedItemCandidate(
                    id = item.id,
                    title = item.title.orEmpty(),
                    slot = slot,
                    type = QuickAddPlannedItemCandidate.Type.RECIPE_BATCH,
                    foodId = recipeHeader.foodId,
                    recipeId = batch.recipeId,
                    batchId = batchId,
                    plannedServings = item.qtyServings,
                    plannedGrams = item.qtyGrams,
                )
            }
        }
    }
}