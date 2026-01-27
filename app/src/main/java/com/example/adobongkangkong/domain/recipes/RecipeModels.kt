package com.example.adobongkangkong.domain.recipes

data class Recipe(
    val id: Long,
    val name: String,
    val ingredients: List<RecipeIngredient>,
    val servingsYield: Double?,       // UX hint; required for per-serving + weight logging
    val totalYieldGrams: Double?      // cooked final weight; required for per-cooked-gram
)

data class RecipeIngredient(
    val foodId: Long,
    val servings: Double
)
