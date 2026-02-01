package com.example.adobongkangkong.ui.recipe.model

sealed interface MissingNutritionReason {
    data class IngredientMissingGramsPerServing(val foodId: Long) : MissingNutritionReason
    data class IngredientMissingNutrients(val foodId: Long) : MissingNutritionReason
}