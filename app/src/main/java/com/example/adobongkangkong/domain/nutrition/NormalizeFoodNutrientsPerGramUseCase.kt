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

            val perGramAmt: Double = when (row.basisType) {

                // Already normalized: X per 100 g → X / 100 per gram
                BasisType.PER_100G -> {
                    row.nutrientAmountPerBasis / 100.0
                }

                // Serving-based snapshot (USDA or user-defined)
                BasisType.USDA_REPORTED_SERVING -> {

                    // Case 1: serving itself is grams (e.g. servingSize = 37 g)
                    if (food.servingUnit == ServingUnit.G) {
                        val grams = food.servingSize
                        if (grams <= 0.0) {
                            return Result.Blocked("Invalid serving size.")
                        }
                        row.nutrientAmountPerBasis / grams
                    }

                    // Case 2: non-gram serving with explicit grams-per-serving backing
                    else {
                        val gpsu = food.gramsPerServingUnit
                            ?: return Result.Blocked("Set grams-per-serving (no density guessing).")

                        if (gpsu <= 0.0) {
                            return Result.Blocked("Invalid grams-per-serving.")
                        }
                        row.nutrientAmountPerBasis / gpsu
                    }
                }

                // Volume-based normalization cannot be converted to grams without density
                BasisType.PER_100ML -> {
                    return Result.Blocked("Cannot normalize PER_100ML to grams without density.")
                }
            }

            perGram[row.nutrientId] =
                (perGram[row.nutrientId] ?: 0.0) + perGramAmt
        }


        return Result.Ok(perGram)
    }
}
