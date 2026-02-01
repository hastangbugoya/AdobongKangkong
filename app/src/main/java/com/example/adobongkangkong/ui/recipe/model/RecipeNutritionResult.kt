package com.example.adobongkangkong.ui.recipe.model

data class RecipeNutritionDisplayModel(
    val totalYieldGrams: Double,                 // raw yield (sum ingredients)
    val totalNutrients: Map<Long, Double>,       // total batch nutrients
    val per100g: Map<Long, Double>,              // derived for display
    val missing: List<MissingNutritionReason>    // why incomplete
)

