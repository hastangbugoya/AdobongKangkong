package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import javax.inject.Inject

/**
 * Deletes a planned meal ONLY if it has zero planned items.
 *
 * This is functional cleanup for "stuck empty meals".
 * Safe by design: meals with items are never deleted.
 */
class RemoveEmptyPlannedMealUseCase @Inject constructor(
    private val meals: PlannedMealRepository,
    private val items: PlannedItemRepository
) {
    suspend operator fun invoke(mealId: Long) {
        if (mealId <= 0L) return

        // Ensure the meal still exists
        val meal = meals.getById(mealId) ?: return

        // Only delete if there are no planned items
        val children = items.getItemsForMeal(mealId)
        if (children.isEmpty()) {
            meals.delete(meal)
        }
    }
}
