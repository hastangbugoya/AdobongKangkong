package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import javax.inject.Inject

/**
 * Removes a planned item while returning a full snapshot for Undo restoration.
 *
 * =============================================================================
 * PURPOSE
 * =============================================================================
 *
 * This use case deletes a PlannedItemEntity and returns the exact entity snapshot
 * that existed prior to deletion.
 *
 * This enables safe, lossless Undo functionality in the UI without requiring
 * reconstruction of the item from partial information.
 *
 * The returned snapshot can be re-inserted later to fully restore the original state.
 *
 *
 * =============================================================================
 * WHY THIS EXISTS
 * =============================================================================
 *
 * Direct deletion without preserving state makes Undo unreliable or impossible.
 *
 * Planned items contain critical fields:
 *
 * - foodId / recipeId / batchId reference
 * - grams or servings quantity
 * - sortOrder
 * - meal association
 *
 * Reconstructing these after deletion is error-prone and may lead to:
 *
 * - wrong sort order
 * - incorrect quantity
 * - lost recipe linkage
 *
 * This use case ensures Undo is exact, not approximate.
 *
 *
 * =============================================================================
 * ARCHITECTURAL ROLE
 * =============================================================================
 *
 * Domain layer operation that supports:
 *
 * - Planner UI Undo interactions
 * - Swipe-to-delete with Snackbar Undo
 * - Multi-step editing workflows
 *
 *
 * Repository responsibilities:
 *
 * PlannedItemRepository
 *
 * - getById → retrieve snapshot
 * - deleteById → remove item
 *
 *
 * =============================================================================
 * SAFETY GUARANTEES
 * =============================================================================
 *
 * Snapshot integrity:
 *
 * The entity is retrieved BEFORE deletion and returned unchanged.
 *
 *
 * Explicit failure on invalid state:
 *
 * If the item does not exist, throws IllegalStateException.
 *
 * This prevents silent failures or restoring invalid data.
 *
 *
 * No sortOrder mutation:
 *
 * This use case does NOT repack sortOrder values.
 *
 * This preserves deterministic ordering and prevents unintended UI shifts.
 *
 *
 * =============================================================================
 * EDGE CASES HANDLED
 * =============================================================================
 *
 * Invalid ID (≤ 0):
 *
 * Immediately rejected via require().
 *
 *
 * Missing item:
 *
 * Throws IllegalStateException.
 *
 *
 * Concurrent deletion:
 *
 * If another process deletes the item first, getById returns null and exception is thrown.
 *
 *
 * =============================================================================
 * WHAT THIS USE CASE DOES NOT DO
 * =============================================================================
 *
 * It does NOT restore the item.
 *
 * Restoration must be performed by a separate restore use case or repository call.
 *
 *
 * It does NOT modify the parent meal.
 *
 * Meal cleanup (removing empty meals) is handled by RemoveEmptyPlannedMealUseCase.
 *
 *
 * =============================================================================
 * PERFORMANCE
 * =============================================================================
 *
 * O(1) single item lookup and delete.
 *
 * Safe to call frequently.
 */
class RemovePlannedItemForUndoUseCase @Inject constructor(
    private val items: PlannedItemRepository
) {

    /**
     * Removes a planned item and returns the exact entity snapshot.
     *
     * =============================================================================
     * PARAMETERS
     * =============================================================================
     *
     * itemId
     *
     * Primary key of the planned item to delete.
     *
     *
     * =============================================================================
     * RETURNS
     * =============================================================================
     *
     * PlannedItemEntity snapshot representing the deleted item.
     *
     * This snapshot can later be re-inserted to restore the item.
     *
     *
     * =============================================================================
     * THROWS
     * =============================================================================
     *
     * IllegalArgumentException
     *
     * If itemId <= 0.
     *
     *
     * IllegalStateException
     *
     * If item does not exist.
     *
     *
     * =============================================================================
     * INVARIANTS
     * =============================================================================
     *
     * Returned entity must match exactly the row that existed before deletion.
     *
     * No fields are modified.
     *
     *
     * =============================================================================
     * TRANSACTION NOTE
     * =============================================================================
     *
     * This use case itself is not transactional.
     *
     * If atomic delete+restore semantics are required,
     * caller may wrap operations in a Room transaction.
     */
    suspend operator fun invoke(itemId: Long): PlannedItemEntity {
        require(itemId > 0) { "itemId must be > 0" }

        val entity = items.getById(itemId)
            ?: throw IllegalStateException("Planned item not found for id=$itemId")

        items.deleteById(itemId)

        return entity
    }
}

/**
 * =============================================================================
 * FOR FUTURE AI / FUTURE DEVELOPER — Undo system invariants and evolution notes
 * =============================================================================
 *
 * This use case is part of the planner Undo architecture.
 *
 *
 * Undo model design
 *
 * Delete → return snapshot → UI stores snapshot temporarily → optional restore later.
 *
 *
 * Why snapshot is returned instead of restoring internally
 *
 * Separation of concerns:
 *
 * Domain layer performs deletion.
 *
 * UI layer decides whether to restore based on user interaction.
 *
 *
 * This prevents domain layer from:
 *
 * - storing temporary state
 * - managing timers
 * - knowing UI timing behavior
 *
 *
 * Required complementary restore use case (recommended)
 *
 * RestorePlannedItemUseCase(snapshot: PlannedItemEntity)
 *
 * which performs:
 *
 * items.insert(snapshot)
 *
 *
 * Critical invariants — DO NOT BREAK
 *
 * Snapshot must be retrieved BEFORE deletion.
 *
 * Snapshot must NOT be modified.
 *
 * deleteById must occur after snapshot retrieval.
 *
 *
 * Potential future improvements
 *
 * Add bulk undo support.
 *
 * Add transaction wrapper for multi-item delete+undo workflows.
 *
 * Add automatic meal cleanup integration.
 *
 *
 * Migration safety
 *
 * If PlannedItemEntity schema changes,
 * ensure snapshot still contains all required restore fields.
 *
 *
 * Treat this use case as the canonical deletion entry point for planner items.
 */