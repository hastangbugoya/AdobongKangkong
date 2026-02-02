package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodNutrientRow
import com.example.adobongkangkong.domain.repository.FoodNutrientRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
import javax.inject.Inject

class SaveFoodWithNutrientsUseCase @Inject constructor(
    private val foods: FoodRepository,
    private val foodNutrients: FoodNutrientRepository
) {
    suspend operator fun invoke(food: Food, rows: List<FoodNutrientRow>): Long {
        val id = foods.upsert(food)

        val grams = food.gramsPerServing?.takeIf { it > 0.0 }

        val normalizedRows = rows.map { row ->
            when (row.basisType) {
                BasisType.PER_100G -> row

                BasisType.PER_SERVING -> {
                    if (grams == null) {
                        // Can't normalize yet; keep legacy until user provides gramsPerServing later.
                        row
                    } else {
                        val factor = 100.0 / grams
                        row.copy(
                            basisType = BasisType.PER_100G,
                            amount = row.amount * factor
                        )
                    }
                }
            }
        }

        foodNutrients.replaceForFood(id, normalizedRows)
        return id
    }
}
