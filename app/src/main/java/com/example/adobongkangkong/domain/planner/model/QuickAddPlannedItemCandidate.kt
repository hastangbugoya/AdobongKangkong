package com.example.adobongkangkong.domain.planner.model

import com.example.adobongkangkong.data.local.db.entity.MealSlot

/**
 * Represents a selectable planner item that can prefill Quick Add.
 *
 * This preserves planner intent while allowing Quick Add to decide
 * how logging should proceed.
 *
 * FOOD → direct log
 * RECIPE → recipe estimate log
 * RECIPE_BATCH → batch-anchored log
 *
 * Meal context is included so the Quick Add "Today Plan" picker can group items by
 * planned meal and offer a meal-level action, e.g. "Log whole meal".
 */
data class QuickAddPlannedItemCandidate(
    /**
     * Planned item id.
     */
    val id: Long,

    /**
     * Parent planned meal id.
     *
     * This is optional for compatibility with any older call sites, but Today Plan picker
     * candidates should provide it so the UI can log the whole meal.
     */
    val plannedMealId: Long? = null,

    /**
     * Parent planned meal title, if available.
     *
     * Used only for display/grouping in picker UI.
     */
    val plannedMealTitle: String? = null,

    val title: String,
    val slot: MealSlot,
    val type: Type,

    val foodId: Long? = null,
    val recipeId: Long? = null,
    val batchId: Long? = null,

    /**
     * Selected recipe variant for recipe-backed planned items.
     *
     * This lets item-level Quick Add preserve planner variant intent instead of silently
     * falling back to the base recipe.
     */
    val recipeVariantId: Long? = null,

    val plannedServings: Double? = null,
    val plannedGrams: Double? = null
) {

    enum class Type {
        FOOD,
        RECIPE,
        RECIPE_BATCH
    }
}