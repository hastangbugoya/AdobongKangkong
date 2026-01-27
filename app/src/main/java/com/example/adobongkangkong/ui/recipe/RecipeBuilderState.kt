package com.example.adobongkangkong.ui.recipe

import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.RecipeMacroPreview
import com.example.adobongkangkong.ui.common.bottomsheet.BlockingSheetModel

data class RecipeIngredientUi(
    val foodId: Long,
    val foodName: String,
    val servings: Double
)

data class RecipeBuilderState(
    val name: String = "",
    val servingsYield: Double = 4.0,
    val query: String = "",
    val results: List<Food> = emptyList(),
    val pickedFood: Food? = null,
    val pickedServings: Double = 1.0,
    val pickedServingsText: String = "",
    val pickedGrams: Double? = null,
    val ingredients: List<RecipeIngredientUi> = emptyList(),

    // ✅ add default
    val totalYieldGrams: Double? = null,

    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val preview: RecipeMacroPreview = RecipeMacroPreview(),
    val blockingSheet: BlockingSheetModel? = null,
    val blockedFoodId: Long? = null,
    val navigateToEditFoodId: Long? = null,
)

