package com.example.adobongkangkong.ui.log

import com.example.adobongkangkong.domain.logging.model.BatchSummary
import com.example.adobongkangkong.domain.model.Food
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

    // canonical amount (we keep this as servings; grams/unit are derived + can “drive” servings)
    val servings: Double = 1.0,

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
    val errorMessage: String? = null
)
