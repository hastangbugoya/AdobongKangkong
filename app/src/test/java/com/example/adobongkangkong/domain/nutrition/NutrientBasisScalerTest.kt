package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.data.local.db.entity.BasisType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.junit.JUnitAsserter.assertTrue

class NutrientBasisScalerTest {

    @Test
    fun bok_choy_regression_per_1lb_round_trip_per100g_and_back() {
        // Bok Choy: 59 kcal per 1 lb
        val servingSize = 1.0
        val gramsPerServingUnit = 453.59237
        val caloriesPerServing = 59.0

        // Convert UI per-serving -> canonical PER_100G
        val toCanonical = NutrientBasisScaler.displayPerServingToCanonical(
            uiPerServingAmount = caloriesPerServing,
            canonicalBasis = BasisType.PER_100G,
            servingSize = servingSize,
            gramsPerServingUnit = gramsPerServingUnit
        )

        assertTrue(toCanonical.didScale)

        // Expected: 59 * 100 / 453.59237 = 13.007273468907776...
        val expectedPer100g = 59.0 * 100.0 / 453.59237
        assertEquals(expectedPer100g, toCanonical.amount, 1e-12)

        // Convert canonical PER_100G -> UI per-serving
        val toDisplay = NutrientBasisScaler.canonicalToDisplayPerServing(
            storedAmount = toCanonical.amount,
            storedBasis = BasisType.PER_100G,
            servingSize = servingSize,
            gramsPerServingUnit = gramsPerServingUnit
        )

        assertTrue(toDisplay.didScale)
        assertEquals(caloriesPerServing, toDisplay.amount, 1e-9)
    }

    @Test
    fun per100g_per_serving_30g_serving() {
        val servingSize = 1.0
        val gramsPerServingUnit = 30.0
        val storedPer100g = 10.0 // e.g., 10g protein per 100g

        val toDisplay = NutrientBasisScaler.canonicalToDisplayPerServing(
            storedAmount = storedPer100g,
            storedBasis = BasisType.PER_100G,
            servingSize = servingSize,
            gramsPerServingUnit = gramsPerServingUnit
        )

        assertTrue(toDisplay.didScale)
        // 10 per 100g -> for 30g serving = 10 * 30 / 100 = 3
        assertEquals(3.0, toDisplay.amount, 1e-12)
    }

    @Test
    fun missing_grams_per100g_should_not_scale() {
        val toDisplay = NutrientBasisScaler.canonicalToDisplayPerServing(
            storedAmount = 13.0,
            storedBasis = BasisType.PER_100G,
            servingSize = 1.0,
            gramsPerServingUnit = null
        )

        assertFalse(toDisplay.didScale)
        assertEquals(13.0, toDisplay.amount, 0.0)
    }

    @Test
    fun usda_reported_serving_should_not_scale() {
        val toDisplay = NutrientBasisScaler.canonicalToDisplayPerServing(
            storedAmount = 59.0,
            storedBasis = BasisType.USDA_REPORTED_SERVING,
            servingSize = 1.0,
            gramsPerServingUnit = 453.59237
        )

        assertFalse(toDisplay.didScale)
        assertEquals(59.0, toDisplay.amount, 0.0)
    }

    @Test
    fun cup_volume_regression_per_100ml_round_trip() {
        // Example: 30 kcal per 1 cup
        // Assumption: 1 US cup = 240 ml
        val servingSize = 1.0
        val mlPerServingUnit = 240.0
        val caloriesPerCup = 30.0

        // UI per-cup -> canonical PER_100ML
        val toCanonical = NutrientBasisScaler.displayPerServingToCanonicalVolume(
            uiPerServingAmount = caloriesPerCup,
            canonicalBasis = BasisType.PER_100ML,
            servingSize = servingSize,
            mlPerServingUnit = mlPerServingUnit
        )

        assertTrue(toCanonical.didScale)

        val expectedPer100ml = 30.0 * 100.0 / 240.0
        assertEquals(expectedPer100ml, toCanonical.amount, 1e-12)

        // canonical PER_100ML -> UI per-cup
        val toDisplay = NutrientBasisScaler.canonicalToDisplayPerServingVolume(
            storedAmount = toCanonical.amount,
            storedBasis = BasisType.PER_100ML,
            servingSize = servingSize,
            mlPerServingUnit = mlPerServingUnit
        )

        assertTrue(toDisplay.didScale)
        assertEquals(caloriesPerCup, toDisplay.amount, 1e-9)
    }

    @Test
    fun per100ml_to_half_cup_scales_correctly() {
        // 8 kcal per 100 ml
        // Half cup = 120 ml
        val servingSize = 0.5
        val mlPerServingUnit = 240.0
        val storedPer100ml = 8.0

        val toDisplay = NutrientBasisScaler.canonicalToDisplayPerServingVolume(
            storedAmount = storedPer100ml,
            storedBasis = BasisType.PER_100ML,
            servingSize = servingSize,
            mlPerServingUnit = mlPerServingUnit
        )

        assertTrue(toDisplay.didScale)
        // 8 * 120 / 100 = 9.6
        assertEquals(9.6, toDisplay.amount, 1e-12)
    }

    @Test
    fun ui_contract_editor_and_list_show_same_per_serving_values_after_canonical_storage() {
        val servingSize = 1.0
        val gramsPerServingUnit = 453.59237 // 1 lb

        data class Macro(val name: String, val perServing: Double)

        val input = listOf(
            Macro("kcal", 59.0),
            Macro("carbs_g", 10.0),
            Macro("protein_g", 6.8),
            Macro("fat_g", 1.0),
        )

        for (m in input) {
            val toCanonical = NutrientBasisScaler.displayPerServingToCanonical(
                uiPerServingAmount = m.perServing,
                canonicalBasis = BasisType.PER_100G,
                servingSize = servingSize,
                gramsPerServingUnit = gramsPerServingUnit
            )
            assertTrue(toCanonical.didScale, "Expected scaling to canonical for ${m.name}")

            val toDisplay = NutrientBasisScaler.canonicalToDisplayPerServing(
                storedAmount = toCanonical.amount,
                storedBasis = BasisType.PER_100G,
                servingSize = servingSize,
                gramsPerServingUnit = gramsPerServingUnit
            )
            assertTrue(toDisplay.didScale, "Expected scaling back to display for ${m.name}")

            assertEquals(
                expected = m.perServing,
                actual = toDisplay.amount,
                absoluteTolerance = 1e-9,
                message = "Round-trip failed for ${m.name}"
            )
        }
    }



}