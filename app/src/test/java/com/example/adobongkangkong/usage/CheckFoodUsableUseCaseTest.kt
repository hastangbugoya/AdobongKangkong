package com.example.adobongkangkong.usage

import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.usage.CheckFoodUsableUseCase
import com.example.adobongkangkong.domain.usage.FoodUsageCheck
import com.example.adobongkangkong.domain.usage.UsageContext
import kotlin.test.Test
import kotlin.test.assertTrue

class CheckFoodUsableUseCaseTest {

    private val useCase = CheckFoodUsableUseCase()

    @Test
    fun volume_unit_by_servings_missing_grams_is_ok() {
        val result = useCase.execute(
            servingUnit = ServingUnit.CUP_US,
            gramsPerServingUnit = null,
            mlPerServingUnit = null,
            amountInput = AmountInput.ByServings(servings = 1.0),
            context = UsageContext.LOGGING
        )

        assertTrue(result is FoodUsageCheck.Ok)
    }

    @Test
    fun container_unit_by_servings_with_ml_bridge_missing_grams_is_ok() {
        val result = useCase.execute(
            servingUnit = ServingUnit.CAN,
            gramsPerServingUnit = null,
            mlPerServingUnit = 473.0,
            amountInput = AmountInput.ByServings(servings = 1.0),
            context = UsageContext.LOGGING
        )

        assertTrue(result is FoodUsageCheck.Ok)
    }

    @Test
    fun container_unit_by_servings_missing_grams_and_missing_ml_bridge_is_blocked() {
        val result = useCase.execute(
            servingUnit = ServingUnit.CAN,
            gramsPerServingUnit = null,
            mlPerServingUnit = null,
            amountInput = AmountInput.ByServings(servings = 1.0),
            context = UsageContext.LOGGING
        )

        assertTrue(result is FoodUsageCheck.Blocked)
    }

    @Test
    fun by_grams_is_ok_even_when_serving_has_no_backing() {
        val result = useCase.execute(
            servingUnit = ServingUnit.CUP_US,
            gramsPerServingUnit = null,
            mlPerServingUnit = null,
            amountInput = AmountInput.ByGrams(grams = 25.0),
            context = UsageContext.LOGGING
        )

        assertTrue(result is FoodUsageCheck.Ok)
    }

    @Test
    fun mass_unit_by_servings_is_ok_without_backing() {
        val result = useCase.execute(
            servingUnit = ServingUnit.G,
            gramsPerServingUnit = null,
            mlPerServingUnit = null,
            amountInput = AmountInput.ByServings(servings = 100.0),
            context = UsageContext.LOGGING
        )

        assertTrue(result is FoodUsageCheck.Ok)
    }
}