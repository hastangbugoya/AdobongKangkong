package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodEntity
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity
import com.example.adobongkangkong.domain.model.ServingUnit

class NormalizeFoodNutrientsPerGramUseCase {

    sealed interface Result {
        data class Ok(val perGram: Map<Long, Double>) : Result
        data class Blocked(val message: String) : Result
    }

    fun execute(
        food: FoodEntity,
        nutrientRows: List<FoodNutrientEntity>
    ): Result {
        if (nutrientRows.isEmpty()) return Result.Blocked("Food nutrition incomplete.")

        val perGram = mutableMapOf<Long, Double>()

        for (row in nutrientRows) {
            val perGramAmt = when (row.basisType) {
                BasisType.PER_100G -> row.nutrientAmountPerBasis / 100.0

                BasisType.PER_SERVING -> {
                    if (food.servingUnit == ServingUnit.G) {
                        val grams = food.servingSize
                        if (grams <= 0.0) return Result.Blocked("Invalid serving size.")
                        row.nutrientAmountPerBasis / grams
                    } else {
                        val gps = food.gramsPerServing ?: return Result.Blocked("Set grams-per-serving (no density guessing).")
                        if (gps <= 0.0) return Result.Blocked("Invalid grams-per-serving.")
                        row.nutrientAmountPerBasis / gps
                    }
                }
            }

            perGram[row.nutrientId] = (perGram[row.nutrientId] ?: 0.0) + perGramAmt
        }

        return Result.Ok(perGram)
    }
}
