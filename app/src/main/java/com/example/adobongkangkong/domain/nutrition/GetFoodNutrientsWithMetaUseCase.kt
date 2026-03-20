package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.data.local.db.dao.FoodNutrientDao
import com.example.adobongkangkong.data.local.db.dao.FoodNutrientWithMetaRow
import javax.inject.Inject

class GetFoodNutrientsWithMetaUseCase @Inject constructor(
    private val dao: FoodNutrientDao
) {
    suspend operator fun invoke(foodId: Long): List<FoodNutrientWithMetaRow> {
        return dao.getForFoodWithMeta(foodId)
    }
}