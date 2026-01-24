package com.example.adobongkangkong.domain.repository


import com.example.adobongkangkong.domain.model.RecipeDraft

interface RecipeRepository {
    suspend fun createRecipe(draft: RecipeDraft): Long // returns recipeId
}