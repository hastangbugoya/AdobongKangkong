package com.example.adobongkangkong.ui.meal.editor

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared MVVM-friendly contract for MealEditorScreen.
 *
 * Implemented by:
 * - PlannedMealEditorViewModel
 * - MealTemplateEditorViewModel
 */
interface MealEditorContract {
    val state: StateFlow<MealEditorUiState>

    fun setName(name: String)

    /**
     * Food-only add path.
     *
     * This remains the common path for both:
     * - planned meal editor
     * - meal template editor
     */
    fun addFood(foodId: Long)

    /**
     * Planned-meal recipe add path.
     *
     * Default no-op because meal templates are still food-shaped for now.
     *
     * recipeId is the RecipeEntity id, not the recipe FoodEntity id.
     * recipeVariantId == null means base recipe.
     */
    fun addRecipe(
        recipeId: Long,
        recipeVariantId: Long? = null,
    ) = Unit

    /**
     * Planned-meal recipe variant selector hook.
     *
     * Default no-op so template editor implementations do not need to care about
     * recipe variants until templates intentionally support recipe/variant items.
     */
    fun setRecipeVariant(
        lineId: String,
        recipeVariantId: Long?,
    ) = Unit

    fun updateServings(lineId: String, servingsText: String)
    fun updateGrams(lineId: String, gramsText: String)
    fun updateMilliliters(lineId: String, mlText: String)
    fun removeItem(lineId: String)
    fun moveItem(fromIndex: Int, toIndex: Int)
    fun save()
    fun discardChanges()

    /** Template-only hook. Planned-meal implementations can keep the default no-op. */
    fun setTemplateDefaultSlot(slot: MealSlot?) = Unit
}

/**
 * Bottom KDoc for future AI assistant.
 *
 * New template-only editor hooks should default to no-op here unless both editor modes require
 * them. That avoids forcing broad changes across planned-meal flows.
 *
 * Recipe / variant hooks are planned-meal-first and intentionally default to no-op so the shared
 * editor contract can evolve without forcing the meal template editor to become recipe-aware yet.
 */