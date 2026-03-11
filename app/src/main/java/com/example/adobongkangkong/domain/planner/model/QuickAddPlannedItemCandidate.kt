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
 */
data class QuickAddPlannedItemCandidate(
    val id: Long,
    val title: String,
    val slot: MealSlot,
    val type: Type,

    val foodId: Long? = null,
    val recipeId: Long? = null,
    val batchId: Long? = null,

    val plannedServings: Double? = null,
    val plannedGrams: Double? = null
) {

    enum class Type {
        FOOD,
        RECIPE,
        RECIPE_BATCH
    }
}