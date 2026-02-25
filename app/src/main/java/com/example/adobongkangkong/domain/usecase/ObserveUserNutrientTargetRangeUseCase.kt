package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.repository.UserNutrientTargetRepository
import com.example.adobongkangkong.ui.calendar.model.TargetRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Observes the user’s configured daily target range (min / target / max) for a single nutrient key.
 *
 * ## Purpose
 * Provide a reactive stream of a nutrient’s configured target range so UI and domain logic can render
 * progress/goal state consistently without duplicating target-mapping logic.
 *
 * ## Rationale (why this use case exists)
 * User nutrient targets are stored as a map keyed by a nutrient identifier (string/int depending on
 * the nutrient system). Most call sites only care about one nutrient at a time (e.g., protein range,
 * sodium range). This use case:
 * - centralizes the lookup and mapping into a stable [TargetRange] model,
 * - ensures missing targets degrade gracefully (null bounds),
 * - provides a distinct stream to avoid unnecessary UI recompositions.
 *
 * ## Behavior
 * - Observes the full set of user nutrient targets from [UserNutrientTargetRepository].
 * - Extracts the entry for the requested [NutrientKey].
 * - Maps that entry into a [TargetRange]:
 *   - `min` from `minPerDay`
 *   - `target` from `targetPerDay`
 *   - `max` from `maxPerDay`
 * - If the nutrient has no configured target, emits a [TargetRange] with all fields null.
 * - Applies `distinctUntilChanged()` to avoid emitting identical ranges.
 *
 * ## Parameters
 * - `key`: The nutrient to look up. The repository map is indexed by `key.value`.
 *
 * ## Return
 * A [Flow] of [TargetRange] for the provided nutrient key, updating whenever the underlying targets
 * map changes.
 *
 * ## Edge cases
 * - Missing target entry → returns `TargetRange(null, null, null)`.
 * - Partially configured target (e.g., only `targetPerDay`) → emits null for missing bounds.
 * - If stored values are inconsistent (min > max, etc.), this use case does not correct them; it
 *   surfaces the stored configuration as-is.
 *
 * ## Pitfalls / gotchas
 * - The repository map key must match [NutrientKey.value]. If the nutrient-keying strategy changes,
 *   update both the repository contract and this mapping together.
 * - This use case intentionally does not enforce validation rules; validation belongs in the target
 *   editing flow (write path), not in the read/observe path.
 *
 * ## Architectural rules
 * - Domain use case reads targets via [UserNutrientTargetRepository] only.
 * - No DB access, no UI state mutation, no side effects beyond observing flows.
 */
class ObserveUserNutrientTargetRangeUseCase @Inject constructor(
    private val repo: UserNutrientTargetRepository
) {
    /**
     * Observes the target range for a single nutrient.
     *
     * @param key Nutrient identifier used to index the stored targets map.
     * @return A stream of [TargetRange] representing the user’s configured bounds for this nutrient.
     */
    operator fun invoke(key: NutrientKey): Flow<TargetRange> =
        repo.observeTargets()
            .map { map ->
                val t = map[key.value] // repository keys must align with NutrientKey.value
                TargetRange(
                    min = t?.minPerDay,
                    target = t?.targetPerDay,
                    max = t?.maxPerDay
                )
            }
            .distinctUntilChanged()
}

/**
 * FUTURE-AI / MAINTENANCE KDoc (Do not remove)
 *
 * ## Invariants (must not change)
 * - Read path only: this use case must not write targets or perform validation side effects.
 * - Missing targets must continue to degrade gracefully to `TargetRange(null, null, null)`.
 * - Mapping must remain: `minPerDay -> min`, `targetPerDay -> target`, `maxPerDay -> max`.
 * - Keying must remain consistent with `map[key.value]` unless the repository contract changes.
 *
 * ## Do not refactor notes
 * - Do not inline this mapping into UI call sites; keep it centralized to avoid drift.
 * - Keep `distinctUntilChanged()` to prevent redundant emissions and recompositions.
 * - Do not “helpfully” clamp, normalize, or reorder bounds here; that belongs in the editor/write path.
 *
 * ## Architectural boundaries
 * - Domain use case depends on [UserNutrientTargetRepository] and domain keys; it may return a simple
 *   model used by UI ([TargetRange]) but must not depend on UI state machinery.
 * - No joins to foods/logs/snapshots; targets are independent configuration data.
 *
 * ## Migration notes (KMP / time APIs)
 * - No time APIs involved. If this is moved to KMP shared code, ensure `NutrientKey.value` remains a
 *   stable primitive key and repository keying is unchanged.
 *
 * ## Performance considerations
 * - Repository emits the full targets map; this use case projects it to a single [TargetRange].
 * - `distinctUntilChanged()` is required to avoid downstream churn when unrelated targets change but
 *   the requested nutrient’s range does not.
 */