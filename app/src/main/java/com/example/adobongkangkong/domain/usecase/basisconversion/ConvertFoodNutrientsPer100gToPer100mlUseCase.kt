package com.example.adobongkangkong.domain.usecase.basisconversion

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodNutrientRow
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.isMassUnit
import com.example.adobongkangkong.domain.model.isVolumeUnit
import com.example.adobongkangkong.domain.model.toGrams
import com.example.adobongkangkong.domain.model.toMilliliters

/**
 * Converts nutrient rows from PER_100G to PER_100ML using food-specific density.
 *
 * Preconditions:
 * - We must be able to compute gramsPerServing AND mlPerServing for the current Food serving definition.
 * - This use case does NOT guess density. It derives density only from (gramsPerServing/mlPerServing).
 *
 * Notes:
 * - Rows not in PER_100G are left unchanged (caller decides if/when to canonicalize first).
 * - basisGrams remains 100.0 to match existing conventions (even for PER_100ML).
 */
class ConvertFoodNutrientsPer100gToPer100mlUseCase {

    sealed class Result {
        data class Success(
            val convertedRows: List<FoodNutrientRow>,
            /**
             * Optional suggestion if you intend to “switch canonical grounding” to volume.
             * This does NOT mutate persistence; caller may ignore.
             */
            val suggestedFoodForVolumeGrounding: Food?
        ) : Result()

        data class Blocked(val reason: String) : Result()
    }

    operator fun invoke(
        food: Food,
        rows: List<FoodNutrientRow>
    ): Result {
        val ctx = computeContext(food) ?: return Result.Blocked(
            "Cannot convert PER_100G → PER_100ML: need both grams-per-serving and ml-per-serving to derive density."
        )

        // amountPer100ml = amountPer100g * (grams in 100ml / 100g)
        // grams in 100ml = densityGPerMl * 100ml
        // => amountPer100ml = amountPer100g * densityGPerMl
        val density = ctx.densityGPerMl

        val converted = rows.map { r ->
            if (r.basisType != BasisType.PER_100G) return@map r
            r.copy(
                basisType = BasisType.PER_100ML,
                amount = r.amount * density,
                basisGrams = 100.0
            )
        }

        // Optional: if caller wants canonical volume grounding, they likely must avoid the app
        // treating the food as “mass grounded”. Your invariant in ImportUsda* is:
        // isVolumeGrounded(food) returns false if isMassGrounded(food) is true.
        //
        // We can only suggest a safe adjustment when the food is volume-ish.
        val suggestedFood: Food? = suggestVolumeGrounding(food)

        return Result.Success(
            convertedRows = converted,
            suggestedFoodForVolumeGrounding = suggestedFood
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
        // Truth-first: explicit bridge wins.
        val bridgedGPer1 = food.gramsPerServingUnit?.takeIf { it > 0.0 }
        val grams = when {
            bridgedGPer1 != null -> food.servingSize * bridgedGPer1
            food.servingUnit.isMassUnit() -> food.servingUnit.toGrams(food.servingSize)
            else -> null
        }
        return grams?.takeIf { it > 0.0 }
    }

    private fun computeMlPerServing(food: Food): Double? {
        // Truth-first: explicit bridge wins.
        val bridgedMlPer1 = food.mlPerServingUnit?.takeIf { it > 0.0 }
        val ml = when {
            bridgedMlPer1 != null -> food.servingSize * bridgedMlPer1
            food.servingUnit.isVolumeUnit() -> food.servingUnit.toMilliliters(food.servingSize)
            else -> null
        }
        return ml?.takeIf { it > 0.0 }
    }

    private fun suggestVolumeGrounding(food: Food): Food? {
        // If the serving unit itself is mass-based, you can't truly become volume-grounded without changing servingUnit,
        // which is outside this use case. So we only suggest when unit is already volume-like.
        if (!food.servingUnit.isVolumeUnit()) return null

        // To satisfy your single-grounding invariant, volume grounding requires NOT mass-grounded:
        // isMassGrounded(food) = servingUnit.isMassUnit() || gramsPerServingUnit != null
        // servingUnit is volume here, so we can safely clear gramsPerServingUnit.
        if (food.gramsPerServingUnit == null) return null

        return food.copy(
            gramsPerServingUnit = null
            // keep mlPerServingUnit as-is; could be null if unit-derived
        )
    }
}
