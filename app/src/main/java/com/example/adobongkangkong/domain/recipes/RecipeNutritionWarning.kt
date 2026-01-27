package com.example.adobongkangkong.domain.recipes

sealed interface RecipeNutritionWarning {

    data object MissingServingsYield : RecipeNutritionWarning
    data object MissingTotalYieldGrams : RecipeNutritionWarning

    data class InvalidServingsYield(val value: Double) : RecipeNutritionWarning
    data class InvalidTotalYieldGrams(val value: Double) : RecipeNutritionWarning

    data class MissingFood(val foodId: Long) : RecipeNutritionWarning

    data class MissingGramsPerServing(val foodId: Long) : RecipeNutritionWarning
    data class MissingNutrientsPerGram(val foodId: Long) : RecipeNutritionWarning

    data class IngredientServingsNonPositive(
        val foodId: Long,
        val servings: Double
    ) : RecipeNutritionWarning
}
