package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.FoodHardDeleteBlockers
import com.example.adobongkangkong.domain.repository.FoodRepository
import javax.inject.Inject

/**
 * HardDeleteFoodIfUnusedUseCase
 *
 * ## Purpose
 * Attempts to permanently delete a Food record *only if it is safe to do so* (i.e., the food is not
 * referenced by any dependent data).
 *
 * ## Rationale
 * In this app, Foods can be referenced by multiple subsystems (logs, planner, recipes, batches).
 * A hard delete must not:
 * - break referential integrity,
 * - orphan related rows that assume the food exists,
 * - or silently invalidate history.
 *
 * Therefore, hard deletion is gated by a dependency scan. If anything still references the food,
 * deletion is blocked and the caller receives a structured explanation that can be surfaced in UI.
 *
 * ## Common scenarios
 * - **User created a food by mistake** and wants it removed:
 *   - If unused → hard delete succeeds.
 * - **User wants to clean up duplicates**:
 *   - If one duplicate is unused → hard delete succeeds.
 *   - If referenced anywhere → blocked with reasons.
 * - **Food is a recipe-food** (special case):
 *   - Recipes have additional identity/data (RecipeEntity + ingredients). Hard delete is blocked
 *     until recipe-side data is removed (explicit rule).
 * - **Food has historical logs**:
 *   - Hard delete is blocked to avoid breaking usage references and any UI flows relying on stableId.
 *
 * ## Behavior
 * 1) Loads the Food via [FoodRepository.getById].
 *    - If missing → returns [Result.NotFound].
 * 2) Fetches dependency blockers via [FoodRepository.getFoodHardDeleteBlockers].
 * 3) If any blockers exist → returns [Result.Blocked] containing:
 *    - the raw [FoodHardDeleteBlockers],
 *    - a UI-friendly list of human-readable [reasons].
 * 4) If not blocked → calls [FoodRepository.hardDeleteFood] and returns [Result.Success].
 *
 * ## Parameters
 * @param foodId The id of the food the caller wants to hard delete.
 *
 * ## Return
 * @return [Result]
 * - [Result.Success] if deletion was performed
 * - [Result.Blocked] if deletion is unsafe due to dependencies
 * - [Result.NotFound] if the food does not exist
 *
 * ## Agreed rules / assumptions
 * - Hard delete is allowed ONLY when [FoodHardDeleteBlockers.isBlocked] is false.
 * - Blocker checks include at least:
 *   - Recipe-food status
 *   - Log usage (stableId)
 *   - Planned item references (foodId)
 *   - Recipe ingredient references (foodId)
 *   - Recipe batch references (batchFoodId)
 * - This use case does not attempt cascading deletes. Cascades must be explicit and handled in
 *   dedicated flows (e.g., delete recipe + ingredients first).
 *
 * ## Ordering and edges
 * - If blockers change between the check and delete (rare), repository-layer constraints should
 *   still prevent corruption. This use case relies on repository correctness.
 * - This use case does not remove media; run media cleanup separately if desired.
 */
class HardDeleteFoodIfUnusedUseCase @Inject constructor(
    private val foodRepository: FoodRepository
) {

    /**
     * Result of attempting a hard delete.
     *
     * [Blocked] includes both machine-usable blocker data ([blockers]) and human-readable [reasons]
     * suitable for showing directly in UI.
     */
    sealed class Result {
        /** Food was safely deleted. */
        data object Success : Result()

        /**
         * Food could not be deleted because it is referenced by other data.
         *
         * @property foodId The food id that was requested for deletion.
         * @property reasons Human-readable reasons describing why deletion is blocked.
         * @property blockers Raw blocker counts/flags for programmatic handling (UI decisions, logs).
         */
        data class Blocked(
            val foodId: Long,
            val reasons: List<String>,
            val blockers: FoodHardDeleteBlockers
        ) : Result()

        /**
         * Food could not be deleted because it does not exist.
         *
         * @property foodId The missing food id.
         */
        data class NotFound(val foodId: Long) : Result()
    }

    /**
     * Attempts to hard delete the given [foodId] if it has no dependency blockers.
     *
     * @param foodId Food id to delete.
     * @return [Result.Success] if deleted, otherwise [Result.Blocked] or [Result.NotFound].
     */
    suspend operator fun invoke(foodId: Long): Result {
        val existing = foodRepository.getById(foodId) ?: return Result.NotFound(foodId)

        val blockers = foodRepository.getFoodHardDeleteBlockers(existing.id)
        if (blockers.isBlocked) {
            val reasons = buildList {
                if (blockers.isRecipeFood) add("Food is a recipe (hard delete requires deleting RecipeEntity too).")
                if (blockers.logsUsingStableId > 0) add("Referenced by logs: ${blockers.logsUsingStableId}")
                if (blockers.plannedItemsUsingFoodId > 0) add("Referenced by planned items: ${blockers.plannedItemsUsingFoodId}")
                if (blockers.recipeIngredientsUsingFoodId > 0) add("Used as ingredient in recipes: ${blockers.recipeIngredientsUsingFoodId}")
                if (blockers.recipeBatchesUsingBatchFoodId > 0) add("Used by recipe batches (batchFoodId): ${blockers.recipeBatchesUsingBatchFoodId}")
            }

            return Result.Blocked(
                foodId = existing.id,
                reasons = reasons,
                blockers = blockers
            )
        }

        foodRepository.hardDeleteFood(existing.id)
        return Result.Success
    }
}

/**
 * FUTURE AI ASSISTANT NOTES
 *
 * - Standard use case documentation format in this codebase:
 *   1) Top KDoc: dev-facing purpose/rationale/scenarios/behavior/params/return/edges.
 *   2) Bottom KDoc: constraints + invariants for automated edits.
 *
 * - Do NOT convert this into cascading deletes unless explicitly requested.
 *   The "blocked with reasons" behavior is intentional to force explicit user decisions.
 *
 * - Keep repository boundaries:
 *   - No DAO/Room access here.
 *   - All dependency counting logic lives in FoodRepository.getFoodHardDeleteBlockers().
 *
 * - If new blockers are added later:
 *   - Extend FoodHardDeleteBlockers and update the reasons list here.
 *   - Maintain stable, user-readable phrasing (avoid breaking UI tests/screenshots if any).
 *
 * - If UI wants richer presentation:
 *   - Keep [Result.Blocked] as the truth.
 *   - Add helper formatting elsewhere rather than bloating this use case.
 */