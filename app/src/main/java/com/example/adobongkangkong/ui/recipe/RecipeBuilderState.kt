package com.example.adobongkangkong.ui.recipe

import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.RecipeMacroPreview
import com.example.adobongkangkong.ui.common.bottomsheet.BlockingSheetModel
import com.example.adobongkangkong.ui.food.editor.FoodCategoryUi
import com.example.adobongkangkong.ui.food.editor.NutrientRowUi

data class RecipeIngredientUi(
    val foodId: Long,
    val foodName: String,

    /** Quantity expressed in the food's serving unit (e.g., 1.5 "can", 0.5 "cup"). */
    val servings: Double?,

    /** Human-readable serving unit label (e.g., "can", "cup"). Optional for legacy rows. */
    val servingUnitLabel: String? = null,

    /** Convenience display weight used by the recipe UI. */
    val grams: Double? = null,

    /** Convenience display volume used by the recipe UI when available. */
    val milliliters: Double? = null,

    /** True when the displayed grams were approximated using a fallback path. */
    val isApproximateWeight: Boolean = false,

    /** What the user actually entered (for reminder + edit UX). */
    val enteredAmount: Double? = null,
    val enteredUnitLabel: String? = null,

    /**
     * Estimated ingredient line cost already computed in ViewModel/use case layer.
     *
     * Null means:
     * - no known normalized price exists
     * - or the ingredient amount could not be resolved honestly to the needed basis
     */
    val estimatedLineCost: Double? = null,

    /**
     * UI-ready estimated ingredient line cost string.
     *
     * Example:
     * - "~ $2.50"
     */
    val estimatedLineCostDisplay: String? = null
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

    /**
     * Picked-food pricing preview.
     *
     * These are display-ready strings so the screen can render them directly
     * without redoing math.
     *
     * Examples:
     * - "~ $0.55 / 100g"
     * - "~ $0.40 / 100mL"
     * - "~ $2.50 / serving"
     * - "~ $2.50 for this ingredient"
     */
    val pickedNormalizedPriceDisplay: String? = null,
    val pickedServingPriceDisplay: String? = null,
    val pickedIngredientLineCostDisplay: String? = null,

    // Ingredients list
    val ingredients: List<RecipeIngredientUi> = emptyList(),

    // Existing total-yield support
    val totalYieldGrams: Double? = null,

    val categories: List<FoodCategoryUi> = emptyList(),
    val selectedCategoryIds: Set<Long> = emptySet(),
    val newCategoryName: String = "",

    // UI state
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val preview: RecipeMacroPreview = RecipeMacroPreview(),
    val blockingSheet: BlockingSheetModel? = null,
    val blockedFoodId: Long? = null,
    val navigateToEditFoodId: Long? = null,

    // Monitor user changes
    val hasUnsavedChanges: Boolean = false,

    val favorite: Boolean = false,
    val eatMore: Boolean = false,
    val limit: Boolean = false,

    // Nutrient tally (read-only, computed on ingredient add/remove)
    val nutrientTallyRows: List<NutrientRowUi> = emptyList(),
    val nutrientTallyLoading: Boolean = false,
    val nutrientTallyErrorMessage: String? = null,

    /**
     * Whole-recipe estimated cost.
     *
     * Null means:
     * - no ingredient line costs are currently available
     * - or none could be resolved honestly from normalized pricing
     */
    val estimatedRecipeTotalCost: Double? = null,

    /**
     * UI-ready whole-recipe estimated total cost string.
     *
     * Example:
     * - "~ $12.80"
     */
    val estimatedRecipeTotalCostDisplay: String? = null,
)