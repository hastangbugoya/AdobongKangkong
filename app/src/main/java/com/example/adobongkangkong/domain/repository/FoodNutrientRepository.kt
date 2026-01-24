package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.domain.model.RecipeMacroPreview

/**
 * Domain-facing nutrient access. Keeps ViewModels away from Room/DAOs.
 */
interface FoodNutrientRepository {

    /**
     * Compute macro totals for a set of ingredients expressed in ingredient servings.
     *
     * @param ingredients list of (foodId, ingredientServings)
     * @return batch totals (not per-serving)
     */
    suspend fun computeRecipeMacroPreview(
        ingredients: List<Pair<Long, Double>>
    ): RecipeMacroPreview
}