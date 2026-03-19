package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.domain.model.RecipeDraft

data class RecipeHeader(
    val recipeId: Long,
    val foodId: Long,
    val servingsYield: Double,
    val totalYieldGrams: Double?
)

data class RecipeIngredientLine(
    val ingredientFoodId: Long,
    val ingredientServings: Double? = null,
    val ingredientGrams: Double? = null
)

/**
 * Instruction step domain model (data-layer-facing, minimal).
 *
 * Notes:
 * - Mirrors DB entity but remains decoupled from Room.
 * - stableId is preserved for future import/export reconciliation.
 * - imagePath is an app-owned internal relative path (nullable).
 */
data class RecipeInstructionStep(
    val id: Long,
    val stableId: String,
    val recipeId: Long,
    val position: Int,
    val text: String,
    val imagePath: String?
)

interface RecipeRepository {
    suspend fun createRecipe(draft: RecipeDraft): Long // returns recipeId

    // Edit mode
    suspend fun getRecipeByFoodId(foodId: Long): RecipeHeader?
    suspend fun getIngredients(recipeId: Long): List<RecipeIngredientLine>

    suspend fun updateRecipeByFoodId(
        foodId: Long,
        servingsYield: Double,
        totalYieldGrams: Double?,
        ingredients: List<RecipeIngredientLine>
    )

    suspend fun getHeaderByRecipeId(recipeId: Long): RecipeHeader?

    /** Bulk lookup to avoid N+1 in planner/day aggregation (recipeId -> recipe.foodId). */
    suspend fun getFoodIdsByRecipeIds(recipeIds: Set<Long>): Map<Long, Long>

    /** Bulk lookup to avoid N+1 in foods-list category filtering (foodId -> recipeId). */
    suspend fun getRecipeIdsByFoodIds(foodIds: Set<Long>): Map<Long, Long>

    /* ---------------- Instruction Steps ---------------- */

    /**
     * Returns ordered instruction steps for a recipe.
     */
    suspend fun getInstructionSteps(recipeId: Long): List<RecipeInstructionStep>

    /**
     * Inserts a new instruction step.
     * Caller is responsible for correct position assignment.
     */
    suspend fun insertInstructionStep(
        recipeId: Long,
        position: Int,
        text: String
    ): Long

    /**
     * Updates text content of a step.
     */
    suspend fun updateInstructionStepText(
        stepId: Long,
        text: String
    )

    /**
     * Updates ordering (position) of a step.
     *
     * Note:
     * - This is a low-level positional update.
     * - For user-facing reordering, prefer [reorderInstructionSteps],
     *   [moveInstructionStepUp], or [moveInstructionStepDown] so ordering
     *   stays normalized and unique-constraint-safe.
     */
    suspend fun updateInstructionStepPosition(
        stepId: Long,
        position: Int
    )

    /**
     * Sets or clears the image path for a step.
     */
    suspend fun setInstructionStepImage(
        stepId: Long,
        imagePath: String?
    )

    /**
     * Deletes a single instruction step.
     */
    suspend fun deleteInstructionStep(stepId: Long)

    /**
     * Deletes all steps for a recipe.
     */
    suspend fun deleteInstructionStepsForRecipe(recipeId: Long)

    /**
     * Reorders all instruction steps for a recipe using strict mode.
     *
     * Strict mode rule:
     * - [orderedStepIds] must match the exact set of existing step ids for [recipeId]
     * - no missing ids
     * - no extra ids
     *
     * Final persisted positions are normalized to 0..N-1 in the provided order.
     */
    suspend fun reorderInstructionSteps(
        recipeId: Long,
        orderedStepIds: List<Long>
    )

    /**
     * Moves a step one position earlier within its recipe, if possible.
     *
     * Strict rules:
     * - [stepId] must exist
     * - [stepId] must belong to [recipeId]
     *
     * No-op when the step is already the first step.
     */
    suspend fun moveInstructionStepUp(
        recipeId: Long,
        stepId: Long
    )

    /**
     * Moves a step one position later within its recipe, if possible.
     *
     * Strict rules:
     * - [stepId] must exist
     * - [stepId] must belong to [recipeId]
     *
     * No-op when the step is already the last step.
     */
    suspend fun moveInstructionStepDown(
        recipeId: Long,
        stepId: Long
    )
}