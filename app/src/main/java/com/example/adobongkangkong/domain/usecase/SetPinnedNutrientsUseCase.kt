package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.repository.UserPinnedNutrientRepository
import javax.inject.Inject

/**
 * Sets the user’s pinned nutrient positions (two-slot model).
 *
 * ## Purpose
 * Persist the user’s selected pinned nutrients that should be prominently displayed
 * (e.g., in dashboards, calendar cells, summary cards).
 *
 * ## Rationale (why this use case exists)
 * Pinned nutrients are a user preference feature that:
 * - Allows quick visibility of priority nutrients (e.g., protein, sodium),
 * - Reduces UI configuration coupling,
 * - Ensures pinned-slot constraints are enforced consistently.
 *
 * Centralizing this write operation in the domain layer:
 * - Preserves Clean Architecture boundaries (UI → Domain → Repository),
 * - Enforces slot invariants (distinctness),
 * - Allows future slot-expansion or validation rules without modifying UI call sites.
 *
 * ## Behavior
 * - Accepts up to two pinned nutrient slots (`slot0`, `slot1`).
 * - Enforces that both slots, if non-null, must reference distinct [NutrientKey] values.
 * - Delegates persistence to [UserPinnedNutrientRepository.setPinnedPositions].
 * - Replaces existing pinned positions with the provided values.
 *
 * ## Parameters
 * - `slot0`: First pinned nutrient (nullable).
 * - `slot1`: Second pinned nutrient (nullable).
 *
 * ## Return
 * No return value. This is a suspend write operation.
 *
 * ## Edge cases
 * - Both slots null → clears pinned nutrients.
 * - One slot null → single pinned nutrient.
 * - Same nutrient in both slots → throws `IllegalArgumentException`.
 *
 * ## Pitfalls / gotchas
 * - This use case enforces distinctness but does not enforce non-null requirements.
 * - If future designs allow more than two slots, the invariant logic must be updated here.
 * - UI must not rely on repository-level validation; domain guarantees distinctness.
 *
 * ## Architectural rules
 * - Pure domain write operation.
 * - No UI state mutation.
 * - No direct database access.
 * - Repository is the only persistence boundary.
 */
class SetPinnedNutrientsUseCase @Inject constructor(
    private val repo: UserPinnedNutrientRepository
) {
    suspend operator fun invoke(slot0: NutrientKey?, slot1: NutrientKey?) {
        require(slot0 == null || slot0 != slot1) { "Pinned nutrients must be distinct" }
        repo.setPinnedPositions(slot0, slot1)
    }
}

/**
 * FUTURE-AI / MAINTENANCE KDoc (Do not remove)
 *
 * ## Invariants (must not change)
 * - Pinned slots must always be distinct when both are non-null.
 * - Null values are allowed and represent empty slots.
 * - This use case must remain the enforcement gate for slot distinctness.
 *
 * ## Do not refactor notes
 * - Do not remove the `require` distinctness check.
 * - Do not silently auto-correct duplicates (e.g., nulling one slot).
 * - Do not introduce UI-dependent ordering logic here.
 *
 * ## Architectural boundaries
 * - UI triggers this use case.
 * - Repository handles persistence.
 * - Domain layer must not depend on database APIs.
 *
 * ## Migration notes (KMP / time APIs)
 * - No time or platform-specific APIs used.
 * - Safe to migrate to shared domain if repository abstraction remains intact.
 *
 * ## Performance considerations
 * - Constant-time write operation.
 * - No need for batching or optimization unless slot count expands significantly.
 */