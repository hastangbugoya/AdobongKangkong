package com.example.adobongkangkong.ui.recipe

import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.RecipeMacroPreview
import com.example.adobongkangkong.ui.common.bottomsheet.BlockingSheetModel
import com.example.adobongkangkong.ui.food.editor.NutrientRowUi

data class RecipeIngredientUi(
    val foodId: Long,
    val foodName: String,
    /** Quantity expressed in the food's serving unit (e.g., 1.5 "can", 0.5 "cup"). */
    val servings: Double,
    /** Human-readable serving unit label (e.g., "can", "cup"). Optional for legacy rows. */
    val servingUnitLabel: String? = null,
    /** Convenience display: servings converted to grams using grams-per-serving, if available. */
    val grams: Double? = null,

    /** What the user actually entered (for reminder + edit UX). */
    val enteredAmount: Double? = null,
    val enteredUnitLabel: String? = null
)


data class RecipeBuilderState(
    val name: String = "",
    val servingsYield: Double = 4.0,

    // Add ingredient
    val query: String = "",
    val results: List<Food> = emptyList(),
    val pickedFood: Food? = null,
    val pickedServings: Double = 1.0,
    val pickedServingsText: String = "",
    val pickedGramsText: String = "",
    val pickedGrams: Double? = null,

    // Ingredients list
    val ingredients: List<RecipeIngredientUi> = emptyList(),

    // ✅ add default
    val totalYieldGrams: Double? = null,

    // UI state
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val preview: RecipeMacroPreview = RecipeMacroPreview(),
    val blockingSheet: BlockingSheetModel? = null,
    val blockedFoodId: Long? = null,
    val navigateToEditFoodId: Long? = null,

    // monitor user changes values
    val hasUnsavedChanges: Boolean = false,

    val favorite: Boolean = false,
    val eatMore: Boolean = false,
    val limit: Boolean = false,

    // Nutrient tally (read-only, computed on ingredient add/remove)
    val nutrientTallyRows: List<NutrientRowUi> = emptyList(),
    val nutrientTallyLoading: Boolean = false,
    val nutrientTallyErrorMessage: String? = null,
)
