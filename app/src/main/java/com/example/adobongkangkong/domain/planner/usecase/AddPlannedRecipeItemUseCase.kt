package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import javax.inject.Inject

/**
 * Adds a RECIPE planned item under an existing planned meal.
 *
 * ----------------------------------------------------------------------------
 * Purpose
 * ----------------------------------------------------------------------------
 *
 * Persists a single “planned recipe” row tied to a planned meal, representing an intent
 * to consume servings of a *recipe definition* (editable recipe food).
 *
 * This planned item references a recipe by its recipeFoodId and stores servings only.
 *
 * ----------------------------------------------------------------------------
 * Rationale (why this use case exists)
 * ----------------------------------------------------------------------------
 *
 * RECIPE planned items are intentionally distinct from:
 *
 * - FOOD planned items (which allow grams or servings and expand directly to a food)
 * - RECIPE_BATCH planned items (which reference a produced batch with its own id and allow
 *   grams/servings consumption of that batch)
 *
 * A “recipe” here means the *definition* (the recipe food that can be edited).
 * Planning servings against the definition is the most natural UX (“I will eat 2 servings
 * of my Chicken Adobo recipe”), and expansion logic can compute totals based on the recipe’s
 * yield and ingredient snapshots.
 *
 * Keeping this separate ensures:
 * - the planned row is unambiguous (refId maps to recipeFoodId),
 * - downstream expansion routes correctly (expandRecipe expects recipeFoodId),
 * - quantity rules remain consistent (servings required).
 *
 * ----------------------------------------------------------------------------
 * Behavior
 * ----------------------------------------------------------------------------
 *
 * - Validates identifiers:
 *   - mealId > 0
 *   - recipeFoodId > 0
 * - Validates quantity:
 *   - plannedServings > 0
 * - Sets sortOrder:
 *   - if not provided, defaults to Int.MAX_VALUE to “append” without a DB read
 * - Inserts a [PlannedItemEntity] with:
 *   - type = [PlannedItemSource.RECIPE]
 *   - refId = recipeFoodId (must match expandRecipe() expectations)
 *   - servings = plannedServings
 *   - grams = null (recipes are planned by servings in this model)
 *
 * ----------------------------------------------------------------------------
 * Parameters
 * ----------------------------------------------------------------------------
 * @param mealId The owning planned meal row id. Must be > 0.
 * @param recipeFoodId The Food id that represents the editable recipe definition. Must be > 0.
 * @param plannedServings Planned servings of the recipe. Must be > 0.
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
 * - The recipe may later change (ingredients, yields, etc.). That is expected:
 *   planner items represent intent and may reflect updated recipe definitions when expanded.
 *
 * - Missing yields/invalid recipe definitions should be handled during expansion/aggregation
 *   (warnings, blocked states), not here. This use case only validates basic inputs.
 *
 * ----------------------------------------------------------------------------
 * Pitfalls / gotchas
 * ----------------------------------------------------------------------------
 *
 * - refId MUST be recipeFoodId (not recipeId) to match the existing expansion pipeline.
 *   Mixing these ids will cause “recipe not found” during expansion.
 *
 * - This does not create immutable snapshots or logs.
 *   Planned recipes are recalculated during planning/expansion, not frozen historically.
 *
 * ----------------------------------------------------------------------------
 * Architectural rules
 * ----------------------------------------------------------------------------
 *
 * - Domain write use case: no UI state mutation, no navigation.
 * - Repository is the only persistence boundary.
 * - Keep insertion deterministic (no “read-before-write” for sort order by default).
 */
class AddPlannedRecipeItemUseCase @Inject constructor(
    private val items: PlannedItemRepository
) {
    suspend operator fun invoke(
        mealId: Long,
        recipeFoodId: Long,
        plannedServings: Double,
        sortOrder: Int? = null
    ): Long {
        require(mealId > 0) { "mealId must be > 0" }
        require(recipeFoodId > 0) { "recipeFoodId must be > 0" }
        require(plannedServings > 0.0) { "plannedServings must be > 0" }

        val finalSortOrder = sortOrder ?: Int.MAX_VALUE

        val entity = PlannedItemEntity(
            mealId = mealId,
            type = PlannedItemSource.RECIPE,
            refId = recipeFoodId,          // ✅ IMPORTANT: matches expandRecipe()
            grams = null,
            servings = plannedServings,
            sortOrder = finalSortOrder
        )

        return items.insert(entity)
    }
}

/**
 * =============================================================================
 * FOR FUTURE AI ASSISTANT — AddPlannedRecipeItemUseCase invariants and boundaries
 * =============================================================================
 *
 * Invariants (MUST NOT CHANGE)
 * - mealId > 0
 * - recipeFoodId > 0
 * - plannedServings > 0
 * - PlannedItemEntity.type is RECIPE
 * - PlannedItemEntity.refId is recipeFoodId (NOT recipeId). This must stay aligned with expandRecipe().
 * - grams must remain null for RECIPE planned items in this model.
 * - Default sortOrder is Int.MAX_VALUE (append semantics) to avoid read-before-write.
 *
 * Do not refactor / do not “improve”
 * - Do NOT switch refId to recipeId unless the entire expansion pipeline is migrated.
 * - Do NOT add automatic recipe validation here (missing yields, missing ingredients, etc.).
 *   That belongs to recipe expansion / nutrition computation and should surface as warnings there.
 * - Do NOT create logs or snapshots here (planner intent != historical log snapshot).
 *
 * Architectural boundaries
 * - This file is a narrow “write planned item row” use case.
 * - Recipe math (ComputeRecipeBatchNutritionUseCase) and expansion belong elsewhere.
 *
 * Performance notes
 * - Must remain O(1) insert by default (no extra query).
 */