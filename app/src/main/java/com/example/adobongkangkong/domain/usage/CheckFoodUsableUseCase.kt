package com.example.adobongkangkong.domain.usage

import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.isVolumeUnit
import com.example.adobongkangkong.domain.model.requiresGramsPerServing
import javax.inject.Inject

class CheckFoodUsableUseCase @Inject constructor() {

    fun execute(
        servingUnit: ServingUnit,
        gramsPerServingUnit: Double?,
        mlPerServingUnit: Double?,
        amountInput: AmountInput,
        context: UsageContext
    ): FoodUsageCheck {
        val needsBacking = servingUnit.requiresGramsPerServing()
        val isServingBased = amountInput is AmountInput.ByServings

        // LOCKED-IN: Liquids are volume-grounded and do NOT require grams.
        // Volume-grounded if:
        // - servingUnit is a deterministic volume unit convertible to mL, OR
        // - mlPerServingUnit is present (e.g. USDA truth bridge for CAN/BOTTLE)
        val isVolumeGrounded =
            servingUnit.isVolumeUnit() || (mlPerServingUnit?.takeIf { it > 0.0 } != null)

        if (needsBacking && isServingBased && gramsPerServingUnit == null && !isVolumeGrounded) {
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

    /**
     * Back-compat overload for existing call sites.
     *
     * AI NOTE (2026-02-06): Prefer calling the overload that supplies [mlPerServingUnit] so
     * USDA liquid imports (volume-grounded) are not incorrectly blocked.
     */
    fun execute(
        servingUnit: ServingUnit,
        gramsPerServingUnit: Double?,
        amountInput: AmountInput,
        context: UsageContext
    ): FoodUsageCheck = execute(
        servingUnit = servingUnit,
        gramsPerServingUnit = gramsPerServingUnit,
        mlPerServingUnit = null,
        amountInput = amountInput,
        context = context
    )
}

/**
 * AI NOTE (2026-02-06):
 * - This use case historically enforced grams backing for volume/container units.
 * - With volume-grounded liquids (PER_100ML + mlPerServingUnit truth bridge), grams are never required.
 * - Do NOT add grams<->mL conversion here.
 */
