package com.example.adobongkangkong.ui.food.editor

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.domain.model.NutrientCategory
import com.example.adobongkangkong.domain.model.NutrientUnit
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.usda.model.BarcodeRemapDialogState

enum class GroundingMode {
    SOLID,
    LIQUID
}

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
    val servingUnit: ServingUnit = ServingUnit.SERVING,
    val gramsPerServingUnit: String = "",
    val mlPerServingUnit: String = "",
    val servingsPerPackage: String = "",

    val categories: List<FoodCategoryUi> = emptyList(),
    val selectedCategoryIds: Set<Long> = emptySet(),
    val newCategoryName: String = "",

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

    // Barcode scan (UI-only, not persisted)
    val scannedBarcode: String = "",
    val isBarcodeScannerOpen: Boolean = false,
    val pendingUsdaSearchJson: String? = null,
    val barcodePickItems: List<com.example.adobongkangkong.domain.usda.SearchUsdaFoodsByBarcodeUseCase.PickItem> = emptyList(),

    // Solid-vs-liquid grounding prompt (UI-only)
    val isGroundingDialogOpen: Boolean = false,
    val groundingMode: GroundingMode = GroundingMode.SOLID,
    val originalServingUnit: ServingUnit? = null,
    val basisType: BasisType? = null,

    // Barcode fallback (USDA lookup failed)
    val isBarcodeFallbackOpen: Boolean = false,
    val barcodeFallbackMessage: String? = null,
    val barcodeFallbackCreateName: String = "",
    val barcodeAlreadyAssignedFoodId: Long? = null,
    // Barcode remap confirm (when barcode already mapped to another food)
    val barcodeRemapDialog: BarcodeRemapDialogState? = null,

    // ✅ NEW: barcode collision prompt (Remap / Open Existing / Cancel)
    val barcodeCollisionDialog: BarcodeCollisionDialogState? = null,

    // Barcode mappings for this food (persisted)
    val assignedBarcodes: List<String> = emptyList(),
    val barcodeActionMessage: String? = null,

    val hasLoaded: Boolean = false,
    // ✅ NEW: non-blocking “Needs Fix” banner state (computed by ViewModel)
    val needsFix: Boolean = false,
    val fixMessage: String? = null,

    // ✅ Optional dismiss: hides banner until message changes (or becomes null)
    val fixBannerDismissed: Boolean = false,
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

            // We intentionally do NOT block save when a unit is ambiguous (cup/tbsp/fl oz/etc.)
            // because the ViewModel can prompt the user for SOLID vs LIQUID grounding.

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

data class FoodCategoryUi(
    val id: Long,
    val name: String,
    val isSystem: Boolean = false,
)


data class NutrientSearchResultUi(
    val id: Long,
    val name: String,
    val unit: NutrientUnit,
    val category: NutrientCategory,
    val aliases: List<String> = emptyList()
)