package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import javax.inject.Inject

/**
 * Adds a FOOD planned item under an existing planned meal.
 *
 * ----------------------------------------------------------------------------
 * Purpose
 * ----------------------------------------------------------------------------
 *
 * Persists a single “planned food” row tied to a planned meal, capturing the user’s
 * intended quantity (grams or servings) in the planner system.
 *
 * This is the planner equivalent of “I plan to eat X of this food”, without creating
 * any immutable log snapshot entry.
 *
 * ----------------------------------------------------------------------------
 * Rationale (why this use case exists)
 * ----------------------------------------------------------------------------
 *
 * Planner items are *intent* (future/plan), not historical truth (logs).
 *
 * We keep FOOD planned insertion separate from RECIPE and RECIPE_BATCH because:
 *
 * - FOOD quantities may be specified by grams or servings (mirrors logging UI),
 * - FOOD expansion/aggregation typically resolves directly via foodId,
 * - the planner must support mixed quantity inputs without forcing normalization
 *   at write-time (imported foods may be incomplete; enforcement happens at use-time).
 *
 * Keeping this as a small, single-purpose use case avoids leaking planner row shape
 * and insertion rules into UI or ViewModels.
 *
 * ----------------------------------------------------------------------------
 * Behavior
 * ----------------------------------------------------------------------------
 *
 * - Validates identifiers:
 *   - mealId > 0
 *   - foodId > 0
 * - Validates quantity:
 *   - grams and/or servings may be provided
 *   - at least one must be provided
 *   - if provided, each must be > 0
 * - Sets sortOrder:
 *   - if not provided, defaults to Int.MAX_VALUE to “append” without a DB read
 * - Inserts a [PlannedItemEntity] with:
 *   - type = [PlannedItemSource.FOOD]
 *   - refId = foodId
 *   - grams/servings as provided
 *
 * ----------------------------------------------------------------------------
 * Parameters
 * ----------------------------------------------------------------------------
 * @param mealId The owning planned meal row id. Must be > 0.
 * @param foodId The referenced Food id. Must be > 0.
 * @param grams Planned grams amount (nullable). If provided, must be > 0.
 * @param servings Planned servings amount (nullable). If provided, must be > 0.
 * @param sortOrder Optional explicit sort order for UI display within the meal.
 *
 * ----------------------------------------------------------------------------
 * Return
 * ----------------------------------------------------------------------------
 * @return The newly inserted planned item id (database row id).
 *
 * ----------------------------------------------------------------------------
 * Edge cases
 * ----------------------------------------------------------------------------
 *
 * - Both grams and servings provided:
 *   - Allowed here as long as both > 0, but discouraged at higher layers.
 *   - This use case keeps the rule “minimal write validation” and does not enforce
 *     mutual exclusivity (unlike some ingredient resolvers).
 *
 * - Neither grams nor servings provided:
 *   - Blocked (throws) to avoid creating “quantity-less” items.
 *
 * - sortOrder default:
 *   - Uses Int.MAX_VALUE so new items naturally appear at the end when reads order
 *     by (sortOrder ASC, id ASC). This avoids “read max sort order then +1”.
 *
 * ----------------------------------------------------------------------------
 * Pitfalls / gotchas
 * ----------------------------------------------------------------------------
 *
 * - This does NOT validate whether the food is usable by servings (grams-per-serving).
 *   That is a “correctness-at-use-time” rule (ServingPolicy) and should be enforced
 *   by higher layers at edit/confirm time or during expansion.
 *
 * - This is not logging:
 *   - No snapshot is created here.
 *   - Planned items may later expand into totals/needs using current snapshot/food data,
 *     which is correct for “plan” semantics.
 *
 * ----------------------------------------------------------------------------
 * Architectural rules
 * ----------------------------------------------------------------------------
 *
 * - Domain write use case: no UI state mutation, no navigation.
 * - Repository is the only persistence boundary.
 * - Keep insertion deterministic (no “read-before-write” for sort order by default).
 */
class AddPlannedFoodItemUseCase @Inject constructor(
    private val items: PlannedItemRepository
) {
    suspend operator fun invoke(
        mealId: Long,
        foodId: Long,
        grams: Double? = null,
        servings: Double? = null,
        sortOrder: Int? = null
    ): Long {
        require(mealId > 0) { "mealId must be > 0" }
        require(foodId > 0) { "foodId must be > 0" }
        requireValidQuantity(grams, servings)

        // Append by default without needing a DB read for "max sort order".
        // Reads should order by (sortOrder ASC, id ASC).
        val finalSortOrder = sortOrder ?: Int.MAX_VALUE

        val entity = PlannedItemEntity(
            mealId = mealId,
            type = PlannedItemSource.FOOD,
            refId = foodId,
            grams = grams,
            servings = servings,
            sortOrder = finalSortOrder
        )

        return items.insert(entity)
    }

    private fun requireValidQuantity(grams: Double?, servings: Double?) {
        if (grams != null) require(grams > 0.0) { "grams must be > 0 when provided" }
        if (servings != null) require(servings > 0.0) { "servings must be > 0 when provided" }
        require(!(grams == null && servings == null)) { "Either grams or servings must be provided" }
    }
}

/**
 * =============================================================================
 * FOR FUTURE AI ASSISTANT — AddPlannedFoodItemUseCase invariants and boundaries
 * =============================================================================
 *
 * Invariants (MUST NOT CHANGE)
 * - mealId and foodId must be > 0.
 * - At least one quantity (grams or servings) must be provided.
 * - If provided, grams/servings must be > 0.
 * - Default sortOrder is Int.MAX_VALUE (append semantics) to avoid read-before-write.
 *
 * Do not refactor / do not “improve”
 * - Do NOT add a DB read to compute max(sortOrder) by default.
 * - Do NOT auto-normalize servings into grams here (ServingPolicy is a separate concern).
 * - Do NOT create nutrition snapshots or log entries here (planner != logging).
 *
 * Architectural boundaries
 * - This is a domain write use case that only writes PlannedItemEntity via PlannedItemRepository.
 * - Any expansion (food lookup, snapshot usage, totals/needs computation) belongs in read/observe
 *   use cases and must remain separate from this insertion.
 *
 * Performance notes
 * - Int.MAX_VALUE default is intentional: O(1) insert, no extra query.
 */