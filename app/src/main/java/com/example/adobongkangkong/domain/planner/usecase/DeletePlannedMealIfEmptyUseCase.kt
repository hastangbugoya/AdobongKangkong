package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import javax.inject.Inject

/**
 * Deletes a planned meal ONLY if it has zero items.
 *
 * Purpose: prevent "stuck empty meals" when user creates a meal container
 * but exits without adding any planned items.
 */
class DeletePlannedMealIfEmptyUseCase @Inject constructor(
    private val meals: PlannedMealRepository,
    private val items: PlannedItemRepository
) {
    suspend operator fun invoke(mealId: Long) {
        if (mealId <= 0L) return

        val existing = meals.getById(mealId) ?: return
        val children = items.getItemsForMeal(mealId)
        if (children.isEmpty()) {
            meals.delete(existing) // cascade will handle items (none here anyway)
        }
    }
}
