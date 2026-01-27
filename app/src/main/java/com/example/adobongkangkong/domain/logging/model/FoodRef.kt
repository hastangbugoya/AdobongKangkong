package com.example.adobongkangkong.domain.logging.model

sealed interface FoodRef {
    data class Food(
        val foodId: Long,
        val stableId: String,
        val displayName: String,
        // per-serving nutrients (or per-100g if you use that; adapt in the usecase)
        val perServingNutrientsJson: String
    ) : FoodRef

    data class Recipe(
        val recipeId: Long,
        val stableId: String,
        val displayName: String,
        val servingsYieldDefault: Double
    ) : FoodRef
}
