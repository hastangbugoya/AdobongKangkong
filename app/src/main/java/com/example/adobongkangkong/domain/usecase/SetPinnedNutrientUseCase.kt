package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.repository.UserPinnedNutrientRepository
import javax.inject.Inject

/**
 * Sets a single pinned nutrient at a specific slot position.
 *
 * ## Purpose
 * Update exactly one pinned nutrient position (slot 0 or slot 1) without requiring
 * the caller to provide the full two-slot state.
 *
 * ## Rationale (why this use case exists — important)
 * While [SetPinnedNutrientsUseCase] handles full two-slot updates atomically,
 * many UI interactions are slot-specific (e.g., user taps slot 1 and changes only that slot).
 *
 * This use case exists to:
 * - Support incremental slot updates without forcing UI to read-modify-write both slots.
 * - Preserve clean architecture boundaries (UI → Domain → Repository).
 * - Enforce slot index invariants centrally (only valid positions allowed).
 * - Keep slot-level semantics explicit and constrained.
 *
 * Having both use cases is intentional:
 * - `SetPinnedNutrientsUseCase` → full-state replacement.
 * - `SetPinnedNutrientUseCase` → single-slot mutation.
 *
 * This separation avoids accidental UI-driven coupling and keeps domain intent clear.
 *
 * ## Behavior
 * - Accepts a `position` index (must be 0 or 1).
 * - Accepts a nullable [NutrientKey] (null clears that slot).
 * - Validates the slot index.
 * - Delegates to [UserPinnedNutrientRepository.setPinned].
 *
 * ## Parameters
 * - `position`: Slot index. Must be 0 or 1.
 * - `key`: Nutrient to pin in that slot. Null clears the slot.
 *
 * ## Return
 * No return value. This is a suspend write operation.
 *
 * ## Edge cases
 * - `key == null` → clears the slot.
 * - Invalid `position` → throws `IllegalArgumentException`.
 * - Distinctness across slots is not enforced here (repository or higher-level use case may enforce).
 *
 * ## Pitfalls / gotchas
 * - This use case validates only slot index, not cross-slot distinctness.
 *   If distinctness is required, use or coordinate with `SetPinnedNutrientsUseCase`.
 * - UI must not assume more than two slots exist.
 *
 * ## Architectural rules
 * - Pure domain write operation.
 * - No UI state mutation.
 * - No direct database access.
 * - Repository is the only persistence boundary.
 */
class SetPinnedNutrientUseCase @Inject constructor(
    private val repo: UserPinnedNutrientRepository
) {
    suspend operator fun invoke(position: Int, key: NutrientKey?) {

        require(position == 0 || position == 1) { "position must be 0 or 1" }

        repo.setPinned(position = position, key = key)
    }
}

/**
 * FUTURE-AI / MAINTENANCE KDoc (Do not remove)
 *
 * ## Invariants (must not change)
 * - Valid slot positions are strictly 0 and 1.
 * - Null key clears a slot.
 * - This use case must remain the enforcement gate for valid slot index.
 *
 * ## Do not refactor notes
 * - Do not remove the `require(position == 0 || position == 1)` guard.
 * - Do not expand slot count here without updating both pinned use cases and repository contract.
 * - Do not silently clamp invalid positions.
 *
 * ## Architectural boundaries
 * - UI calls this use case for slot-level mutation.
 * - Repository persists state.
 * - Domain layer must not depend on DB/Room APIs.
 *
 * ## Migration notes (KMP / time APIs)
 * - No time or platform-specific APIs involved.
 * - Safe for KMP migration if repository abstraction remains intact.
 *
 * ## Performance considerations
 * - Constant-time write operation.
 * - No batching or heavy computation.
 */