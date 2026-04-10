import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.domain.importing.Decision
import com.example.adobongkangkong.domain.model.NutrientCategory
import com.example.adobongkangkong.domain.model.NutrientUnit
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.nutrition.ServingResolution
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

enum class StoreEditorMode {
    CREATE,
    EDIT
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

/**
 * UI-only store editor state for the first-pass food-editor-managed store CRUD.
 *
 * Persistence contract for this pass:
 * - Only `name` is real persisted data.
 * - `previewAddress` and `previewContact` are dummy preview-only fields so we can
 *   see what a future dedicated store editor may feel like.
 * - Do not treat preview fields as saved data until the DB/schema explicitly adds them.
 */
data class StoreEditorState(
    val mode: StoreEditorMode,
    val storeId: Long? = null,
    val originalName: String = "",
    val name: String = "",
    val previewAddress: String = "",
    val previewContact: String = "",
    val showDeleteConfirmation: Boolean = false,
) {
    val title: String
        get() = when (mode) {
            StoreEditorMode.CREATE -> "New store"
            StoreEditorMode.EDIT -> "Edit store"
        }

    val confirmButtonLabel: String
        get() = when (mode) {
            StoreEditorMode.CREATE -> "Create"
            StoreEditorMode.EDIT -> "Save"
        }

    val canDelete: Boolean
        get() = mode == StoreEditorMode.EDIT && storeId != null

    val trimmedName: String
        get() = name.trim()

    val canConfirm: Boolean
        get() = trimmedName.isNotBlank()
}

/**
 * Explicit serving draft used by the new bulk-math editor flow.
 *
 * IMPORTANT:
 * - This is layered on top of the legacy top-level serving fields for now.
 * - We intentionally keep both during the migration to avoid breaking existing UI/VM code all at once.
 * - Once the editor/ViewModel fully migrates, the duplicated legacy fields can be cleaned up later.
 */
data class ServingDraftState(
    val servingSize: String = "1.0",
    val servingUnit: ServingUnit = ServingUnit.SERVING,
    val gramsPerServingUnit: String = "",
    val mlPerServingUnit: String = "",
    val servingsPerPackage: String = "",
)

/**
 * Explicit nutrient draft used by the new bulk recompute/apply workflow.
 *
 * For now this mirrors the editable nutrient rows already shown in the UI.
 * Later, the editor flow should treat this as:
 * - recompute target (canonical -> UI draft)
 * - apply source (UI draft -> canonical)
 */
data class NutrientDraftState(
    val rows: List<NutrientRowUi> = emptyList(),
)

/**
 * Explicit editor workflow/status flags for the new nutrition editing model.
 *
 * These flags are intentionally separate from the older generic dirty flags so we can migrate safely.
 */
data class NutritionEditorStatusState(
    val isServingDirty: Boolean = false,
    val isNutrientsDirty: Boolean = false,
    val hasPendingRecompute: Boolean = false,
    val hasPendingApply: Boolean = false,
    val showDiscardNutrientEditsDialog: Boolean = false,
    val servingResolution: ServingResolution? = null,
)

data class FoodEditorState(
    val foodId: Long? = null,
    val stableId: String? = null,
    val name: String = "",
    val brand: String = "",

    // -------------------------------------------------------------------------
    // Legacy top-level serving fields
    // Kept during migration so existing screen / VM code keeps compiling.
    // New code should gradually prefer servingDraft.
    // -------------------------------------------------------------------------
    val servingSize: String = "1.0",
    val servingUnit: ServingUnit = ServingUnit.SERVING,
    val gramsPerServingUnit: String = "",
    val mlPerServingUnit: String = "",
    val servingsPerPackage: String = "",

    // -------------------------------------------------------------------------
    // New explicit draft/state buckets
    // -------------------------------------------------------------------------
    val servingDraft: ServingDraftState = ServingDraftState(),
    val nutrientDraft: NutrientDraftState = NutrientDraftState(),
    val nutritionEditorStatus: NutritionEditorStatusState = NutritionEditorStatusState(),

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

    /**
     * Store editor state for first-pass store CRUD from the store-price sheet.
     *
     * Null means no editor is currently open.
     */
    val storeEditor: StoreEditorState? = null,

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

    /**
     * New explicit status accessors for the refactored editor flow.
     * Kept as convenience properties so screen/viewmodel wiring can migrate gradually.
     */
    val isServingDirty: Boolean
        get() = nutritionEditorStatus.isServingDirty

    val isNutrientsDirtyExplicit: Boolean
        get() = nutritionEditorStatus.isNutrientsDirty

    val hasPendingRecompute: Boolean
        get() = nutritionEditorStatus.hasPendingRecompute

    val hasPendingApply: Boolean
        get() = nutritionEditorStatus.hasPendingApply

    val showDiscardNutrientEditsDialog: Boolean
        get() = nutritionEditorStatus.showDiscardNutrientEditsDialog

    val servingResolution: ServingResolution?
        get() = nutritionEditorStatus.servingResolution

    /**
     * Migration-friendly aggregate status.
     *
     * During migration:
     * - old dirty flags still matter
     * - new explicit workflow flags also matter
     */
    val hasPendingNutritionWorkflowChanges: Boolean
        get() = isServingDirty || isNutrientsDirtyExplicit || hasPendingRecompute || hasPendingApply

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