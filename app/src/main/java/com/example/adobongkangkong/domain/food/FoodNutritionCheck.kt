package com.example.adobongkangkong.domain.food

sealed interface FoodNutritionCheck {
    data object Ok : FoodNutritionCheck

    data class Blocked(
        val reason: Reason,
        val message: String
    ) : FoodNutritionCheck

    enum class Reason {
        MissingNutrients,
        MissingEnergy,
        MissingMacros
    }
}

enum class NutritionUsageContext {
    LOGGING,   // logging food/batch into a day
    RECIPE     // using as an ingredient in a recipe
}
