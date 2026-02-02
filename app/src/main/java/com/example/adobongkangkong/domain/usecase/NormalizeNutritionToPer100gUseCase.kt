package com.example.adobongkangkong.domain.usecase

import javax.inject.Inject

/**
 * Normalizes nutrient values to a PER_100G basis.
 *
 * Strategy A: normalize on write, tolerate legacy on read.
 */
class NormalizeNutritionToPer100gUseCase @Inject constructor() {

    data class Input(
        val basis: Basis,
        /** Grams for the basis. Required when basis == PER_SERVING. */
        val gramsForBasis: Double?,
        val nutrients: Map<String, Double?> // nutrientCode -> amount
    )

    data class Output(
        val basis: Basis = Basis.PER_100G,
        val nutrientsPer100g: Map<String, Double?>
    )

    enum class Basis { PER_100G, PER_SERVING }

    sealed class Result {
        data class Ok(val output: Output) : Result()
        data class CannotNormalize(val reason: String) : Result()
    }

    fun invoke(input: Input): Result {
        return when (input.basis) {
            Basis.PER_100G -> {
                Result.Ok(
                    Output(
                        nutrientsPer100g = input.nutrients
                    )
                )
            }

            Basis.PER_SERVING -> {
                val grams = input.gramsForBasis
                if (grams == null || grams <= 0.0) {
                    return Result.CannotNormalize("Missing/invalid gramsForBasis for PER_SERVING")
                }
                val factor = 100.0 / grams
                val out = input.nutrients.mapValues { (_, v) ->
                    v?.let { it * factor }
                }
                Result.Ok(Output(nutrientsPer100g = out))
            }
        }
    }
}
