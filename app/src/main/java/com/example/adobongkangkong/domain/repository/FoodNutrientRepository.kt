package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.domain.model.RecipeMacroPreview

/**
 * Domain-facing nutrient access. Keeps ViewModels away from Room/DAOs.
 */
interface FoodNutrientRepository {

    suspend fun getForFood(foodId: Long): List<com.example.adobongkangkong.domain.model.FoodNutrientRow>

    suspend fun replaceForFood(
        foodId: Long,
        rows: List<com.example.adobongkangkong.domain.model.FoodNutrientRow>
    )

    suspend fun deleteOne(foodId: Long, nutrientId: Long)

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