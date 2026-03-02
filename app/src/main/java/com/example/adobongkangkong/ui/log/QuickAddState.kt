package com.example.adobongkangkong.ui.log

import com.example.adobongkangkong.domain.logging.model.BatchSummary
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.ui.food.FoodListItemUiModel

enum class InputMode {
    SERVINGS,
    SERVING_UNIT,   // tbsp / cup / ml etc
    GRAMS
}

data class QuickAddState(
    val query: String = "",
    val results: List<FoodListItemUiModel> = emptyList(),

    val selectedFood: Food? = null,

    // canonical stepper amount (servings UI)
    val servings: Double = 1.0,

    // Derived: serving-equivalent computed from grams when possible
    val servingsEquivalent: Double? = null,

    // User input for "Amount (UNIT)"
    val inputUnit: ServingUnit = ServingUnit.G,
    val inputAmount: Double? = null,

    // derived UI values
    val servingUnitAmount: Double? = null,
    val gramsAmount: Double? = null,

    val inputMode: InputMode = InputMode.SERVINGS,

    // Recipe batch context (only relevant if selectedFood.isRecipe == true)
    val batches: List<BatchSummary> = emptyList(),
    val selectedBatchId: Long? = null,

    // Create-batch dialog
    val yieldGramsText: String = "",
    val servingsYieldText: String = "",
    val isCreateBatchDialogOpen: Boolean = false,

    val isSaving: Boolean = false,
    val errorMessage: String? = null,

    val isResolveMassDialogOpen: Boolean = false,
    val gramsPerServingText: String = "",

    // Barcode scan UI state
    val isScannerOpen: Boolean = false,
    val foundBarcodeDialogFood: Food? = null,
    val foundBarcodeDialogBarcode: String? = null,
    val notFoundBarcodeDialogBarcode: String? = null,
)