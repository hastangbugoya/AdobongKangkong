package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.repository.FoodRepository
import javax.inject.Inject

/**
 * Soft-deletes a food so it is no longer selectable or visible in normal flows,
 * while preserving historical integrity.
 *
 * ## Purpose
 * Mark a food as deleted (logically) without physically removing it from persistence.
 *
 * ## Rationale (why this use case exists)
 * In a nutrition-tracking system:
 * - Logged entries store immutable nutrient snapshots.
 * - Historical logs must remain correct even if the original food changes or is removed.
 *
 * A **soft delete**:
 * - Hides the food from search, pickers, and planner flows,
 * - Prevents future usage,
 * - Preserves referential integrity for:
 *     - Existing logs
 *     - Snapshot history
 *     - Past planner items
 *
 * A hard delete would:
 * - Risk breaking foreign keys,
 * - Create orphaned historical references,
 * - Undermine the “immutable snapshot” model.
 *
 * Therefore, soft delete is the default and only supported deletion mechanism
 * in normal app behavior.
 *
 * ## Behavior
 * - Looks up the food by `foodId`.
 * - If not found → returns [Result.NotFound].
 * - If found → calls `foodRepository.softDeleteFood(id)`.
 * - Returns [Result.Success] on completion.
 *
 * ## Parameters
 * - `foodId`: Identifier of the food to soft-delete.
 *
 * ## Return
 * - [Result.Success] if the food was found and soft-deleted.
 * - [Result.NotFound] if the food does not exist.
 *
 * ## Edge cases
 * - Repeated soft-delete calls on the same food are repository-defined
 *   (should be idempotent).
 * - Does not cascade-delete logs, nutrients, or planner entries.
 *
 * ## Pitfalls / gotchas
 * - This does NOT permanently remove the food from storage.
 * - UI and repository queries must filter out soft-deleted foods where appropriate.
 * - Historical logs must never be altered during deletion.
 *
 * ## Architectural rules
 * - Domain-layer enforcement of deletion semantics.
 * - Repository defines the soft-delete implementation (e.g., `isDeleted` flag).
 * - No direct DB or Room references in this layer.
 */
class SoftDeleteFoodUseCase @Inject constructor(
    private val foodRepository: FoodRepository
) {
    sealed class Result {
        data object Success : Result()
        data class NotFound(val foodId: Long) : Result()
    }

    suspend operator fun invoke(foodId: Long): Result {
        val existing = foodRepository.getById(foodId) ?: return Result.NotFound(foodId)
        foodRepository.softDeleteFood(existing.id)
        return Result.Success
    }
}

/**
 * FUTURE-AI / MAINTENANCE KDoc (Do not remove)
 *
 * ## Invariants (must not change)
 * - Deletion must remain soft (logical), not physical.
 * - Historical logs and snapshots must remain untouched.
 * - This use case must not cascade-delete related entities.
 *
 * ## Do not refactor notes
 * - Do not convert to hard delete without a full migration strategy.
 * - Do not auto-delete associated nutrient rows or logs.
 * - Do not bypass the repository’s soft-delete API.
 *
 * ## Architectural boundaries
 * - Domain defines deletion semantics.
 * - Repository implements how soft-delete is stored (e.g., `isDeleted` column).
 * - UI must treat soft-deleted foods as unavailable for selection.
 *
 * ## Migration notes (KMP / time APIs)
 * - No time APIs involved.
 * - If introducing a `deletedAt` timestamp in the future, ensure backward compatibility
 *   and maintain snapshot invariants.
 *
 * ## Performance considerations
 * - Single-row update; minimal cost.
 * - Ensure repository queries are indexed to efficiently exclude soft-deleted rows.
 */