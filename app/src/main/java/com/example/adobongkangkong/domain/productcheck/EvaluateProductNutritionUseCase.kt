package com.example.adobongkangkong.domain.productcheck

import javax.inject.Inject

/**
 * EvaluateProductNutritionUseCase
 *
 * Pure evaluation logic for USDA food.
 *
 * IMPORTANT:
 * - No DB
 * - No repository
 * - No side effects
 *
 * Uses USDA-reported per-serving values.
 */
class EvaluateProductNutritionUseCase @Inject constructor() {

    data class NutrientResult(
        val name: String,
        val value: Double?,
        val unit: String,
        val threshold: Double,
        val isWarning: Boolean,
        val message: String
    )

    data class Result(
        val foodName: String,
        val brand: String?,
        val servingText: String?,
        val nutrients: List<NutrientResult>,
        val overallMessage: String,
        val hasWarnings: Boolean
    )

    companion object {
        private const val SODIUM_MAX_MG = 400.0
        private const val SUGAR_MAX_G = 10.0
    }

    /**
     * @param sodiumMg per serving
     * @param sugarG per serving
     */
    fun execute(
        foodName: String,
        brand: String?,
        servingText: String?,
        sodiumMg: Double?,
        sugarG: Double?
    ): Result {

        val sodiumResult = evaluate(
            name = "Sodium",
            value = sodiumMg,
            unit = "mg",
            threshold = SODIUM_MAX_MG,
            warningMessage = "High sodium (over 400mg)"
        )

        val sugarResult = evaluate(
            name = "Total sugars",
            value = sugarG,
            unit = "g",
            threshold = SUGAR_MAX_G,
            warningMessage = "High sugar (over 10g)"
        )

        val nutrients = listOf(sodiumResult, sugarResult)

        val hasWarnings = nutrients.any { it.isWarning }

        val overall = if (hasWarnings) {
            "Use caution"
        } else {
            "Looks okay based on selected nutrients"
        }

        return Result(
            foodName = foodName,
            brand = brand,
            servingText = servingText,
            nutrients = nutrients,
            overallMessage = overall,
            hasWarnings = hasWarnings
        )
    }

    private fun evaluate(
        name: String,
        value: Double?,
        unit: String,
        threshold: Double,
        warningMessage: String
    ): NutrientResult {

        val isWarning = value != null && value > threshold

        val message = when {
            value == null -> "Not available"
            isWarning -> "⚠ $warningMessage"
            else -> "OK"
        }

        return NutrientResult(
            name = name,
            value = value,
            unit = unit,
            threshold = threshold,
            isWarning = isWarning,
            message = message
        )
    }
}