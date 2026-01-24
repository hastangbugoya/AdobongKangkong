package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodNutrientRow
import com.example.adobongkangkong.domain.repository.FoodNutrientRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
import javax.inject.Inject

data class FoodEditorData(
    val food: Food?,
    val nutrients: List<FoodNutrientRow>
)

class GetFoodEditorDataUseCase @Inject constructor(
    private val foods: FoodRepository,
    private val foodNutrients: FoodNutrientRepository
) {
    suspend operator fun invoke(foodId: Long?): FoodEditorData {
        if (foodId == null) return FoodEditorData(food = null, nutrients = emptyList())
        val food = foods.getById(foodId)
        val rows = foodNutrients.getForFood(foodId)
        return FoodEditorData(food = food, nutrients = rows)
    }
}
