package com.example.adobongkangkong.domain.recipes

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RecipeBatchSnapshotMathTest {

    private val CAL = NutrientKey("CALORIES_KCAL")
    private val PRO = NutrientKey("PROTEIN_G")

    @Test
    fun `per100g scales inversely with cookedYieldGrams`() {
        val totals = NutrientMap(
            mapOf(
                CAL to 1000.0,   // kcal in whole recipe
                PRO to 100.0     // g in whole recipe
            )
        )

        val r1 = RecipeBatchSnapshotMath.compute(
            totals = totals,
            cookedYieldGrams = 1000.0,
            servingsYieldUsed = null
        )
        // 1000 kcal / 1000g * 100 = 100 kcal per 100g
        assertEquals(100.0, r1.per100g[CAL], 1e-9)
        // 100 g / 1000g * 100 = 10 g per 100g
        assertEquals(10.0, r1.per100g[PRO], 1e-9)

        val r2 = RecipeBatchSnapshotMath.compute(
            totals = totals,
            cookedYieldGrams = 2000.0,
            servingsYieldUsed = null
        )
        // doubling cooked yield halves density
        assertEquals(50.0, r2.per100g[CAL], 1e-9)
        assertEquals(5.0, r2.per100g[PRO], 1e-9)
    }

    @Test
    fun `gramsPerServingCooked computed from cookedYieldGrams and servingsYieldUsed`() {
        val totals = NutrientMap(mapOf(CAL to 1000.0))

        val r = RecipeBatchSnapshotMath.compute(
            totals = totals,
            cookedYieldGrams = 800.0,
            servingsYieldUsed = 4.0
        )

        assertEquals(200.0, r.gramsPerServingCooked!!, 1e-9)
    }

    @Test
    fun `gramsPerServingCooked is null when servingsYieldUsed null or invalid`() {
        val totals = NutrientMap(mapOf(CAL to 1000.0))

        val rNull = RecipeBatchSnapshotMath.compute(
            totals = totals,
            cookedYieldGrams = 800.0,
            servingsYieldUsed = null
        )
        assertNull(rNull.gramsPerServingCooked)

        val rZero = RecipeBatchSnapshotMath.compute(
            totals = totals,
            cookedYieldGrams = 800.0,
            servingsYieldUsed = 0.0
        )
        assertNull(rZero.gramsPerServingCooked)

        val rNeg = RecipeBatchSnapshotMath.compute(
            totals = totals,
            cookedYieldGrams = 800.0,
            servingsYieldUsed = -2.0
        )
        assertNull(rNeg.gramsPerServingCooked)
    }

    @Test
    fun `throws when cookedYieldGrams not positive`() {
        val totals = NutrientMap(mapOf(CAL to 1000.0))

        assertThrows(IllegalArgumentException::class.java) {
            RecipeBatchSnapshotMath.compute(
                totals = totals,
                cookedYieldGrams = 0.0,
                servingsYieldUsed = 4.0
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            RecipeBatchSnapshotMath.compute(
                totals = totals,
                cookedYieldGrams = -1.0,
                servingsYieldUsed = 4.0
            )
        }
    }

    @Test
    fun `consistency - per100g equals perGram times 100`() {
        val totals = NutrientMap(mapOf(CAL to 900.0))
        val cookedYield = 600.0

        val r = RecipeBatchSnapshotMath.compute(
            totals = totals,
            cookedYieldGrams = cookedYield,
            servingsYieldUsed = null
        )

        val expected = (900.0 / cookedYield) * 100.0
        assertEquals(expected, r.per100g[CAL], 1e-9)
    }
}