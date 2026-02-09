package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import javax.inject.Inject

/**
 * Deletes a planned meal ONLY if it has zero planned items.
 *
 * This is functional cleanup: it prevents "stuck empty meals" in the day planner.
 * Cascade delete is fine here (there are no items).
 */
class RemoveEmptyPlannedMealUseCase @Inject constructor(
    private val meals: PlannedMealRepository,
    private val items: PlannedItemRepository
) {
    suspend operator fun invoke(mealId: Long) {
        require(mealId > 0L) { "mealId must be > 0" }

        val meal = meals.getById(mealId) ?: return
        val children = items.getItemsForMeal(mealId)
        if (children.isEmpty()) {
            meals.delete(meal)
        }
    }
}
