package com.example.adobongkangkong.data.csvimport

import com.example.adobongkangkong.domain.model.ServingUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * =============================================================================
 * CsvUnits TESTS — CONTRACT LOCK
 * =============================================================================
 *
 * These tests enforce:
 *
 * 1) Serving unit precedence:
 *    serv → weight → OTHER
 *
 * 2) Deterministic parsing:
 *    - no guessing
 *    - no density inference
 *
 * 3) Regression protection:
 *    - LB must NEVER become OTHER when present in weight
 *
 * -----------------------------------------------------------------------------
 * ⚠️ DO NOT WEAKEN THESE TESTS
 * -----------------------------------------------------------------------------
 *
 * If these fail:
 * - your importer is broken
 * - your DB will silently corrupt unit semantics
 *
 * =============================================================================
 */
class CsvUnitsTest {

    // -------------------------------------------------------------------------
    // resolveServingUnit — CORE CONTRACT
    // -------------------------------------------------------------------------

    @Test
    fun `serv takes precedence over weight`() {
        val result = CsvUnits.resolveServingUnit("cup", "200g")
        assertEquals(ServingUnit.CUP, result)
    }

    @Test
    fun `serv lb overrides weight`() {
        val result = CsvUnits.resolveServingUnit("lb", "200g")
        assertEquals(ServingUnit.LB, result)
    }

    @Test
    fun `fallback to weight when serv is null - lb`() {
        val result = CsvUnits.resolveServingUnit(null, "1lb")
        assertEquals(ServingUnit.LB, result)
    }

    @Test
    fun `fallback to weight when serv is null - lbs plural`() {
        val result = CsvUnits.resolveServingUnit(null, "1lbs")
        assertEquals(ServingUnit.LB, result)
    }

    @Test
    fun `fallback to weight when serv is null - grams`() {
        val result = CsvUnits.resolveServingUnit(null, "165g")
        assertEquals(ServingUnit.G, result)
    }

    @Test
    fun `fallback to weight when serv is null - oz`() {
        val result = CsvUnits.resolveServingUnit(null, "8oz")
        assertEquals(ServingUnit.OZ, result)
    }

    @Test
    fun `fallback to weight when serv is null - ml`() {
        val result = CsvUnits.resolveServingUnit(null, "400ml")
        assertEquals(ServingUnit.ML, result)
    }

    @Test
    fun `fallback to OTHER when both serv and weight unusable`() {
        val result = CsvUnits.resolveServingUnit(null, null)
        assertEquals(ServingUnit.OTHER, result)
    }

    @Test
    fun `serv OTHER but weight valid should use weight`() {
        val result = CsvUnits.resolveServingUnit("unknown", "1lb")
        assertEquals(ServingUnit.LB, result)
    }

    // -------------------------------------------------------------------------
    // parseWeightUnit — identity extraction
    // -------------------------------------------------------------------------

    @Test
    fun `parseWeightUnit lb`() {
        assertEquals(ServingUnit.LB, CsvUnits.parseWeightUnit("1lb"))
    }

    @Test
    fun `parseWeightUnit lbs plural`() {
        assertEquals(ServingUnit.LB, CsvUnits.parseWeightUnit("1lbs"))
    }

    @Test
    fun `parseWeightUnit grams`() {
        assertEquals(ServingUnit.G, CsvUnits.parseWeightUnit("165g"))
    }

    @Test
    fun `parseWeightUnit oz`() {
        assertEquals(ServingUnit.OZ, CsvUnits.parseWeightUnit("8oz"))
    }

    @Test
    fun `parseWeightUnit ml`() {
        assertEquals(ServingUnit.ML, CsvUnits.parseWeightUnit("400ml"))
    }

    @Test
    fun `parseWeightUnit unknown returns null`() {
        val result = CsvUnits.parseWeightUnit("abc")
        assertEquals(null, result)
    }

    // -------------------------------------------------------------------------
    // parseWeightToGrams — numeric correctness
    // -------------------------------------------------------------------------

    @Test
    fun `parseWeightToGrams lb`() {
        val grams = CsvUnits.parseWeightToGrams("1lb").grams
        assertEquals(453.59237, grams!!, 0.0001)
    }

    @Test
    fun `parseWeightToGrams lbs plural`() {
        val grams = CsvUnits.parseWeightToGrams("1lbs").grams
        assertEquals(453.59237, grams!!, 0.0001)
    }

    @Test
    fun `parseWeightToGrams grams`() {
        val grams = CsvUnits.parseWeightToGrams("165g").grams
        assertEquals(165.0, grams!!, 0.0001)
    }

    @Test
    fun `parseWeightToGrams oz`() {
        val grams = CsvUnits.parseWeightToGrams("8oz").grams
        assertEquals(226.796185, grams!!, 0.0001)
    }

    @Test
    fun `parseWeightToGrams volume returns null`() {
        val grams = CsvUnits.parseWeightToGrams("400ml").grams
        assertEquals(null, grams)
    }

    // -------------------------------------------------------------------------
    // parseServingUnit — explicit mapping only
    // -------------------------------------------------------------------------

    @Test
    fun `parseServingUnit lb`() {
        assertEquals(ServingUnit.LB, CsvUnits.parseServingUnit("lb"))
    }

    @Test
    fun `parseServingUnit pound`() {
        assertEquals(ServingUnit.LB, CsvUnits.parseServingUnit("pound"))
    }

    @Test
    fun `parseServingUnit cup`() {
        assertEquals(ServingUnit.CUP, CsvUnits.parseServingUnit("cup"))
    }

    @Test
    fun `parseServingUnit tbsp`() {
        assertEquals(ServingUnit.TBSP, CsvUnits.parseServingUnit("tbsp"))
    }

    @Test
    fun `parseServingUnit unknown returns OTHER`() {
        assertEquals(ServingUnit.OTHER, CsvUnits.parseServingUnit("???"))
    }

    // -------------------------------------------------------------------------
    // REGRESSION TEST — YOUR ORIGINAL BUG
    // -------------------------------------------------------------------------

    @Test
    fun `regression - lb must not become OTHER when only weight is present`() {
        val result = CsvUnits.resolveServingUnit(null, "1lb")
        assertEquals(
            "LB was incorrectly mapped to OTHER — regression detected",
            ServingUnit.LB,
            result
        )
    }
}