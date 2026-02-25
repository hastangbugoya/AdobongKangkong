package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.TargetEdit
import com.example.adobongkangkong.domain.model.UserNutrientTarget
import com.example.adobongkangkong.domain.repository.UserNutrientTargetRepository
import javax.inject.Inject

/**
 * Creates or updates (upserts) a single user nutrient target after enforcing basic range consistency.
 *
 * ## Purpose
 * Persist a user’s daily nutrient target configuration (min / target / max) for one nutrient key.
 *
 * ## Rationale (why this use case exists)
 * User nutrient targets influence progress indicators, goal evaluation, and planner summaries.
 * The write path must enforce consistent constraints so downstream reads can remain simple and
 * predictable (no repeated validation logic in UI and no “impossible” ranges stored in DB).
 *
 * Centralizing target persistence in the domain layer:
 * - Preserves Clean Architecture boundaries (UI → Domain → Repository),
 * - Ensures consistent validation regardless of which screen edits targets,
 * - Keeps repository implementations dumb (storage only) while domain owns rules.
 *
 * ## Behavior
 * - Validates that provided bounds are internally consistent:
 *   - `min <= max` when both are present
 *   - `target >= min` when both are present
 *   - `target <= max` when both are present
 * - Constructs a [UserNutrientTarget] from the incoming [TargetEdit].
 * - Delegates persistence to [UserNutrientTargetRepository.upsert].
 *
 * ## Parameters
 * - `edit`: Target edit payload containing:
 *   - `key` (nutrient identifier),
 *   - optional `min`, `target`, `max` values.
 *
 * ## Return
 * No return value. This is a suspend write operation.
 *
 * ## Edge cases
 * - Any of `min`, `target`, `max` may be null (meaning “unset”).
 * - All values null is allowed and represents “no configured target” for that nutrient
 *   (repository behavior determines whether this becomes a row with nulls or a cleared entry).
 * - Equal bounds are allowed (e.g., min == max, target == min).
 *
 * ## Pitfalls / gotchas
 * - This validates only internal ordering constraints; it does not enforce unit correctness,
 *   positivity, or recommended ranges (those are UI/UX decisions or future domain rules).
 * - Violations throw `IllegalArgumentException` via `require(...)`; callers must handle this
 *   (typically by preventing invalid input in the editor UI).
 * - Nutrient key persistence uses `edit.key.value` as the stored identifier; keying strategy
 *   must remain stable across migrations.
 *
 * ## Architectural rules
 * - Domain owns validation and mapping; repository owns persistence.
 * - No UI state mutation, no navigation, no direct DB access here.
 */
class UpsertUserNutrientTargetUseCase @Inject constructor(
    private val repo: UserNutrientTargetRepository
) {
    suspend operator fun invoke(edit: TargetEdit) {
        // validate consistency
        require(!(edit.min != null && edit.max != null && edit.min > edit.max)) {
            "min cannot be greater than max"
        }
        require(!(edit.target != null && edit.min != null && edit.target < edit.min)) {
            "target cannot be less than min"
        }
        require(!(edit.target != null && edit.max != null && edit.target > edit.max)) {
            "target cannot be greater than max"
        }

        repo.upsert(
            UserNutrientTarget(
                nutrientCode = edit.key.value,
                minPerDay = edit.min,
                targetPerDay = edit.target,
                maxPerDay = edit.max
            )
        )
    }
}

/**
 * FUTURE-AI / MAINTENANCE KDoc (Do not remove)
 *
 * ## Invariants (must not change)
 * - Ordering constraints must remain enforced on the write path:
 *   - If both present: min <= max
 *   - If both present: target >= min
 *   - If both present: target <= max
 * - Stored nutrient identifier must remain `edit.key.value` unless the repository contract changes.
 * - This use case must remain the domain gate for validation (do not push this into UI only).
 *
 * ## Do not refactor notes
 * - Do not remove the `require(...)` checks; they prevent impossible states from entering storage.
 * - Do not “auto-correct” invalid values (clamping/swapping) without explicit product rules.
 * - Do not introduce cross-nutrient validation here (e.g., macro sum rules); keep it single-nutrient.
 *
 * ## Architectural boundaries
 * - UI supplies a [TargetEdit]; domain validates and maps; repository persists.
 * - Repository should remain storage-focused; keep business rules here.
 *
 * ## Migration notes (KMP / time APIs)
 * - No time APIs involved.
 * - For KMP migration, ensure `NutrientKey.value` remains a stable primitive identifier.
 *
 * ## Performance considerations
 * - Constant-time validation + single upsert write.
 * - Avoid adding reads here unless required for explicit conflict resolution rules.
 */