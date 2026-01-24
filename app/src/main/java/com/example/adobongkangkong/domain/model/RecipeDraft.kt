package com.example.adobongkangkong.domain.model

data class RecipeDraft(
    val name: String,
    val servingsYield: Double,
    val ingredients: List<RecipeIngredientDraft>
)