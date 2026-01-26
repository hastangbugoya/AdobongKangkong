package com.example.adobongkangkong.domain.recipes

/**
 * What the user is logging for a recipe.
 *
 * - BY_COOKED_GRAMS: user logs a weight of the cooked recipe.
 * - BY_SERVINGS: user logs servings (UX hint).
 */
sealed interface RecipeLogInput {
    data class ByCookedGrams(val grams: Double) : RecipeLogInput
    data class ByServings(val servings: Double) : RecipeLogInput
}