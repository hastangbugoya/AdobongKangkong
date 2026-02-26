package com.example.adobongkangkong.domain.usda

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity
import com.example.adobongkangkong.domain.model.FoodNutrientRow
import com.example.adobongkangkong.domain.repository.FoodNutrientRepository
import javax.inject.Inject

/**
 * Merges USDA-provided nutrient rows into an existing food’s nutrient set, preserving manual-only nutrients.
 *
 * Purpose
 * - Apply USDA nutrient data to an already-existing food without discarding user-entered nutrients that USDA does not provide.
 * - Produce a deterministic merged nutrient list suitable for persistence.
 *
 * Rationale (why this use case exists)
 * - Users may have created or edited foods manually (including adding custom nutrients or corrections).
 * - Later, USDA data may become available (via barcode scan/import) and should become authoritative where it overlaps.
 * - However, USDA is not guaranteed to cover every nutrient the user tracked (custom or app-catalog drift),
 *   so manual-only rows must be preserved.
 * - This use case centralizes the “USDA wins on collision” policy so merge behavior is consistent across the app.
 *
 * Behavior
 * - Reads existing nutrient rows for the target food via [FoodNutrientRepository.getForFood].
 * - Indexes incoming USDA rows by collision key = (nutrientId, basisType).
 * - Produces merged output:
 *   1) Include all USDA rows (USDA is authoritative).
 *   2) Include any existing rows that do NOT collide with USDA keys (manual-only preservation).
 * - Persists merged result via [FoodNutrientRepository.replaceForFood].
 *
 * Parameters
 * - foodId: Target food whose nutrients will be replaced with the merged set.
 * - usdaNutrients: Incoming USDA nutrient rows (already mapped into internal nutrient catalog + basis types).
 *
 * Return
 * - Unit. Persistence is the side-effect (replaceForFood).
 *
 * Edge cases
 * - If usdaNutrients is empty:
 *   - Result becomes “existing preserved” (since there are no collisions), effectively re-writing existing rows.
 * - If existing is empty:
 *   - Result is exactly the USDA list.
 * - Duplicate USDA rows with the same (nutrientId, basisType):
 *   - Only one will survive in the map; which one depends on input ordering (associateBy keeps last).
 *
 * Pitfalls / gotchas
 * - This use case does not validate basis correctness (PER_100G vs PER_100ML vs USDA_REPORTED_SERVING).
 *   It assumes upstream import/canonicalization already decided correct bases.
 * - This performs a full replaceForFood write. If callers expect incremental updates, they must not use this use case.
 * - The merge key includes basisType. If the same nutrient exists in multiple bases, they are treated as distinct rows.
 *   This is intentional, but note your system-wide “one basis per nutrient” rule should generally prevent that upstream.
 *
 * Architectural rules (if applicable)
 * - No UI concerns. No navigation. Pure merge decision + persistence.
 * - Deterministic policy: USDA wins on collisions; manual-only nutrients are preserved.
 * - Logging model note: ISO-date-based logging uses `logDateIso` as authoritative. This use case does not read/write logs.
 * - Snapshot logs are immutable and must not rejoin foods. This use case updates a food’s nutrient set; it must not be
 *   used to modify historical snapshot foods that are already logged.
 */
class MergeUsdaNutrientsIntoFoodUseCase @Inject constructor(
    private val foodNutrientRepository: FoodNutrientRepository
) {

    suspend operator fun invoke(
        foodId: Long,
        usdaNutrients: List<FoodNutrientRow>
    ) {
        // Read existing nutrients
        val existing = foodNutrientRepository.getForFood(foodId)

        // Index USDA nutrients by collision key
        val usdaByKey = usdaNutrients.associateBy {
            NutrientKey(
                nutrientId = it.nutrient.id,
                basisType = it.basisType
            )
        }

        val merged = mutableListOf<FoodNutrientRow>()

        // 1) USDA always wins on collision
        usdaByKey.values.forEach { usda ->
            merged.add(usda)
        }

        // 2) Keep MANUAL nutrients only if USDA does not contain them
        existing.forEach { manual ->
            val key = NutrientKey(
                nutrientId = manual.nutrient.id,
                basisType = manual.basisType
            )

            if (key !in usdaByKey) {
                merged += manual
            }
        }

        // Persist merged result
        foodNutrientRepository.replaceForFood(
            foodId = foodId,
            rows = merged
        )
    }

    /**
     * Collision key used for deterministic merge.
     *
     * Two nutrient rows are considered the “same” for overwrite purposes when both:
     * - nutrientId matches, and
     * - basisType matches.
     *
     * Note:
     * - basisType is part of the key intentionally to avoid collapsing distinct bases.
     */
    private data class NutrientKey(
        val nutrientId: Long,
        val basisType: BasisType
    )
}

/**
 * ===== Bottom KDoc (for future AI assistant) =====
 *
 * Invariants (must never change)
 * - USDA wins on collisions, defined as (nutrientId + basisType).
 * - Existing rows not present in USDA must be preserved.
 * - Merge must be deterministic given the same inputs.
 * - Persistence is performed via replaceForFood (full replacement).
 *
 * Do not refactor notes
 * - Do not change the collision key to nutrientId-only without auditing basis behavior across the app.
 * - Do not introduce deletes beyond replaceForFood semantics; callers rely on manual-only preservation.
 * - Do not reorder rules: USDA rows must be applied first, then manual-only appended.
 *
 * Architectural boundaries
 * - This use case is safe for normal foods that are editable.
 * - Do NOT apply to snapshot foods already referenced by logs; snapshot logs are immutable and must not rejoin foods.
 *
 * Migration notes (KMP / time APIs)
 * - None; no time API usage.
 *
 * Performance considerations
 * - O(N + M) merge: map USDA by key, scan existing once.
 * - Memory: holds map of USDA nutrients and merged list.
 *
 * Maintenance recommendations
 * - If upstream canonicalization enforces one basis per nutrient, consider asserting that as a debug-only check
 *   (future improvement; do not change behavior in this pass).
 * - If duplicates in usdaNutrients become common, consider explicit de-dupe policy (first-wins vs last-wins)
 *   and document it (future change only).
 */