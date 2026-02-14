package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.FoodHardDeleteBlockers
import com.example.adobongkangkong.domain.repository.FoodRepository
import javax.inject.Inject

class HardDeleteFoodIfUnusedUseCase @Inject constructor(
    private val foodRepository: FoodRepository
) {
    sealed class Result {
        data object Success : Result()

        data class Blocked(
            val foodId: Long,
            val reasons: List<String>,
            val blockers: FoodHardDeleteBlockers
        ) : Result()

        data class NotFound(val foodId: Long) : Result()
    }

    suspend operator fun invoke(foodId: Long): Result {
        val existing = foodRepository.getById(foodId) ?: return Result.NotFound(foodId)

        val blockers = foodRepository.getFoodHardDeleteBlockers(existing.id)
        if (blockers.isBlocked) {
            val reasons = buildList {
                if (blockers.isRecipeFood) add("Food is a recipe (hard delete requires deleting RecipeEntity too).")
                if (blockers.logsUsingStableId > 0) add("Referenced by logs: ${blockers.logsUsingStableId}")
                if (blockers.plannedItemsUsingFoodId > 0) add("Referenced by planned items: ${blockers.plannedItemsUsingFoodId}")
                if (blockers.recipeIngredientsUsingFoodId > 0) add("Used as ingredient in recipes: ${blockers.recipeIngredientsUsingFoodId}")
                if (blockers.recipeBatchesUsingBatchFoodId > 0) add("Used by recipe batches (batchFoodId): ${blockers.recipeBatchesUsingBatchFoodId}")
            }

            return Result.Blocked(
                foodId = existing.id,
                reasons = reasons,
                blockers = blockers
            )
        }

        foodRepository.hardDeleteFood(existing.id)
        return Result.Success
    }
}
