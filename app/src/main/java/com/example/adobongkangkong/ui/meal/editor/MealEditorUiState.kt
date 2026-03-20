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
 * - Derived nutrition preview fields are UI-state only. They must be recomputed by the owning VM
 *   from the same quantity-resolution / scaling rules used by save flows, and never treated as a
 *   persisted source of truth.
 * - Critical nutrient preview fields are intentionally additive and optional. Current UI may ignore
 *   them, but VMs can populate them now so future UI work does not require reshaping editor state.
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

    /**
     * Existing live totals used by the shared meal editor UI.
     * Keep these for backward compatibility.
     */
    val liveMacroTotals: MacroTotals? = null,
    val liveMacroSummaryLine: String? = null,

    /**
     * Richer future-safe derived preview fields.
     *
     * These are intended to support:
     * - per-screen meal total summaries
     * - future critical nutrient surfacing
     * - explicit unknown vs zero handling
     */
    val mealMacroPreview: MacroPreview? = null,
    val mealEffectiveQuantityText: String? = null,
    val criticalNutrientTotals: List<CriticalNutrientPreview> = emptyList(),
    val hasUnknownCriticalNutrients: Boolean = false
) {
    data class Item(
        val lineId: String,
        val id: Long?,
        val foodId: Long,
        val foodName: String,
        val servings: String,
        val grams: Double? = null,
        val milliliters: Double? = null,

        /**
         * Derived preview-only fields.
         *
         * These should be rebuilt by the VM after any edit to servings / grams / milliliters or
         * any structural list mutation (add/remove/reorder/reload).
         */
        val effectiveQuantityText: String? = null,
        val macroPreview: MacroPreview? = null,
        val macroSummaryLine: String? = null,
        val criticalNutrients: List<CriticalNutrientPreview> = emptyList(),
        val hasUnknownCriticalNutrients: Boolean = false
    )

    /**
     * Lightweight display-oriented macro preview.
     *
     * Keep nullable so the VM can represent incomplete or unavailable data without lying by
     * coercing unknown values to zero.
     */
    data class MacroPreview(
        val caloriesKcal: Double? = null,
        val proteinG: Double? = null,
        val carbsG: Double? = null,
        val fatG: Double? = null
    ) {
        companion object {
            val Empty = MacroPreview(
                caloriesKcal = null,
                proteinG = null,
                carbsG = null,
                fatG = null
            )
        }
    }

    /**
     * VM-facing critical nutrient preview model.
     *
     * Notes:
     * - nutrientId is the stable join key for future UI and domain wiring.
     * - value may be null when not derivable from the current food data.
     * - isMissing means the nutrient is expected/important but not available from the source food.
     * - isEstimated is reserved for future bridge/derived math if needed.
     */
    data class CriticalNutrientPreview(
        val nutrientId: Long,
        val nutrientName: String,
        val unitName: String? = null,
        val value: Double? = null,
        val isMissing: Boolean = false,
        val isEstimated: Boolean = false
    )
}

/**
 * Bottom KDoc for future AI assistant.
 *
 * The template editor reuses this shared state object. Template-specific additions must stay
 * additive and nullable so planned-meal editor compile/runtime behavior does not regress.
 *
 * Derived preview fields added here are intentionally generic enough to support:
 * - planned meal editor live per-item macro previews
 * - meal-level aggregate macro previews
 * - future critical nutrient awareness without immediate UI work
 *
 * IMPORTANT:
 * - Do not persist or serialize these preview fields as authoritative nutrition.
 * - The owning VM must recompute them from authoritative food data + current editor inputs.
 * - Unknown nutrient values must remain nullable so future UI can distinguish unknown from zero.
 */