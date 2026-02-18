package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.data.local.db.entity.BasisType


/**
 * Computes the multiplier that scales a food's canonical nutrients to match a user's logged quantity.
 *
 * Output:
 * - multiplier = 1.0 means "use canonical nutrients as-is"
 * - multiplier = 2.0 means "double canonical nutrients"
 *
 * Canonical meaning by BasisType:
 * - PER_100G: canonical nutrients are per 100 g
 * - PER_100ML: canonical nutrients are per 100 mL
 * - USDA_REPORTED_SERVING: canonical nutrients are per 1 serving
 *
 * This use case DOES NOT modify foods, does not convert nutrients into a new basis.
 * It only returns a scale factor.
 */
class ComputeCanonicalNutrientMultiplierUseCase {

    operator fun invoke(
        basisType: BasisType,
        log: LogAmount,
        // Bridges for conversions (nullable because some foods may be missing them)
        gramsPerServing: Double?,
        mlPerServing: Double?,
    ): Result {
        return when (basisType) {
            BasisType.PER_100G -> computeForPer100g(log, gramsPerServing)
            BasisType.PER_100ML -> computeForPer100ml(log, mlPerServing)
            BasisType.USDA_REPORTED_SERVING -> computeForServing(log, gramsPerServing, mlPerServing)
        }
    }

    private fun computeForPer100g(
        log: LogAmount,
        gramsPerServing: Double?,
    ): Result {
        val grams = when (log) {
            is LogAmount.Grams -> log.value
            is LogAmount.Servings -> gramsPerServing?.let { it * log.value }
            is LogAmount.Milliliters -> null // not supported without density; keep strict for now
        } ?: return Result.Blocked("Need grams (or gramsPerServing for servings) for PER_100G foods")

        if (grams <= 0.0) return Result.Blocked("Logged grams must be > 0")

        // Canonical is "per 100 g"
        return Result.Success(multiplier = grams / 100.0)
    }

    private fun computeForPer100ml(
        log: LogAmount,
        mlPerServing: Double?,
    ): Result {
        val ml = when (log) {
            is LogAmount.Milliliters -> log.value
            is LogAmount.Servings -> mlPerServing?.let { it * log.value }
            is LogAmount.Grams -> null // not supported without density; strict for now
        } ?: return Result.Blocked("Need mL (or mlPerServing for servings) for PER_100ML foods")

        if (ml <= 0.0) return Result.Blocked("Logged mL must be > 0")

        // Canonical is "per 100 mL"
        return Result.Success(multiplier = ml / 100.0)
    }

    private fun computeForServing(
        log: LogAmount,
        gramsPerServing: Double?,
        mlPerServing: Double?,
    ): Result {
        val servings = when (log) {
            is LogAmount.Servings -> log.value
            is LogAmount.Grams -> gramsPerServing?.let { log.value / it }
            is LogAmount.Milliliters -> mlPerServing?.let { log.value / it }
        } ?: return Result.Blocked(
            "Need servings directly, or provide gramsPerServing/mlPerServing to convert into servings"
        )

        if (servings <= 0.0) return Result.Blocked("Logged servings must be > 0")

        // Canonical is "per 1 serving"
        return Result.Success(multiplier = servings)
    }

    /**
     * The user's log input (exactly one).
     *
     * Keep this private if you only want to call this use case from one module.
     * Or make it public if you want a single shared logging quantity type everywhere.
     */
    sealed class LogAmount {
        data class Grams(val value: Double) : LogAmount()
        data class Milliliters(val value: Double) : LogAmount()
        data class Servings(val value: Double) : LogAmount()
    }

    sealed class Result {
        data class Success(val multiplier: Double) : Result()
        data class Blocked(val reason: String) : Result()
    }
}
