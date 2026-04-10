package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.PlannedOccurrenceStatus
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import javax.inject.Inject

/**
 * Removes an empty planned meal occurrence safely.
 *
 * One-off meals:
 * - hard delete
 *
 * Series-backed occurrences:
 * - DO NOT hard delete
 * - mark as CANCELLED so recurrence expansion treats the occurrence as intentionally removed
 *   and does not recreate it later
 *
 * This preserves user intent:
 * “I removed this specific generated occurrence.”
 */
class RemoveEmptyPlannedMealUseCase @Inject constructor(
    private val meals: PlannedMealRepository,
    private val items: PlannedItemRepository
) {

    suspend operator fun invoke(mealId: Long) {
        if (mealId <= 0L) return

        val meal = meals.getById(mealId) ?: return

        val children = items.getItemsForMeal(mealId)
        if (children.isNotEmpty()) return

        if (meal.seriesId != null) {
            // Recurring occurrence tombstone:
            // keep the row so recurrence materialization can see that this occurrence
            // already exists and was intentionally removed by the user.
            if (meal.status != PlannedOccurrenceStatus.CANCELLED.name) {
                meals.update(
                    meal.copy(
                        status = PlannedOccurrenceStatus.CANCELLED.name
                    )
                )
            }
        } else {
            // One-off meal: safe to remove entirely.
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
 * Recurring-series rule (IMPORTANT)
 *
 * If an empty meal belongs to a series (seriesId != null), do NOT hard delete it.
 * Mark it CANCELLED instead.
 *
 * Why:
 * - hard delete makes the recurrence materializer think the occurrence is missing
 * - next horizon ensure can recreate it
 * - CANCELLED acts as a tombstone for “user intentionally removed this occurrence”
 *
 *
 * One-off meal rule
 *
 * If seriesId == null and the meal is empty, hard delete is correct.
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
 * Empty recurring occurrences should be tombstoned safely,
 * but NEVER hard-deleted once user intent is “remove this generated occurrence”.
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