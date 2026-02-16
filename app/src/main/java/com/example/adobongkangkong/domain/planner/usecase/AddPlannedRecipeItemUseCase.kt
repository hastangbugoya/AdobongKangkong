package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import javax.inject.Inject

class AddPlannedRecipeItemUseCase @Inject constructor(
    private val items: PlannedItemRepository
) {
    suspend operator fun invoke(
        mealId: Long,
        recipeFoodId: Long,
        plannedServings: Double,
        sortOrder: Int? = null
    ): Long {
        require(mealId > 0) { "mealId must be > 0" }
        require(recipeFoodId > 0) { "recipeFoodId must be > 0" }
        require(plannedServings > 0.0) { "plannedServings must be > 0" }

        val finalSortOrder = sortOrder ?: Int.MAX_VALUE

        val entity = PlannedItemEntity(
            mealId = mealId,
            type = PlannedItemSource.RECIPE,
            refId = recipeFoodId,          // ✅ IMPORTANT: matches expandRecipe()
            grams = null,
            servings = plannedServings,
            sortOrder = finalSortOrder
        )

        return items.insert(entity)
    }
}