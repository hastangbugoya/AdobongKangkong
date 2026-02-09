package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import javax.inject.Inject

/**
 * Restores (undoes) a previously removed planned item by re-inserting
 * the captured entity snapshot.
 *
 * Important:
 * - Re-insert will get a NEW id (we reset id=0).
 * - MealId/type/refId/grams/servings/sortOrder are preserved.
 *
 * Safety:
 * - If the parent meal no longer exists (e.g., the empty meal was deleted),
 *   we refuse to restore to avoid FOREIGN KEY crashes.
 */
class RestorePlannedItemUseCase @Inject constructor(
    private val meals: PlannedMealRepository,
    private val items: PlannedItemRepository
) {
    suspend operator fun invoke(snapshot: PlannedItemEntity): Long {
        val mealExists = meals.getById(snapshot.mealId) != null
        if (!mealExists) {
            // Throw a clear message; VM can surface it as a snackbar/toast/header error.
            throw IllegalStateException("Can’t undo: the meal was removed.")
        }

        // Room @Insert expects a new PK; ensure we don't collide with the deleted id.
        val toInsert = snapshot.copy(id = 0L)
        return items.insert(toInsert)
    }
}

