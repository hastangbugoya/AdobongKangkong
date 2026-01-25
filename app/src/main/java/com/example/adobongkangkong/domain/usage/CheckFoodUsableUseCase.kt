package com.example.adobongkangkong.domain.usage

import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.requiresGramsPerServing
import javax.inject.Inject

class CheckFoodUsableUseCase @Inject constructor() {

    fun execute(
        servingUnit: ServingUnit,
        gramsPerServing: Double?,
        amountInput: AmountInput,
        context: UsageContext
    ): FoodUsageCheck {
        val needsBacking = servingUnit.requiresGramsPerServing()
        val isServingBased = amountInput is AmountInput.ByServings

        if (needsBacking && isServingBased && gramsPerServing == null) {
            val noun = when (context) {
                UsageContext.LOGGING -> "log this food by serving"
                UsageContext.RECIPE -> "use this food in a recipe by servings"
            }
            return FoodUsageCheck.Blocked(
                reason = BlockReason.MissingGramsPerServing,
                message = "Set grams-per-serving to $noun (no density guessing)."
            )
        }
        return FoodUsageCheck.Ok
    }
}
