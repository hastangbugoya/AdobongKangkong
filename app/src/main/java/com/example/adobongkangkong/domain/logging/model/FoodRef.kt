package com.example.adobongkangkong.domain.logging.model

sealed interface FoodRef {
    data class Food(
        val foodId: Long
    ) : FoodRef

    data class Recipe(
        val recipeId: Long,
        val stableId: String,
        val displayName: String,
        val servingsYieldDefault: Double
    ) : FoodRef
}
