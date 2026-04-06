package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.data.local.db.entity.BasisType
import javax.inject.Inject

/**
 * Converts edited displayed nutrient values (per serving) back into canonical nutrients.
 *
 * Input:
 * - edited UI nutrient values for the current serving
 * - canonical basis type (editor interpretation, not storage basis)
 * - resolved serving grounding
 *
 * Output:
 * - normalized canonical nutrients (PER_100G or PER_100ML)
 * - explicit blocked reason if conversion is not possible
 *
 * IMPORTANT:
 * - No density guessing
 * - No partial conversion logic
 * - All nutrients are converted in one bulk pass
 *
 * ## UPDATED BEHAVIOR
 * - USDA_REPORTED_SERVING is now SUPPORTED for manual entry flows
 * - It will normalize using:
 *   - grams path if available
 *   - else mL path if available
 *   - else blocked
 */
class ApplyEditedNutrientsUseCase @Inject constructor() {

    fun execute(
        displayedNutrients: Map<NutrientKey, Double>,
        basisType: BasisType,
        resolution: ServingResolution
    ): Result {

        if (displayedNutrients.isEmpty()) {
            return Result.Blocked(BlockReason.NO_NUTRIENTS)
        }

        return when (basisType) {

            BasisType.PER_100G -> {
                val grams = resolution.gramsPerServing
                    ?: return Result.Blocked(BlockReason.NO_GRAM_PATH)

                if (grams <= 0.0) {
                    return Result.Blocked(BlockReason.INVALID_SERVING_AMOUNT)
                }

                val canonical = normalize(displayedNutrients, grams)

                Result.Success(
                    canonicalNutrients = canonical,
                    usedPath = ComputationPath.PER_100G
                )
            }

            BasisType.PER_100ML -> {
                val milliliters = resolution.millilitersPerServing
                    ?: return Result.Blocked(BlockReason.NO_ML_PATH)

                if (milliliters <= 0.0) {
                    return Result.Blocked(BlockReason.INVALID_SERVING_AMOUNT)
                }

                val canonical = normalize(displayedNutrients, milliliters)

                Result.Success(
                    canonicalNutrients = canonical,
                    usedPath = ComputationPath.PER_100ML
                )
            }

            BasisType.USDA_REPORTED_SERVING -> {
                // ✅ NEW: support per-serving manual entry

                val grams = resolution.gramsPerServing
                if (grams != null) {
                    if (grams <= 0.0) {
                        return Result.Blocked(BlockReason.INVALID_SERVING_AMOUNT)
                    }

                    val canonical = normalize(displayedNutrients, grams)

                    return Result.Success(
                        canonicalNutrients = canonical,
                        usedPath = ComputationPath.PER_100G
                    )
                }

                val milliliters = resolution.millilitersPerServing
                if (milliliters != null) {
                    if (milliliters <= 0.0) {
                        return Result.Blocked(BlockReason.INVALID_SERVING_AMOUNT)
                    }

                    val canonical = normalize(displayedNutrients, milliliters)

                    return Result.Success(
                        canonicalNutrients = canonical,
                        usedPath = ComputationPath.PER_100ML
                    )
                }

                // No valid grounding path
                Result.Blocked(BlockReason.NO_SERVING_GROUNDING_PATH)
            }
        }
    }

    private fun normalize(
        displayed: Map<NutrientKey, Double>,
        servingAmount: Double
    ): Map<NutrientKey, Double> {
        val factor = 100.0 / servingAmount

        return displayed.mapValues { (_, value) ->
            value * factor
        }
    }

    sealed class Result {

        data class Success(
            val canonicalNutrients: Map<NutrientKey, Double>,
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
        INVALID_SERVING_AMOUNT,
        NO_SERVING_GROUNDING_PATH
    }
}