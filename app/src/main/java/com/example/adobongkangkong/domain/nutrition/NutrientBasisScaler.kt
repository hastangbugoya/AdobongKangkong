package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.data.local.db.entity.BasisType
import kotlin.math.abs

/**
 * Canonical nutrient scaling utilities.
 *
 * DO NOT TOUCH THIS (future-you note):
 * - The DB canonical storage basis is typically PER_100G whenever grams-per-serving-unit is known.
 * - The UI shows nutrient amounts per *serving* (servingSize * gramsPerServingUnit).
 * - Therefore conversions between canonical storage and UI display MUST be done here.
 *
 * Regression example:
 * Bok Choy = 59 kcal per 1 lb (453.59237g).
 * Stored PER_100G = 59 * (100 / 453.59237) = 13.007273468907776...
 * Display per 1 lb must scale back: 13.00727... * (453.59237 / 100) = 59.
 */
object NutrientBasisScaler {

    data class Result(
        val amount: Double,
        val didScale: Boolean
    )

    /**
     * Converts a stored nutrient amount (in [storedBasis]) into a per-serving display amount.
     *
     * If scaling cannot be performed (missing grams info), returns the original amount with didScale=false.
     */
    fun canonicalToDisplayPerServing(
        storedAmount: Double,
        storedBasis: BasisType,
        servingSize: Double,
        gramsPerServingUnit: Double?
    ): Result {
        return when (storedBasis) {
            BasisType.PER_100G -> {
                val grams = gramsPerServing(servingSize, gramsPerServingUnit)
                    ?: return Result(storedAmount, false)
                Result(storedAmount * grams / 100.0, true)
            }

            // We do not attempt ml<->g conversions here (density unknown).
            BasisType.PER_100ML -> Result(storedAmount, false)

            // Already a per-serving snapshot, nothing to do.
            BasisType.USDA_REPORTED_SERVING -> Result(storedAmount, false)
        }
    }

    /**
     * Converts a per-serving UI amount into canonical storage amount for the given [canonicalBasis].
     *
     * If scaling cannot be performed (missing grams info), returns the original amount with didScale=false.
     */
    fun displayPerServingToCanonical(
        uiPerServingAmount: Double,
        canonicalBasis: BasisType,
        servingSize: Double,
        gramsPerServingUnit: Double?
    ): Result {
        return when (canonicalBasis) {
            BasisType.PER_100G -> {
                val grams = gramsPerServing(servingSize, gramsPerServingUnit)
                    ?: return Result(uiPerServingAmount, false)
                Result(uiPerServingAmount * 100.0 / grams, true)
            }

            BasisType.PER_100ML -> Result(uiPerServingAmount, false)
            BasisType.USDA_REPORTED_SERVING -> Result(uiPerServingAmount, false)
        }
    }

    /**
     * Convenience: stable "almost equals" helper for tests or sanity checks.
     */
    fun almostEqual(a: Double, b: Double, eps: Double = 1e-9): Boolean = abs(a - b) <= eps

    private fun gramsPerServing(servingSize: Double, gramsPerServingUnit: Double?): Double? {
        if (servingSize <= 0.0) return null
        val g = gramsPerServingUnit ?: return null
        if (g <= 0.0) return null
        return servingSize * g
    }

// DO NOT TOUCH THIS (future-you note):
// Volume scaling is independent from mass scaling.
// PER_100ML assumes pure volume math (ml only).
    fun canonicalToDisplayPerServingVolume(
        storedAmount: Double,
        storedBasis: BasisType,
        servingSize: Double,
        mlPerServingUnit: Double?
    ): Result {
        return when (storedBasis) {
            BasisType.PER_100ML -> {
                val ml = mlPerServing(servingSize, mlPerServingUnit)
                    ?: return Result(storedAmount, false)
                Result(storedAmount * ml / 100.0, true)
            }
            else -> Result(storedAmount, false)
        }
    }

    fun displayPerServingToCanonicalVolume(
        uiPerServingAmount: Double,
        canonicalBasis: BasisType,
        servingSize: Double,
        mlPerServingUnit: Double?
    ): Result {
        return when (canonicalBasis) {
            BasisType.PER_100ML -> {
                val ml = mlPerServing(servingSize, mlPerServingUnit)
                    ?: return Result(uiPerServingAmount, false)
                Result(uiPerServingAmount * 100.0 / ml, true)
            }
            else -> Result(uiPerServingAmount, false)
        }
    }

    private fun mlPerServing(servingSize: Double, mlPerServingUnit: Double?): Double? {
        if (servingSize <= 0.0) return null
        val ml = mlPerServingUnit ?: return null
        if (ml <= 0.0) return null
        return servingSize * ml
    }

}
