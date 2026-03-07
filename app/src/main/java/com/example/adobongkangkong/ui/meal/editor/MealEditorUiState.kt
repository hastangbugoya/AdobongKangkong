package com.example.adobongkangkong.ui.meal.editor

import com.example.adobongkangkong.domain.model.MacroTotals

/**
 * Shared UI state for both planned-meal editing and meal-template editing.
 *
 * ## For developers
 * Responsibilities:
 * - hold the current editable header fields and line items
 * - expose shared save/error/dirty flags used by [MealEditorScreen]
 * - optionally carry template-only live macro guidance without affecting planned-meal flows
 *
 * Conventions:
 * - `mealId` is reused as the route/entity id for both modes
 * - `liveMacroTotals` / `liveMacroSummaryLine` are nullable so planned-meal editor stays unchanged
 * - macro guidance is advisory only and should never be treated as validation failure
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
 * The nullable live-macro fields were added for template-editor Phase 3A so the shared editor UI
 * could render advisory totals without forking the screen/state model. Preserve that boundary:
 * - planned-meal editor can keep these null
 * - template editor can keep them updated from its in-memory draft
 * - do not repurpose them for hard validation
 */
