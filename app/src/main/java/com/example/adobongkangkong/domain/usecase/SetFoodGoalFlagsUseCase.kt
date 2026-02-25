package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.repository.FoodGoalFlagsRepository
import javax.inject.Inject

/**
 * Sets goal-related flags for a food (eat-more, limit, favorite).
 *
 * ## Purpose
 * Provide a domain-level write operation for updating user-defined goal flags
 * associated with a specific food.
 *
 * ## Rationale (why this use case exists)
 * Food goal flags are user preference metadata that influence:
 * - Planner highlighting
 * - Food picker emphasis
 * - Goal-aligned suggestions
 * - Favorite shortcuts
 *
 * Centralizing flag updates in a use case:
 * - Preserves Clean Architecture boundaries (UI → Domain → Repository),
 * - Prevents UI from directly mutating repository state,
 * - Allows future validation or rule enforcement to be introduced without
 *   modifying UI call sites.
 *
 * ## Behavior
 * - Delegates to [FoodGoalFlagsRepository.setFlags].
 * - Replaces the current flag state for the given food with the provided values.
 * - Does not merge or toggle values; the provided booleans are authoritative.
 *
 * ## Parameters
 * - `foodId`: Identifier of the food whose flags should be updated.
 * - `eatMore`: Whether the food is marked as aligned with “eat more” goals.
 * - `limit`: Whether the food is marked as something to limit.
 * - `favorite`: Whether the food is marked as a favorite.
 *
 * ## Return
 * No return value. This is a suspend write operation.
 *
 * ## Edge cases
 * - If the food does not exist, repository behavior defines the outcome
 *   (e.g., no-op, error, or insert flags row).
 * - Conflicting flags (e.g., eatMore = true and limit = true) are not
 *   validated here; domain rules may allow or restrict this elsewhere.
 *
 * ## Pitfalls / gotchas
 * - This use case does not enforce mutual exclusivity between flags.
 *   If future business rules require exclusivity, they must be added here.
 * - Avoid introducing UI logic (such as toggling behavior) into this layer.
 *
 * ## Architectural rules
 * - Pure domain write operation.
 * - No UI state mutation.
 * - No direct database access.
 * - Repository is the only persistence boundary.
 */
class SetFoodGoalFlagsUseCase @Inject constructor(
    private val repo: FoodGoalFlagsRepository
) {
    suspend operator fun invoke(foodId: Long, eatMore: Boolean, limit: Boolean, favorite: Boolean) {
        repo.setFlags(foodId, eatMore, limit, favorite)
    }
}

/**
 * FUTURE-AI / MAINTENANCE KDoc (Do not remove)
 *
 * ## Invariants (must not change)
 * - This use case must remain a thin domain-layer delegation to
 *   [FoodGoalFlagsRepository.setFlags].
 * - Provided boolean values are authoritative; do not auto-toggle or merge.
 * - Must remain a suspend write operation.
 *
 * ## Do not refactor notes
 * - Do not move repository access into UI.
 * - Do not introduce implicit toggle logic here unless formalized as a domain rule.
 * - Do not silently resolve conflicting flags without explicit business requirements.
 *
 * ## Architectural boundaries
 * - UI triggers this use case.
 * - Repository defines persistence semantics.
 * - Domain layer must not depend on DB/Room APIs.
 *
 * ## Migration notes (KMP / time APIs)
 * - No time or platform-specific APIs used.
 * - Safe to migrate to shared domain as long as repository abstraction remains intact.
 *
 * ## Performance considerations
 * - Single-row write operation; negligible overhead.
 * - Avoid batching logic here unless repository contract changes to support it.
 */