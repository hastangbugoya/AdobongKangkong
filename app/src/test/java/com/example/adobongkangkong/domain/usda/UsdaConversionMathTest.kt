package com.example.adobongkangkong.domain.usda

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.abs

class UsdaConversionMathTest {

    private fun assertNearlyEquals(expected: Double, actual: Double, eps: Double = 1e-9) {
        if (abs(expected - actual) > eps) {
            throw AssertionError("Expected $expected but was $actual (eps=$eps)")
        }
    }

    @Test
    fun nutella_serving_grounding_2_tbsp_37g_bridge_is_18_5g_per_tbsp() {
        val gramsTotal = 37.0
        val householdSize = 2.0 // "2 tbsp"
        val gramsPerServingUnit = gramsTotal / householdSize

        assertNearlyEquals(18.5, gramsPerServingUnit)
        val gramsPerServingResolved = householdSize * gramsPerServingUnit
        assertNearlyEquals(37.0, gramsPerServingResolved)
    }

    @Test
    fun perServing_to_per100g_conversion_is_correct_for_macros() {
        val gramsPerServing = 37.0
        val factor = 100.0 / gramsPerServing

        // USDA per serving label values (example)
        val caloriesPerServing = 200.0
        val proteinPerServing = 2.0
        val carbsPerServing = 21.0
        val fatPerServing = 11.0

        val caloriesPer100g = caloriesPerServing * factor
        val proteinPer100g = proteinPerServing * factor
        val carbsPer100g = carbsPerServing * factor
        val fatPer100g = fatPerServing * factor

        // 200 * (100/37) = 540.540540...
        assertNearlyEquals(540.5405405405405, caloriesPer100g, eps = 1e-12)
        assertNearlyEquals(5.405405405405405, proteinPer100g, eps = 1e-12)
        assertNearlyEquals(56.75675675675676, carbsPer100g, eps = 1e-12)
        assertNearlyEquals(29.72972972972973, fatPer100g, eps = 1e-12)
    }

    @Test
    fun round_trip_invariant_holds_per100g_to_perGram_back_to_perServing() {
        val gramsPerServing = 37.0
        val factor = 100.0 / gramsPerServing

        val perServing = mapOf(
            "CALORIES_KCAL" to 200.0,
            "PROTEIN_G" to 2.0,
            "CARBS_G" to 21.0,
            "FAT_G" to 11.0
        )

        perServing.forEach { (code, amtPerServing) ->
            val per100g = amtPerServing * factor
            val perGram = per100g / 100.0
            val roundTrip = perGram * gramsPerServing

            assertNearlyEquals(
                amtPerServing,
                roundTrip,
                eps = 1e-9
            )
        }
    }

    @Test
    fun edge_case_servingSize_is_1_unit_bridge_is_total_grams() {
        // "1 bar (45 g)" style
        val gramsTotal = 45.0
        val householdSize = 1.0
        val gramsPerServingUnit = gramsTotal / householdSize

        assertEquals(45.0, gramsPerServingUnit)
        val gramsPerServingResolved = householdSize * gramsPerServingUnit
        assertEquals(45.0, gramsPerServingResolved)
    }
}