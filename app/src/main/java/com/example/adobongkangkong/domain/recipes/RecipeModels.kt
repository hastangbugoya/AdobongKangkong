package com.example.adobongkangkong.domain.recipes

import com.example.adobongkangkong.data.local.db.entity.RecipeEntity

data class Recipe(
    val id: Long,
    val name: String,
    val ingredients: List<RecipeIngredient> = emptyList<RecipeIngredient>(),
    val servingsYield: Double?,       // UX hint; required for per-serving + weight logging
    val totalYieldGrams: Double?      // cooked final weight; required for per-cooked-gram
)

data class RecipeIngredient(
    val foodId: Long,
    val servings: Double
)

fun RecipeEntity.toDomainRecipe(
    ingredients: List<RecipeIngredient> = emptyList<RecipeIngredient>()
): Recipe {
    return Recipe(
        id = id,
        name = name,
        ingredients = ingredients,
        servingsYield = servingsYield,
        totalYieldGrams = totalYieldGrams,
    )
}