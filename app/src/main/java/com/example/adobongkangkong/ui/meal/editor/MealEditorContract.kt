package com.example.adobongkangkong.ui.meal.editor

import kotlinx.coroutines.flow.StateFlow
/**
 * Shared MVVM-friendly contract for MealEditorScreen.
 *
 * Implemented by:
 * - PlannedMealEditorViewModel
 * - MealTemplateEditorViewModel
 *
 * UI depends ONLY on this contract (state + events).
 */
interface MealEditorContract {

    val state: StateFlow<MealEditorUiState>

    fun setName(name: String)

    fun addFood(foodId: Long)

    fun updateServings(itemId: Long, servings: Double?)

    fun removeItem(itemId: Long)

    fun moveItem(fromIndex: Int, toIndex: Int)

    fun save()
    fun updateServings(lineId: String, servingsText: String)

    fun removeItem(lineId: String)

    fun updateGrams(lineId: String, grams: String)

    fun updateMilliliters(lineId: String, ml: String)
}