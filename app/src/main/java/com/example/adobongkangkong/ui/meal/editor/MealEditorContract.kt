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

    fun updateServings(lineId: String, servingsText: String)

    fun updateGrams(lineId: String, gramsText: String)

    fun updateMilliliters(lineId: String, mlText: String)

    fun removeItem(lineId: String)

    fun moveItem(fromIndex: Int, toIndex: Int)

    fun save()

    fun discardChanges()
}