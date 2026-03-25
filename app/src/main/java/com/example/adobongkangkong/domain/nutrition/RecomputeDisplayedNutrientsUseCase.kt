package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.data.local.db.entity.BasisType

/**
 * Recomputes displayed nutrient values (per serving) from canonical nutrients.
 *
 * Input:
 * - canonical nutrients (PER_100G or PER_100ML)
 * - serving resolution
 *
 * Output:
 * - per-serving nutrient map
 * - computation status
 */
class RecomputeDisplayedNutrientsUseCase {

    fun execute(
        canonicalNutrients: Map<NutrientKey, Double>,
        basisType: BasisType,
        resolution: ServingResolution
    ): Result {

        if (canonicalNutrients.isEmpty()) {
            return Result.Blocked(BlockReason.NO_NUTRIENTS)
        }

        return when (basisType) {

            BasisType.PER_100G -> {
                val grams = resolution.gramsPerServing
                    ?: return Result.Blocked(BlockReason.NO_GRAM_PATH)

                val scaled = scale(canonicalNutrients, grams)

                Result.Success(
                    nutrients = scaled,
                    usedPath = ComputationPath.PER_100G
                )
            }

            BasisType.PER_100ML -> {
                val ml = resolution.millilitersPerServing
                    ?: return Result.Blocked(BlockReason.NO_ML_PATH)

                val scaled = scale(canonicalNutrients, ml)

                Result.Success(
                    nutrients = scaled,
                    usedPath = ComputationPath.PER_100ML
                )
            }

            else -> Result.Blocked(BlockReason.UNSUPPORTED_BASIS)
        }
    }

    private fun scale(
        canonical: Map<NutrientKey, Double>,
        amount: Double
    ): Map<NutrientKey, Double> {
        val factor = amount / 100.0

        return canonical.mapValues { (_, value) ->
            value * factor
        }
    }

    sealed class Result {

        data class Success(
            val nutrients: Map<NutrientKey, Double>,
            val usedPath: ComputationPath
        ) : Result()

        data class Blocked(
            val reason: BlockReason
        ) : Result()
    }

    enum class ComputationPath {
        PER_100G,
        PER_100ML
    }

    enum class BlockReason {
        NO_GRAM_PATH,
        NO_ML_PATH,
        NO_NUTRIENTS,
        UNSUPPORTED_BASIS
    }
}