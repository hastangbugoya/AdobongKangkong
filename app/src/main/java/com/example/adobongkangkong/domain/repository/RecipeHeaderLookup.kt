package com.example.adobongkangkong.domain.repository

interface RecipeHeaderLookup {
    suspend fun getRecipeHeaderByFoodId(foodId: Long): RecipeHeader?
}