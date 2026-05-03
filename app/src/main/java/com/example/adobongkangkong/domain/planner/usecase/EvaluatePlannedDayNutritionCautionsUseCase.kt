package com.example.adobongkangkong.domain.planner.usecase

import javax.inject.Inject

/**
 * Evaluates gentle, non-blocking nutrition cautions for a planned day.
 *
 * This is intentionally pure:
 * - no database access
 * - no DataStore access
 * - no UI dependencies
 *
 * Scope v1:
 * - sodium daily planned total
 * - total sugar daily planned total
 *
 * Important:
 * Sugar means TOTAL sugar, not added sugar.
 */
class EvaluatePlannedDayNutritionCautionsUseCase @Inject constructor() {

    operator fun invoke(
        plannedSodiumMg: Double?,
        plannedSugarG: Double?,
        dailySodiumLimitMg: Double,
        dailySugarLimitG: Double,
    ): List<PlannerNutritionCaution> {
        val cautions = mutableListOf<PlannerNutritionCaution>()

        val sodium = plannedSodiumMg
        if (
            sodium != null &&
            dailySodiumLimitMg > 0.0 &&
            sodium > dailySodiumLimitMg
        ) {
            cautions += PlannerNutritionCaution(
                nutrient = PlannerNutritionCautionNutrient.SODIUM,
                plannedAmount = sodium,
                limitAmount = dailySodiumLimitMg,
                unit = "mg",
                message = "Use caution: above your daily sodium limit of ${dailySodiumLimitMg.roundWhole()} mg."
            )
        }

        val sugar = plannedSugarG
        if (
            sugar != null &&
            dailySugarLimitG > 0.0 &&
            sugar > dailySugarLimitG
        ) {
            cautions += PlannerNutritionCaution(
                nutrient = PlannerNutritionCautionNutrient.TOTAL_SUGAR,
                plannedAmount = sugar,
                limitAmount = dailySugarLimitG,
                unit = "g",
                message = "Use caution: above your daily total sugar caution of ${dailySugarLimitG.roundWhole()} g."
            )
        }

        return cautions
    }

    private fun Double.roundWhole(): String =
        "%,.0f".format(this)
}

data class PlannerNutritionCaution(
    val nutrient: PlannerNutritionCautionNutrient,
    val plannedAmount: Double,
    val limitAmount: Double,
    val unit: String,
    val message: String,
)

enum class PlannerNutritionCautionNutrient {
    SODIUM,
    TOTAL_SUGAR,
}