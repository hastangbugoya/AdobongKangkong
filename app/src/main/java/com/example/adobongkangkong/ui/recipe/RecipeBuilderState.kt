package com.example.adobongkangkong.ui.recipe

import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.RecipeMacroPreview

data class RecipeIngredientUi(
    val foodId: Long,
    val foodName: String,
    val servings: Double
)

data class RecipeBuilderState(
    val name: String = "",
    val servingsYield: Double = 4.0,

    // add-ingredient flow
    val query: String = "",
    val results: List<Food> = emptyList(),
    val pickedFood: Food? = null,
    val pickedServings: Double = 1.0,
    val pickedGrams: Double? = null,

    val ingredients: List<RecipeIngredientUi> = emptyList(),

    val isSaving: Boolean = false,
    val errorMessage: String? = null,

    val preview: RecipeMacroPreview = RecipeMacroPreview()
)

