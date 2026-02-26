package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import javax.inject.Inject

/**
 * Deletes a planned meal ONLY if it has zero planned items.
 *
 * =============================================================================
 * WHAT is a Planned Meal
 * =============================================================================
 *
 * A PlannedMeal represents a scheduled meal occurrence on a specific date and slot
 * (breakfast, lunch, dinner, snack, or custom).
 *
 * It serves as a container for PlannedItemEntity rows (foods, recipes, or batches).
 *
 * PlannedMeal lifecycle:
 *
 * created → items added/removed → possibly logged → possibly deleted
 *
 * This use case handles ONLY the deletion phase for meals that have become empty.
 *
 *
 * =============================================================================
 * WHY this use case exists
 * =============================================================================
 *
 * Empty planned meals can occur during normal UI workflows:
 *
 * Examples:
 *
 * • User creates a meal, then removes all items.
 * • User moves items to another meal.
 * • User deletes items one-by-one.
 * • Series occurrence creates a meal but template items are later removed.
 *
 * Without cleanup, these empty meals would:
 *
 * • clutter planner UI
 * • create confusing empty slots
 * • waste DB rows
 * • complicate planner aggregation logic
 *
 * This use case provides SAFE automatic cleanup.
 *
 *
 * =============================================================================
 * SAFETY GUARANTEE (CRITICAL INVARIANT)
 * =============================================================================
 *
 * Meals with items are NEVER deleted.
 *
 * Deletion only occurs if:
 *
 * PlannedItemRepository.getItemsForMeal(mealId) returns empty list
 *
 * This prevents:
 *
 * • accidental data loss
 * • deleting meals still in use
 * • deleting series occurrences with items
 *
 *
 * =============================================================================
 * DESIGN RATIONALE
 * =============================================================================
 *
 * This logic lives in a domain use case (NOT repository, NOT UI) because:
 *
 * • deletion depends on business rules (item count)
 * • not a raw CRUD operation
 * • ensures consistent behavior across all callers
 *
 * This keeps planner integrity centralized.
 *
 *
 * =============================================================================
 * IDENTITY AND IDEMPOTENCY
 * =============================================================================
 *
 * This use case is safe to call repeatedly.
 *
 * If:
 *
 * • meal does not exist → no-op
 * • meal has items → no-op
 * • meal already deleted → no-op
 *
 *
 * =============================================================================
 * WHEN to call this
 * =============================================================================
 *
 * Typical trigger points:
 *
 * • after removing a PlannedItem
 * • after moving items between meals
 * • after clearing a meal
 * • after series modifications
 *
 *
 * =============================================================================
 * EDGE CASES handled
 * =============================================================================
 *
 * mealId <= 0 → ignored
 *
 * meal does not exist → ignored
 *
 * meal has ≥1 items → preserved
 *
 *
 * =============================================================================
 * PERFORMANCE
 * =============================================================================
 *
 * Single meal lookup + single item lookup.
 *
 * O(items per meal), typically very small.
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


/**
 * =============================================================================
 * FOR FUTURE AI / FUTURE DEVELOPER — deletion invariants and evolution notes
 * =============================================================================
 *
 * CRITICAL RULE
 *
 * Never delete a meal that still contains planned items.
 *
 * Planner integrity depends on meals being stable containers.
 *
 *
 * Why this exists as a separate use case
 *
 * Deletion depends on planner business logic, not raw repository access.
 *
 * This prevents accidental destructive operations.
 *
 *
 * Interaction with recurring series
 *
 * Series occurrences create PlannedMeal rows automatically.
 *
 * Empty occurrences should be cleaned up safely,
 * but NEVER delete meals that still contain template-derived items.
 *
 *
 * Possible future improvements
 *
 * Add batch cleanup:
 *
 * removeEmptyMealsForDate(dateIso)
 *
 * Add automatic invocation after:
 *
 * RemovePlannedItemUseCase
 *
 *
 * Possible optimization
 *
 * Could replace item lookup with count query:
 *
 * SELECT COUNT(*) FROM planned_items WHERE mealId = ?
 *
 *
 * Migration safety
 *
 * Do NOT move this logic into repository layer.
 *
 * Repositories should remain persistence-only.
 *
 *
 * Architectural role
 *
 * This is a maintenance / integrity use case,
 * not a user-facing primary workflow.
 */