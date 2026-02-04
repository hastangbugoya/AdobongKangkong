package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodNutrientRow
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.repository.FoodNutrientRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
import javax.inject.Inject

class SaveFoodWithNutrientsUseCase @Inject constructor(
    private val foods: FoodRepository,
    private val foodNutrients: FoodNutrientRepository
) {

    suspend operator fun invoke(
        food: Food,
        rows: List<FoodNutrientRow>
    ): Long {

        val foodId = foods.upsert(food)

        val gramsPerUnit = food.gramsPerServingUnit?.takeIf { it > 0.0 }

        val resultRows = buildList {

            rows.forEach { row ->

                // 1) Always persist the row as-is
                add(row)

                // 2) Derive PER_100G when possible and meaningful
                if (
                    row.basisType == BasisType.USDA_REPORTED_SERVING &&
                    gramsPerUnit != null
                ) {
                    val factor = 100.0 / gramsPerUnit

                    add(
                        row.copy(
                            basisType = BasisType.PER_100G,
                            amount = row.amount * factor,
                            basisGrams = 100.0
                        )
                    )
                }

                // 3) Derive PER_100ML ONLY if the row itself is ML-based
                if (
                    row.basisType == BasisType.USDA_REPORTED_SERVING &&
                    food.servingUnit == ServingUnit.ML
                ) {
                    val factor = 100.0 / food.servingSize

                    add(
                        row.copy(
                            basisType = BasisType.PER_100ML,
                            amount = row.amount * factor,
                            basisGrams = 100.0
                        )
                    )
                }
            }
        }

        foodNutrients.replaceForFood(foodId, resultRows)
        return foodId
    }
}

