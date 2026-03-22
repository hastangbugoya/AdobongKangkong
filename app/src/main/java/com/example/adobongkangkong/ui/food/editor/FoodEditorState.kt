package com.example.adobongkangkong.ui.food.editor

import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.domain.importing.Decision
import com.example.adobongkangkong.domain.model.NutrientCategory
import com.example.adobongkangkong.domain.model.NutrientUnit
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.usda.model.BarcodeRemapDialogState
import com.example.adobongkangkong.domain.usda.model.CollisionReason

enum class GroundingMode {
    SOLID,
    LIQUID
}

enum class UsdaNutrientInterpretationChoice {
    PER_100,
    PER_SERVING
}

data class NutrientRowUi(
    val nutrientId: Long,
    val code: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val unit: NutrientUnit,
    val category: NutrientCategory,
    val amount: String
)

data class AssignedBarcodeUi(
    val barcode: String,
    val source: BarcodeMappingSource,
    val overrideServingsPerPackage: Double? = null,
    val overrideHouseholdServingText: String? = null,
    val overrideServingSize: Double? = null,
    val overrideServingUnit: ServingUnit? = null,
)

data class BarcodePackageEditorState(
    val barcode: String,
    val overrideServingsPerPackage: String = "",
    val overrideHouseholdServingText: String = "",
    val overrideServingSize: String = "",
    val overrideServingUnit: ServingUnit? = null,
)

data class PendingUsdaBackfillPromptState(
    val barcode: String,
    val selectedFdcId: Long,
    val candidateLabel: String,
)

data class PendingUsdaInterpretationPromptState(
    val foodId: Long,
    val selectedFdcId: Long,
    val candidateLabel: String,
    val servingText: String?,
    val calories: Double?,
    val carbs: Double?,
    val protein: Double?,
    val fat: Double?,
)

data class UsdaBackfillMessageState(
    val message: String,
    val insertedCount: Int,
    val skippedExistingCount: Int,
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

    val isFoodMetadataDirty: Boolean = false,
    val areNutrientsDirty: Boolean = false,
    val isBasisInterpretationDirty: Boolean = false,

    val hasUnsavedChanges: Boolean = false,

    val favorite: Boolean = false,
    val eatMore: Boolean = false,
    val limit: Boolean = false,
    val isLbDialogOpen: Boolean = false,
    val lbInputText: String = "",

    val scannedBarcode: String = "",
    val isBarcodeScannerOpen: Boolean = false,
    val pendingUsdaSearchJson: String? = null,
    val barcodePickItems: List<com.example.adobongkangkong.domain.usda.SearchUsdaFoodsByBarcodeUseCase.PickItem> = emptyList(),

    val pendingUsdaInterpretationPrompt: PendingUsdaInterpretationPromptState? = null,
    val pendingUsdaBackfillPrompt: PendingUsdaBackfillPromptState? = null,
    val usdaBackfillMessage: UsdaBackfillMessageState? = null,

    val isGroundingDialogOpen: Boolean = false,
    val groundingMode: GroundingMode = GroundingMode.SOLID,
    val originalServingUnit: ServingUnit? = null,
    val basisType: BasisType? = null,

    val isBarcodeFallbackOpen: Boolean = false,
    val barcodeFallbackMessage: String? = null,
    val barcodeFallbackCreateName: String = "",
    val barcodeAlreadyAssignedFoodId: Long? = null,

    val barcodeRemapDialog: BarcodeRemapDialogState? = null,
    val barcodeCollisionDialog: BarcodeCollisionDialogState? = null,

    val assignedBarcodes: List<AssignedBarcodeUi> = emptyList(),
    val barcodeActionMessage: String? = null,
    val barcodePackageEditor: BarcodePackageEditorState? = null,

    val hasLoaded: Boolean = false,

    val needsFix: Boolean = false,
    val fixMessage: String? = null,
    val fixBannerDismissed: Boolean = false,
) {
    val isEditing: Boolean
        get() = foodId != null

    val calories: String
        get() = amountForAnyName("Calories", "Energy", "kcal")

    val carbs: String
        get() = amountForAnyName("Carbohydrate", "Carbs", "Total Carbohydrate")

    val protein: String
        get() = amountForAnyName("Protein")

    val fat: String
        get() = amountForAnyName("Fat", "Total Fat")

    val fiber: String
        get() = amountForAnyName("Fiber", "Dietary Fiber")

    val sodium: String
        get() = amountForAnyName("Sodium")

    val canSave: Boolean
        get() {
            if (isSaving) return false
            if (name.isBlank()) return false

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
    val code: String,
    val name: String,
    val unit: NutrientUnit,
    val category: NutrientCategory,
    val aliases: List<String> = emptyList()
)

data class BarcodeCollisionDialogState(
    val barcode: String,
    val existingFoodId: Long,
    val existingSource: BarcodeMappingSource,
    val incomingFdcId: Long?,
    val incomingPublishedDateIso: String?,
    val incomingLabel: String,
    val reason: CollisionReason,
)