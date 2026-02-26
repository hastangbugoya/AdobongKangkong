package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import javax.inject.Inject

/**
 * Adds a RECIPE_BATCH planned item under an existing planned meal.
 *
 * ----------------------------------------------------------------------------
 * Purpose
 * ----------------------------------------------------------------------------
 *
 * Persists a single “planned recipe batch consumption” row tied to a planned meal.
 *
 * A RECIPE_BATCH planned item references a specific batch instance (e.g., meal prep batch)
 * and stores an intended quantity (grams or servings) to be consumed from that batch.
 *
 * ----------------------------------------------------------------------------
 * Rationale (why this use case exists)
 * ----------------------------------------------------------------------------
 *
 * RECIPE_BATCH is intentionally separate from:
 *
 * - RECIPE (recipe definition / recipe foodId planned by servings only)
 * - FOOD (direct food id planned by grams/servings)
 *
 * A “batch” represents a produced output with its own identifier and potentially its own
 * derived snapshot food, yield, or cooked gram basis.
 *
 * Planning against a batch is the right model for meal-prep scenarios:
 * “Eat 300g from my Chili batch” or “Eat 2 servings from batch #12”.
 *
 * Keeping this use case distinct ensures:
 * - refId semantics are unambiguous (recipeBatchId),
 * - quantity semantics match batch consumption (grams or servings),
 * - downstream expansion can choose the correct computation path for batches.
 *
 * ----------------------------------------------------------------------------
 * Behavior
 * ----------------------------------------------------------------------------
 *
 * - Validates identifiers:
 *   - mealId > 0
 *   - recipeBatchId > 0
 * - Validates quantity:
 *   - grams and/or servings may be provided
 *   - at least one must be provided
 *   - if provided, each must be > 0
 * - Sets sortOrder:
 *   - if not provided, defaults to Int.MAX_VALUE to “append” without a DB read
 * - Inserts a [PlannedItemEntity] with:
 *   - type = [PlannedItemSource.RECIPE_BATCH]
 *   - refId = recipeBatchId
 *   - grams/servings as provided
 *
 * ----------------------------------------------------------------------------
 * Parameters
 * ----------------------------------------------------------------------------
 * @param mealId The owning planned meal row id. Must be > 0.
 * @param recipeBatchId The referenced RecipeBatch id. Must be > 0.
 * @param grams Planned grams amount from the batch (nullable). If provided, must be > 0.
 * @param servings Planned servings amount from the batch (nullable). If provided, must be > 0.
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
 *   - Allowed here as long as both > 0, but higher layers should avoid ambiguity.
 *
 * - Batch identity vs recipe identity:
 *   - A batch may be deleted/archived separately from the recipe definition; handling missing
 *     batch data is the responsibility of expansion/aggregation layers (blocked/warnings).
 *
 * ----------------------------------------------------------------------------
 * Pitfalls / gotchas
 * ----------------------------------------------------------------------------
 *
 * - This is not the same as RECIPE:
 *   - refId is recipeBatchId, not recipeFoodId.
 *   - The expansion path may differ (batch may have perCookedGram, yields, etc.).
 *
 * - This does not create immutable log snapshots.
 *   Planned items are intent and may reflect current batch metadata when expanded.
 *
 * ----------------------------------------------------------------------------
 * Architectural rules
 * ----------------------------------------------------------------------------
 *
 * - Domain write use case: no UI state mutation, no navigation.
 * - Repository is the only persistence boundary.
 * - Keep insertion deterministic (no “read-before-write” for sort order by default).
 */
class AddPlannedRecipeBatchItemUseCase @Inject constructor(
    private val items: PlannedItemRepository
) {
    suspend operator fun invoke(
        mealId: Long,
        recipeBatchId: Long,
        grams: Double? = null,
        servings: Double? = null,
        sortOrder: Int? = null
    ): Long {
        require(mealId > 0) { "mealId must be > 0" }
        require(recipeBatchId > 0) { "recipeBatchId must be > 0" }
        requireValidQuantity(grams, servings)

        // Append by default without needing a DB read for "max sort order".
        // Reads should order by (sortOrder ASC, id ASC).
        val finalSortOrder = sortOrder ?: Int.MAX_VALUE

        val entity = PlannedItemEntity(
            mealId = mealId,
            type = PlannedItemSource.RECIPE_BATCH,
            refId = recipeBatchId,
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
 * FOR FUTURE AI ASSISTANT — AddPlannedRecipeBatchItemUseCase invariants and boundaries
 * =============================================================================
 *
 * Invariants (MUST NOT CHANGE)
 * - mealId and recipeBatchId must be > 0.
 * - At least one quantity (grams or servings) must be provided.
 * - If provided, grams/servings must be > 0.
 * - PlannedItemEntity.type is RECIPE_BATCH.
 * - PlannedItemEntity.refId is recipeBatchId (do not swap with recipeFoodId).
 * - Default sortOrder is Int.MAX_VALUE (append semantics) to avoid read-before-write.
 *
 * Do not refactor / do not “improve”
 * - Do NOT add a DB read to compute max(sortOrder) by default.
 * - Do NOT collapse this into AddPlannedFoodItemUseCase; batch identity is distinct and
 *   must remain explicit for expansion.
 * - Do NOT create logs or snapshots here.
 *
 * Architectural boundaries
 * - This use case only writes PlannedItemEntity via PlannedItemRepository.
 * - Batch nutrition math and expansion logic belong elsewhere.
 *
 * Performance notes
 * - Must remain O(1) insert by default (no extra query).
 */