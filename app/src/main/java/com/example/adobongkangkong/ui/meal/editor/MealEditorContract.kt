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
    fun addFood(foodId: Long)
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
 */
