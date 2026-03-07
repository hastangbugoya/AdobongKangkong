package com.example.adobongkangkong.ui.meal.editor

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.model.MacroTotals

/**
 * Shared editor UI state used by planned meal editor and meal template editor.
 *
 * For developers:
 * - Keep shared fields generic so [MealEditorScreen] remains reusable.
 * - Template-only fields must be nullable / optional so planned-meal flows are unaffected.
 * - Macro guidance is advisory only.
 */
enum class MealEditorMode { PLANNED, TEMPLATE }

data class MealEditorUiState(
    val mealId: Long?,
    val name: String = "",
    val mode: MealEditorMode,
    val subtitle: String? = null,
    val items: List<Item> = emptyList(),
    val isSaving: Boolean = false,
    val canSave: Boolean = true,
    val errorMessage: String? = null,
    val isDirty: Boolean = false,
    val warnings: List<String> = emptyList(),
    val templateDefaultSlot: MealSlot? = null,
    val liveMacroTotals: MacroTotals? = null,
    val liveMacroSummaryLine: String? = null
) {
    data class Item(
        val lineId: String,
        val id: Long?,
        val foodId: Long,
        val foodName: String,
        val servings: String,
        val grams: Double? = null,
        val milliliters: Double? = null
    )
}

/**
 * Bottom KDoc for future AI assistant.
 *
 * The template editor reuses this shared state object. Template-specific additions must stay
 * additive and nullable so planned-meal editor compile/runtime behavior does not regress.
 */
