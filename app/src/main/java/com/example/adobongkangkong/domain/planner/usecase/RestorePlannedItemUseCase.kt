package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import javax.inject.Inject

/**
 * Restores (undoes) a previously removed planned item by re-inserting
 * the captured entity snapshot.
 *
 * Important:
 * - Re-insert will get a NEW id (we reset id=0).
 * - MealId/type/refId/grams/servings/sortOrder are preserved.
 */
class RestorePlannedItemUseCase @Inject constructor(
    private val items: PlannedItemRepository
) {
    suspend operator fun invoke(snapshot: PlannedItemEntity): Long {
        // Room @Insert expects a new PK; ensure we don't collide with the deleted id.
        val toInsert = snapshot.copy(id = 0L)
        return items.insert(toInsert)
    }
}
