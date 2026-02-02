package com.example.adobongkangkong.ui.food.editor

import com.example.adobongkangkong.domain.model.NutrientCategory
import com.example.adobongkangkong.domain.model.NutrientUnit
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.requiresGramsPerServing

data class NutrientRowUi(
    val nutrientId: Long,
    val name: String,
    val aliases: List<String> = emptyList(),
    val unit: NutrientUnit,
    val category: NutrientCategory,
    val amount: String // keep as String for text field editing
)

data class FoodEditorState(
    val foodId: Long? = null,
    val stableId: String? = null,
    val name: String = "",
    val brand: String = "",
    val servingSize: String = "1.0",
    val servingUnit: ServingUnit = ServingUnit.G,
    val gramsPerServing: String = "",
    val servingsPerPackage: String = "",

    val nutrientRows: List<NutrientRowUi> = emptyList(),

    val nutrientSearchQuery: String = "",
    val nutrientSearchResults: List<NutrientSearchResultUi> = emptyList(),

    val isSaving: Boolean = false,
    val errorMessage: String? = null,

    // Navigate-away warning
    val hasUnsavedChanges: Boolean = false,

    // Flags (stored separately from FoodEntity)
    val favorite: Boolean = false,
    val eatMore: Boolean = false,
    val limit: Boolean = false,
    val isLbDialogOpen: Boolean = false,
    val lbInputText: String = "",
) {
    /**
     * Convenience flags/fields used by FoodEditorScreen.
     *
     * These are derived from the canonical fields above (especially nutrientRows) so the UI can
     * keep simple one-field text inputs for common nutrients while your data model stays generic.
     *
     * IMPORTANT:
     * - These are read-only views over nutrientRows.
     * - Updates must be handled by the ViewModel by modifying nutrientRows (typically by nutrientId).
     */
    val isEditing: Boolean get() = foodId != null

    // UI convenience nutrient fields (Strings for TextField values)
    val calories: String get() = amountForAnyName("Calories", "Energy", "kcal")
    val carbs: String get() = amountForAnyName("Carbohydrate", "Carbs", "Total Carbohydrate")
    val protein: String get() = amountForAnyName("Protein")
    val fat: String get() = amountForAnyName("Fat", "Total Fat")
    val fiber: String get() = amountForAnyName("Fiber", "Dietary Fiber")
    val sodium: String get() = amountForAnyName("Sodium")

    /**
     * Minimal "Save enabled" rule for the screen.
     *
     * The strict validation belongs in the ViewModel / domain, but the bottom bar needs a quick
     * signal to enable/disable the Save button.
     */
    val canSave: Boolean
        get() {
            if (isSaving) return false
            if (name.isBlank()) return false

            // If the unit is volume-like, grams-per-serving is required for deterministic math.
            if (servingUnit.requiresGramsPerServing() && gramsPerServing.trim().isEmpty()) return false

            // servingSize should be a positive number if present.
            val servingSizeValue = servingSize.toDoubleOrNull()
            return !(servingSizeValue != null && servingSizeValue <= 0.0)
        }

    private fun amountForAnyName(vararg candidates: String): String {
        if (nutrientRows.isEmpty()) return ""
        val lower = candidates.map { it.trim().lowercase() }
        return nutrientRows.firstOrNull { row ->
            val n = row.name.trim().lowercase()
            lower.any { it == n || n.contains(it) }
        }?.amount.orEmpty()
    }
}

data class NutrientSearchResultUi(
    val id: Long,
    val name: String,
    val unit: NutrientUnit,
    val category: NutrientCategory,
    val aliases: List<String> = emptyList<String>()
)
