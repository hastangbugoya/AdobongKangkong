package com.example.adobongkangkong.domain.usecase.basisconversion

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodNutrientRow
import com.example.adobongkangkong.domain.model.isMassUnit
import com.example.adobongkangkong.domain.model.isVolumeUnit
import com.example.adobongkangkong.domain.model.toGrams
import com.example.adobongkangkong.domain.model.toMilliliters

/**
 * Converts nutrient rows from PER_100ML to PER_100G using food-specific density.
 *
 * Preconditions:
 * - Must compute gramsPerServing AND mlPerServing for the current Food serving definition.
 *
 * Notes:
 * - Rows not in PER_100ML are left unchanged.
 * - basisGrams remains 100.0 to match existing conventions.
 */
class ConvertFoodNutrientsPer100mlToPer100gUseCase {

    sealed class Result {
        data class Success(
            val convertedRows: List<FoodNutrientRow>,
            /**
             * Optional suggestion if you intend to “switch canonical grounding” to mass.
             * This does NOT mutate persistence; caller may ignore.
             */
            val suggestedFoodForMassGrounding: Food?
        ) : Result()

        data class Blocked(val reason: String) : Result()
    }

    operator fun invoke(
        food: Food,
        rows: List<FoodNutrientRow>
    ): Result {
        val ctx = computeContext(food) ?: return Result.Blocked(
            "Cannot convert PER_100ML → PER_100G: need both grams-per-serving and ml-per-serving to derive density."
        )

        val density = ctx.densityGPerMl
        if (density <= 0.0) return Result.Blocked("Invalid density (g/mL) computed.")

        // amountPer100g = amountPer100ml * (ml in 100g / 100ml)
        // ml in 100g = 100g / densityGPerMl
        // => amountPer100g = amountPer100ml / densityGPerMl
        val converted = rows.map { r ->
            if (r.basisType != BasisType.PER_100ML) return@map r
            r.copy(
                basisType = BasisType.PER_100G,
                amount = r.amount / density,
                basisGrams = 100.0
            )
        }

        // Optional suggestion: if caller wants canonical mass grounding, avoid the app treating the food as volume-grounded.
        // In your invariant, isVolumeGrounded(food) returns false if isMassGrounded(food) is true.
        // So mass grounding can simply ensure gramsPerServingUnit is present or servingUnit is mass.
        // We can only safely suggest clearing mlPerServingUnit when the unit is already mass-like.
        val suggestedFood: Food? = suggestMassGrounding(food)

        return Result.Success(
            convertedRows = converted,
            suggestedFoodForMassGrounding = suggestedFood
        )
    }

    private data class Context(
        val gramsPerServing: Double,
        val mlPerServing: Double,
        val densityGPerMl: Double
    )

    private fun computeContext(food: Food): Context? {
        val grams = computeGramsPerServing(food) ?: return null
        val ml = computeMlPerServing(food) ?: return null
        if (ml <= 0.0) return null
        val density = grams / ml
        if (density <= 0.0) return null
        return Context(
            gramsPerServing = grams,
            mlPerServing = ml,
            densityGPerMl = density
        )
    }

    private fun computeGramsPerServing(food: Food): Double? {
        val bridgedGPer1 = food.gramsPerServingUnit?.takeIf { it > 0.0 }
        val grams = when {
            bridgedGPer1 != null -> food.servingSize * bridgedGPer1
            food.servingUnit.isMassUnit() -> food.servingUnit.toGrams(food.servingSize)
            else -> null
        }
        return grams?.takeIf { it > 0.0 }
    }

    private fun computeMlPerServing(food: Food): Double? {
        val bridgedMlPer1 = food.mlPerServingUnit?.takeIf { it > 0.0 }
        val ml = when {
            bridgedMlPer1 != null -> food.servingSize * bridgedMlPer1
            food.servingUnit.isVolumeUnit() -> food.servingUnit.toMilliliters(food.servingSize)
            else -> null
        }
        return ml?.takeIf { it > 0.0 }
    }

    private fun suggestMassGrounding(food: Food): Food? {
        // If servingUnit is volume-based, clearing mlPerServingUnit would not make it mass-grounded;
        // mass grounding would require gramsPerServingUnit to exist (or unit mass). We won’t invent grams.
        if (!food.servingUnit.isMassUnit()) return null

        // If unit is mass, we can suggest clearing mlPerServingUnit to reduce “volume truth” confusion,
        // but this is optional; caller may ignore.
        if (food.mlPerServingUnit == null) return null

        return food.copy(
            mlPerServingUnit = null
        )
    }
}
