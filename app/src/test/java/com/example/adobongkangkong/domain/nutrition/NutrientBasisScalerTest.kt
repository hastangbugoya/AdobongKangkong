package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.data.local.db.entity.BasisType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [NutrientBasisScaler].
 *
 * ## Purpose
 * Lock down the exact conversion contract between:
 * - canonical storage (PER_100G / PER_100ML), and
 * - UI per-serving display amounts.
 *
 * ## Rationale (why these tests exist)
 * Nutrient scaling is a core correctness boundary. Small mistakes here silently corrupt:
 * - food editor displays,
 * - saved canonical nutrient values,
 * - macro totals,
 * - recipe and batch computations,
 * - historical snapshot correctness.
 *
 * These tests explicitly validate:
 * - round-trip correctness (UI → canonical → UI),
 * - “no-scale” behavior when scaling is unsafe or not applicable,
 * - volume-only scaling behavior (PER_100ML family),
 * - agreed-upon importer conventions (e.g., fl oz → mL bridges).
 *
 * ## Architectural rule reinforced by these tests
 * - No grams ↔ mL conversions (density guessing) are permitted.
 * - If required grounding is missing, scaling must not occur and didScale must be false.
 *
 * ## Notes
 * - Tolerances are intentionally tight to catch regressions early.
 * - Any change in these expectations must be treated as a product decision + migration (not a refactor).
 */
class NutrientBasisScalerTest {

    /**
     * Regression: Bok Choy label example must round-trip perfectly for PER_100G.
     *
     * ## Input
     * - servingSize = 1.0
     * - gramsPerServingUnit = 453.59237 (1 lb)
     * - UI calories per serving = 59.0 kcal
     *
     * ## Expected
     * - UI → canonical PER_100G scales:
     *     59 * 100 / 453.59237 = 13.007273468907776...
     * - canonical → UI returns exactly 59.0 kcal (within tolerance)
     *
     * ## Why this exists
     * This is the canonical “round-trip proof” that ensures:
     * - PER_100G storage preserves UI-visible label values,
     * - food editor and list views can show consistent per-serving values
     *   even after canonicalization.
     *
     * This regression case was explicitly agreed upon by dev + assistant as a correctness baseline.
     */
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

    /**
     * Basic correctness: PER_100G scales to a 30g serving as expected.
     *
     * ## Input
     * - storedPer100g = 10.0 (e.g., 10g protein per 100g)
     * - servingSize = 1.0
     * - gramsPerServingUnit = 30.0 (serving is 30g)
     *
     * ## Expected
     * - display = 10 * 30 / 100 = 3.0
     *
     * ## Why this exists
     * This is a minimal sanity check that protects against accidental inversions
     * (multiplying when you should divide, etc.).
     */
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

    /**
     * Contract: Missing grams grounding must not scale PER_100G.
     *
     * ## Input
     * - storedBasis = PER_100G
     * - gramsPerServingUnit = null
     *
     * ## Expected
     * - didScale == false
     * - amount unchanged
     *
     * ## Why this exists
     * This enforces the “truth-first” rule:
     * If mass grounding is missing, we do NOT invent it.
     */
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

    /**
     * Contract: USDA_REPORTED_SERVING is already per-serving and must not be rescaled.
     *
     * ## Input
     * - storedBasis = USDA_REPORTED_SERVING
     * - storedAmount = 59.0
     *
     * ## Expected
     * - didScale == false
     * - amount unchanged (59.0)
     *
     * ## Why this exists
     * Snapshot logs and some imports store already-per-serving values under this basis.
     * Rescaling would double-apply serving conversions and corrupt display and totals.
     */
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

    /**
     * Regression: PER_100ML round-trip for 1 cup (240 mL) must be correct.
     *
     * ## Input
     * - servingSize = 1.0 (1 cup)
     * - mlPerServingUnit = 240.0 (1 cup = 240 mL)
     * - UI calories per cup = 30.0
     *
     * ## Expected
     * - UI → canonical PER_100ML:
     *     30 * 100 / 240 = 12.5 per 100 mL
     * - canonical → UI returns exactly 30.0 (within tolerance)
     *
     * ## Why this exists
     * This locks down the volume scaling family independently from grams scaling.
     * It also enforces the “no density guessing” separation: this is pure mL math.
     */
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

    /**
     * Correctness: PER_100ML scales to half-cup (120 mL) correctly when servingSize scales the unit.
     *
     * ## Input
     * - storedPer100ml = 8.0
     * - servingSize = 0.5
     * - mlPerServingUnit = 240.0 (1 cup = 240 mL)
     *
     * ## Expected
     * - half-cup mL = 0.5 * 240 = 120 mL
     * - display = 8 * 120 / 100 = 9.6
     *
     * ## Why this exists
     * This confirms servingSize participates in the ml-per-serving computation and is not ignored.
     */
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

    /**
     * Contract: Missing ml grounding must not scale PER_100ML.
     *
     * ## Input
     * - storedBasis = PER_100ML
     * - mlPerServingUnit = null
     *
     * ## Expected
     * - didScale == false
     * - amount unchanged
     *
     * ## Why this exists
     * This mirrors the grams contract: if volume grounding is missing, we do not invent it.
     */
    @Test
    fun missing_ml_per100ml_should_not_scale() {
        val toDisplay = NutrientBasisScaler.canonicalToDisplayPerServingVolume(
            storedAmount = 8.0,
            storedBasis = BasisType.PER_100ML,
            servingSize = 1.0,
            mlPerServingUnit = null
        )

        assertFalse(toDisplay.didScale)
        assertEquals(8.0, toDisplay.amount, 0.0)
    }

    /**
     * UI contract regression: editor and list must display the same per-serving values after canonical storage.
     *
     * ## Input
     * - servingSize = 1.0
     * - gramsPerServingUnit = 453.59237 (1 lb)
     * - multiple macro-like values provided as per-serving UI inputs
     *
     * ## Expected
     * For each macro:
     * - UI → canonical PER_100G must scale (didScale == true)
     * - canonical → UI must scale back (didScale == true)
     * - round-trip UI value must equal original per-serving input (within tolerance)
     *
     * ## Why this exists
     * This protects the most important UX guarantee:
     * - Once a user enters per-serving values and the app stores canonical PER_100G,
     *   every screen must show the same per-serving values (no drift).
     *
     * This behavior was explicitly agreed upon by dev + assistant as the stable contract.
     */
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

    /**
     * Importer convention regression: label serving “8 fl oz = 240 mL” must round-trip under PER_100ML.
     *
     * ## Input (persisted model after importer fix)
     * - servingSize = 8.0 (label serving is “8 fl oz”)
     * - mlPerServingUnit = 30.0 (mL per 1 fl oz, rounded bridge)
     * - caloriesPerServing = 46.0 (per label serving)
     *
     * ## Expected
     * - total mL per serving = 8 * 30 = 240
     * - UI → canonical PER_100ML:
     *     46 * 100 / 240 = 19.166666666666668
     * - canonical → UI returns exactly 46.0 (within tolerance)
     *
     * ## Why this exists
     * This case is not intuitive because servingSize is not “1 cup” but “8 fl oz”.
     * The agreed contract is:
     * - servingSize represents the *count* of serving units in the displayed serving,
     * - mlPerServingUnit bridges one unit (1 fl oz) to milliliters.
     *
     * This locks down the importer + UI scaling alignment agreed upon by dev + assistant.
     */
    @Test
    fun fl_oz_label_serving_round_trip_per100ml_8_floz_equals_240ml() {
        // Orange juice label serving: 8 fl oz = 240 mL (using your rounded bridge)
        // Persisted model after importer fix:
        // - servingSize = 8 (serving is the label serving)
        // - mlPerServingUnit = 30 (mL per 1 fl oz)
        val servingSize = 8.0
        val mlPerServingUnit = 30.0
        val caloriesPerServing = 46.0 // USDA per label serving (8 fl oz / 240 mL)

        // UI per-serving -> canonical PER_100ML
        val toCanonical = NutrientBasisScaler.displayPerServingToCanonicalVolume(
            uiPerServingAmount = caloriesPerServing,
            canonicalBasis = BasisType.PER_100ML,
            servingSize = servingSize,
            mlPerServingUnit = mlPerServingUnit
        )

        assertTrue(toCanonical.didScale)

        // Expected: 46 * 100 / (8*30) = 19.166666666666668
        val expectedPer100ml = caloriesPerServing * 100.0 / (servingSize * mlPerServingUnit)
        assertEquals(expectedPer100ml, toCanonical.amount, 1e-12)

        // canonical PER_100ML -> UI per-serving (8 fl oz)
        val toDisplay = NutrientBasisScaler.canonicalToDisplayPerServingVolume(
            storedAmount = toCanonical.amount,
            storedBasis = BasisType.PER_100ML,
            servingSize = servingSize,
            mlPerServingUnit = mlPerServingUnit
        )

        assertTrue(toDisplay.didScale)
        assertEquals(caloriesPerServing, toDisplay.amount, 1e-9)
    }

    /**
     * Volume scaling sanity: per-100mL canonical value must scale to a single fl oz (30 mL) correctly.
     *
     * ## Input
     * - servingSize = 1.0 (1 fl oz unit)
     * - mlPerServingUnit = 30.0 (bridge)
     * - storedPer100ml = 19.166666666666668 (from previous test)
     *
     * ## Expected
     * - display = 19.166666666666668 * 30 / 100 = 5.75
     *
     * ## Why this exists
     * Confirms the “unit bridge” model remains consistent when switching between:
     * - label serving (8 units) and
     * - single unit display (1 unit).
     */
    @Test
    fun fl_oz_single_unit_display_from_per100ml_scales_to_1_floz() {
        // If canonical is truly per 100 mL, then 1 fl oz (~30 mL bridge) should be 0.3x of per-100mL.
        val servingSize = 1.0
        val mlPerServingUnit = 30.0
        val storedPer100ml = 19.166666666666668 // from the previous test's expected per100ml

        val toDisplay = NutrientBasisScaler.canonicalToDisplayPerServingVolume(
            storedAmount = storedPer100ml,
            storedBasis = BasisType.PER_100ML,
            servingSize = servingSize,
            mlPerServingUnit = mlPerServingUnit
        )

        assertTrue(toDisplay.didScale)
        // 19.1667 * 30 / 100 = 5.75 kcal per 1 fl oz
        assertEquals(5.75, toDisplay.amount, 1e-9)
    }
}

/**
 * FUTURE-AI / MAINTENANCE KDoc (Do not remove)
 *
 * ## Invariants (must not change)
 * - These tests define the contract for [NutrientBasisScaler]. If they fail, scaling semantics changed.
 * - Tight tolerances are intentional; rounding drift is treated as a regression.
 * - The volume tests assume the “bridge unit” model:
 *   - servingSize is a count of serving units,
 *   - (g/ml)PerServingUnit is per ONE unit.
 *
 * ## Suggested additional tests (future)
 * 1) servingSize <= 0 should not scale (didScale=false) for both grams and ml paths.
 * 2) gramsPerServingUnit <= 0 should not scale (didScale=false) for PER_100G.
 * 3) mlPerServingUnit <= 0 should not scale (didScale=false) for PER_100ML.
 * 4) PER_100ML passed into canonicalToDisplayPerServing (mass path) must not scale.
 * 5) PER_100G passed into canonicalToDisplayPerServingVolume (volume path) must not scale.
 * 6) Symmetry tests for multiple random values:
 *    - pick a gramsPerServing + ui value, round-trip, assert equality.
 *    - pick a mlPerServing + ui value, round-trip, assert equality.
 * 7) Large values + small values:
 *    - very small nutrients (e.g., 0.0001) and large servings should remain stable.
 * 8) Multi-nutrient deterministic behavior:
 *    - verify a list/map of nutrients round-trips without per-item divergence.
 *
 * ## Do not refactor notes
 * - Do not loosen tolerances unless there is a deliberate product decision and corresponding math change.
 * - If changing serving model semantics, update tests first to document the new contract explicitly.
 */