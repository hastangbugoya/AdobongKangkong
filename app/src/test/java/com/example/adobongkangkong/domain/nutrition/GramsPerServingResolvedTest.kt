package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.ServingUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GramsPerServingResolvedTest {

    private fun food(
        servingSize: Double = 1.0,
        unit: ServingUnit = ServingUnit.SERVING,
        gramsPerUnit: Double? = null,
        mlPerUnit: Double? = null
    ) = Food(
        id = 1L,
        name = "Test Food",
        brand = null,

        servingSize = servingSize,
        servingUnit = unit,

        gramsPerServingUnit = gramsPerUnit,
        mlPerServingUnit = mlPerUnit,

        servingsPerPackage = null,

        isRecipe = false
    )

    // --------------------------------------------------
    // gramsPerServingUnitResolved()
    // --------------------------------------------------

    @Test
    fun grams_unit_returns_serving_size() {
        val food = food(
            servingSize = 30.0,
            unit = ServingUnit.G
        )

        val result = food.gramsPerServingUnitResolved()

        assertEquals(30.0, result)
    }

    @Test
    fun ml_unit_returns_bridge_value() {
        val food = food(
            servingSize = 1.0,
            unit = ServingUnit.ML,
            gramsPerUnit = 1.03
        )

        val result = food.gramsPerServingUnitResolved()

        assertEquals(1.03, result)
    }

    @Test
    fun ml_unit_without_bridge_returns_null() {
        val food = food(
            servingSize = 1.0,
            unit = ServingUnit.ML,
            gramsPerUnit = null
        )

        val result = food.gramsPerServingUnitResolved()

        assertNull(result)
    }

    @Test
    fun arbitrary_unit_returns_bridge_value() {
        val food = food(
            servingSize = 2.0,
            unit = ServingUnit.TBSP,
            gramsPerUnit = 18.5
        )

        val result = food.gramsPerServingUnitResolved()

        assertEquals(18.5, result)
    }

    @Test
    fun arbitrary_unit_without_bridge_returns_null() {
        val food = food(
            servingSize = 2.0,
            unit = ServingUnit.TBSP,
            gramsPerUnit = null
        )

        val result = food.gramsPerServingUnitResolved()

        assertNull(result)
    }

    // --------------------------------------------------
    // gramsPerServingResolved()
    // --------------------------------------------------

    @Test
    fun grams_per_serving_multiplies_unit_by_serving_size() {
        val food = food(
            servingSize = 2.0,
            unit = ServingUnit.TBSP,
            gramsPerUnit = 18.5
        )

        val result = food.gramsPerServingResolved()

        assertEquals(37.0, result)
    }

    @Test
    fun grams_per_serving_returns_null_when_bridge_missing() {
        val food = food(
            servingSize = 2.0,
            unit = ServingUnit.TBSP,
            gramsPerUnit = null
        )

        val result = food.gramsPerServingResolved()

        assertNull(result)
    }

    @Test
    fun grams_per_serving_returns_null_when_serving_size_zero() {
        val food = food(
            servingSize = 0.0,
            unit = ServingUnit.TBSP,
            gramsPerUnit = 18.5
        )

        val result = food.gramsPerServingResolved()

        assertNull(result)
    }

    @Test
    fun grams_per_serving_returns_null_when_bridge_zero() {
        val food = food(
            servingSize = 2.0,
            unit = ServingUnit.TBSP,
            gramsPerUnit = 0.0
        )

        val result = food.gramsPerServingResolved()

        assertNull(result)
    }

    @Test
    fun grams_unit_double_multiplies_serving_size() {
        val food = food(
            servingSize = 30.0,
            unit = ServingUnit.G
        )

        val result = food.gramsPerServingResolved()

        // gramsPerUnitResolved = 30
        // gramsPerServingResolved = 30 * 30
        assertEquals(900.0, result)
    }

    @Test
    fun tiny_values_remain_precise() {
        val food = food(
            servingSize = 0.5,
            unit = ServingUnit.TBSP,
            gramsPerUnit = 0.001
        )

        val result = food.gramsPerServingResolved()

        assertEquals(0.0005, result)
    }
}