package com.example.adobongkangkong.domain.repository


import com.example.adobongkangkong.domain.model.RecipeDraft

data class RecipeHeader(
    val recipeId: Long,
    val foodId: Long,
    val servingsYield: Double,
    val totalYieldGrams: Double?
)

data class RecipeIngredientLine(
    val ingredientFoodId: Long,
    val ingredientServings: Double? = null,
    val ingredientGrams: Double? = null
)
interface RecipeRepository {
    suspend fun createRecipe(draft: RecipeDraft): Long // returns recipeId

    // Edit mode
    suspend fun getRecipeByFoodId(foodId: Long): RecipeHeader?
    suspend fun getIngredients(recipeId: Long): List<RecipeIngredientLine>

    suspend fun updateRecipeByFoodId(
        foodId: Long,
        servingsYield: Double,
        totalYieldGrams: Double?,
        ingredients: List<RecipeIngredientLine>
    )
}