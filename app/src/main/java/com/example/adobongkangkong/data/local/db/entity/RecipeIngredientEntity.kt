package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity

@Entity(
    tableName = "recipe_ingredients",
    primaryKeys = ["recipeId", "ingredientFoodId"]
)
data class RecipeIngredientEntity(
    val recipeId: Long,
    val ingredientFoodId: Long,

    // canonical: ingredients stored in SERVINGS of that ingredient
    val ingredientServings: Double
)

