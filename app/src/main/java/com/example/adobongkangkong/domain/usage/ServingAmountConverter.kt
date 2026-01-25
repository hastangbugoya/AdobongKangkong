package com.example.adobongkangkong.domain.usage

import com.example.adobongkangkong.domain.model.ServingUnit

/**
 * Converts between grams and servings.
 *
 * Policy:
 * - If servingUnit is weight-based (G/MG/OZ/LB), "servings" can be interpreted as weight directly.
 *   (Your current DB comment implies this possibility.)
 * - Otherwise, grams <-> servings requires gramsPerServing.
 */
object ServingAmountConverter {

    fun gramsToServings(
        servingUnit: ServingUnit,
        gramsPerServing: Double?,
        grams: Double
    ): Result<Double> {
        if (grams <= 0.0) return Result.success(0.0)

        return when (servingUnit) {
            ServingUnit.G -> Result.success(grams) // treat "servings" as grams
            ServingUnit.MG -> Result.success(grams * 1000.0) // if you ever store mg-based serving
            ServingUnit.OZ -> Result.failure(IllegalStateException("OZ conversion requires explicit policy"))
            ServingUnit.LB -> Result.failure(IllegalStateException("LB conversion requires explicit policy"))
            else -> {
                val gps = gramsPerServing
                    ?: return Result.failure(IllegalStateException("Missing gramsPerServing"))
                Result.success(grams / gps)
            }
        }
    }

    fun servingsToGrams(
        servingUnit: ServingUnit,
        gramsPerServing: Double?,
        servings: Double
    ): Result<Double> {
        if (servings <= 0.0) return Result.success(0.0)

        return when (servingUnit) {
            ServingUnit.G -> Result.success(servings) // "servings" are grams
            ServingUnit.MG -> Result.success(servings / 1000.0)
            ServingUnit.OZ -> Result.failure(IllegalStateException("OZ conversion requires explicit policy"))
            ServingUnit.LB -> Result.failure(IllegalStateException("LB conversion requires explicit policy"))
            else -> {
                val gps = gramsPerServing
                    ?: return Result.failure(IllegalStateException("Missing gramsPerServing"))
                Result.success(servings * gps)
            }
        }
    }
}
