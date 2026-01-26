package com.example.adobongkangkong.domain.recipes

data class Recipe(
    val id: Long,
    val name: String,
    val ingredients: List<RecipeIngredient>,
    val servingsYield: Int?,       // UX hint; required for per-serving + weight logging
    val totalYieldGrams: Int?      // cooked final weight; required for per-cooked-gram
)

data class RecipeIngredient(
    val foodId: Long,
    val servings: Double
)
