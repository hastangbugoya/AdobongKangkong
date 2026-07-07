package com.example.adobongkangkong.ui.log

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.logging.model.BatchSummary
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.planner.model.QuickAddPlannedItemCandidate
import com.example.adobongkangkong.ui.food.FoodListItemUiModel

enum class InputMode {
    SERVINGS,
    SERVING_UNIT,
    GRAMS
}

enum class QuickAddMode {
    CREATE,
    EDIT
}

data class QuickAddNutrientCaution(
    val label: String,
    val amountText: String,
    val message: String
)

data class QuickAddRecipeVariantUi(
    val id: Long,
    val name: String,
    val notes: String? = null,
    val servingsYieldOverride: Double? = null
)

data class QuickAddState(
    val query: String = "",
    val results: List<FoodListItemUiModel> = emptyList(),

    val mode: QuickAddMode = QuickAddMode.CREATE,
    val editingLogId: Long? = null,
    val isIdentityLocked: Boolean = false,

    val selectedFood: Food? = null,

    val servings: Double = 1.0,
    val servingsEquivalent: Double? = null,

    val inputUnit: ServingUnit = ServingUnit.G,
    val inputAmount: Double? = null,

    val servingUnitAmount: Double? = null,
    val gramsAmount: Double? = null,

    val inputMode: InputMode = InputMode.SERVINGS,

    val nutrientCautions: List<QuickAddNutrientCaution> = emptyList(),

    val batches: List<BatchSummary> = emptyList(),
    val selectedBatchId: Long? = null,

    val recipeVariants: List<QuickAddRecipeVariantUi> = emptyList(),
    val selectedRecipeVariantId: Long? = null,

    /**
     * Active measured cooked yield for the selected recipe form.
     *
     * Recipe form means:
     * - selected base recipe when selectedRecipeVariantId is null
     * - selected recipe variant when selectedRecipeVariantId is non-null
     *
     * This value is used only as the visible basis for future gram-based recipe logging.
     * It is not cooked-batch inventory and does not mean a physical batch still exists.
     */
    val activeMeasuredYieldGrams: Double? = null,

    /**
     * Last time the active measured yield was entered or confirmed.
     *
     * UX rule:
     * Whenever the user has an option to use grams/weight to log a recipe or variant,
     * the UI must show this timestamp with the active measured yield.
     */
    val activeMeasuredYieldUpdatedAtEpochMs: Long? = null,

    /**
     * Optional note attached to the active measured yield, such as "large pot",
     * "air fryer", or "less water".
     */
    val activeMeasuredYieldNote: String? = null,

    /**
     * True when the selected recipe or recipe variant has an active measured yield
     * that can support gram-based logging.
     */
    val recipeGramLoggingAvailable: Boolean = false,

    val mealSlot: MealSlot? = null,

    val yieldGramsText: String = "",
    val servingsYieldText: String = "",
    val isCreateBatchDialogOpen: Boolean = false,

    val isSaving: Boolean = false,
    val errorMessage: String? = null,

    val isResolveMassDialogOpen: Boolean = false,
    val gramsPerServingText: String = "",

    val isNutritionChoiceDialogOpen: Boolean = false,
    val nutritionChoiceMessage: String? = null,

    val isScannerOpen: Boolean = false,
    val foundBarcodeDialogFood: Food? = null,
    val foundBarcodeDialogBarcode: String? = null,
    val notFoundBarcodeDialogBarcode: String? = null,

    val isIouDialogOpen: Boolean = false,
    val iouDescription: String = "",
    val iouCaloriesText: String = "",
    val iouProteinText: String = "",
    val iouCarbsText: String = "",
    val iouFatText: String = "",
    val isSavingIou: Boolean = false,
    val iouErrorMessage: String? = null,

    val isTodayPlanPickerOpen: Boolean = false,
    val todayPlanSections: Map<MealSlot, List<QuickAddPlannedItemCandidate>> = emptyMap(),
    val isTodayPlanLoading: Boolean = false,

    /**
     * Shown when the user tries to log a planned meal that has been logged before.
     *
     * Planned meals are logging templates. The app does not inspect or reconcile the
     * current Day Log rows after a planned meal is logged; this dialog only helps the
     * user avoid accidental double logging.
     */
    val isPlannedMealRelogDialogOpen: Boolean = false,
    val plannedMealRelogMessage: String? = null
)