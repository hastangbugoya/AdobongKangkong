package com.example.adobongkangkong.domain.repository


import com.example.adobongkangkong.domain.model.RecipeDraft

data class RecipeHeader(
    val recipeId: Long,
    val foodId: Long,
    val servingsYield: Double
)

data class RecipeIngredientLine(
    val ingredientFoodId: Long,
    val ingredientServings: Double
)
interface RecipeRepository {
    suspend fun createRecipe(draft: RecipeDraft): Long // returns recipeId

    // Edit mode
    suspend fun getRecipeByFoodId(foodId: Long): RecipeHeader?
    suspend fun getIngredients(recipeId: Long): List<RecipeIngredientLine>

    suspend fun updateRecipeByFoodId(
        foodId: Long,
        servingsYield: Double,
        ingredients: List<RecipeIngredientLine>
    )
}