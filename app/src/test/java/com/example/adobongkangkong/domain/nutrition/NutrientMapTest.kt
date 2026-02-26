package com.example.adobongkangkong.domain.nutrition

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test suite for [NutrientMap] and its companion extension [NutrientMap.dividedBy].
 *
 * ## Purpose
 * “Cements” [NutrientMap] as a core domain primitive by locking in the behavioral
 * contracts that the rest of the nutrition system depends on:
 *
 * - Safe lookup: missing nutrients never crash and always return 0.0
 * - Immutability: operations never mutate inputs or backing maps
 * - Deterministic math: add/scale/divide operations produce correct totals
 * - Persistence bridge: toCodeMap()/fromCodeMap() round-trip without loss
 * - Algebraic expectations: addition + scaling follow predictable identities
 *
 * ## Rationale
 * In AdobongKangkong, [NutrientMap] is the foundation for:
 *
 * - immutable log snapshots (historical correctness)
 * - recipe math and batch nutrition
 * - planner totals and projections
 * - dashboard macros and heatmap calculations
 * - importer normalization and persistence
 *
 * If [NutrientMap] is wrong, *everything* downstream is wrong.
 * These tests are therefore “core safety rails”, not optional coverage.
 *
 * ## Key contracts validated here
 *
 * - Missing keys → return 0.0
 * - Addition merges maps and sums overlaps
 * - Scaling multiplies every value (including negatives/zeros)
 * - dividedBy() validates divisor and is equivalent to scaling by reciprocal
 * - Inputs are never mutated (including backing MutableMap instances)
 * - toCodeMap()/fromCodeMap() preserve exact codes and values
 *
 * ## Important gotcha: @JvmInline value class
 * [NutrientMap] is a @JvmInline value class. In JVM tests, boxing/unboxing can occur,
 * so identity checks (assertSame) are NOT reliable.
 *
 * This suite intentionally asserts *value semantics* (contents), not identity.
 *
 * ## Rules
 * - These tests MUST always pass.
 * - Do NOT “fix” failures by loosening tolerances or bypassing assertions.
 * - If a failure occurs, treat it as a domain correctness regression and fix
 *   the production code (or explicitly document why the contract changed).
 *
 * ## Suggested post–first-production regression additions
 * After the first production release (when real data exists), add:
 *
 * - A frozen “real meal day” fixture:
 *   - Persist a known set of foods/recipes
 *   - Aggregate totals
 *   - Assert exact macro totals and a handful of micronutrients
 *
 * - A “snapshot immutability” regression:
 *   - Take a NutrientMap snapshot used in a LogEntry
 *   - Change Food nutrients in DB
 *   - Ensure logged totals remain unchanged (no joins / no backflow)
 *
 * - A large-map performance regression:
 *   - Construct ~200 nutrient entries
 *   - Add and scale repeatedly
 *   - Ensure execution stays within a reasonable threshold on CI
 */
class NutrientMapTest {

    private val K1 = NutrientKey("K1")
    private val K2 = NutrientKey("K2")
    private val K3 = NutrientKey("K3")

    /**
     * Missing nutrient lookup must be safe and deterministic:
     * - never throws
     * - always returns 0.0
     */
    @Test
    fun get_missing_key_returns_zero_and_never_crashes() {
        val map = NutrientMap(mapOf(K1 to 10.0))
        assertEquals(0.0, map[K2], 0.0)
    }

    /**
     * Present nutrient lookup returns the exact stored value.
     */
    @Test
    fun get_present_key_returns_value() {
        val map = NutrientMap(mapOf(K1 to 10.0, K2 to 2.5))
        assertEquals(10.0, map[K1], 0.0)
        assertEquals(2.5, map[K2], 0.0)
    }

    /**
     * keys() should return the exact set of keys present.
     * This is used by debug screens and UI iteration over present nutrients.
     */
    @Test
    fun keys_returns_exact_key_set() {
        val map = NutrientMap(mapOf(K1 to 10.0, K2 to 2.5))
        assertEquals(setOf(K1, K2), map.keys())
    }

    /**
     * entries() must expose a stable key/value view of the underlying map.
     * This is used for mapping/export and debug printing.
     */
    @Test
    fun entries_returns_exact_entries() {
        val map = NutrientMap(mapOf(K1 to 10.0, K2 to 2.5))
        val entries = map.entries().associate { it.key to it.value }
        assertEquals(mapOf(K1 to 10.0, K2 to 2.5), entries)
    }

    /**
     * EMPTY must behave as a canonical empty container.
     */
    @Test
    fun isEmpty_reflects_underlying_map() {
        assertTrue(NutrientMap.EMPTY.isEmpty())
        assertFalse(NutrientMap(mapOf(K1 to 1.0)).isEmpty())
    }

    /**
     * scaledBy(1.0) is a content-preserving operation.
     *
     * NOTE: We do NOT assert identity here because NutrientMap is a @JvmInline value class
     * and may be boxed/unboxed across test boundaries.
     */
    @Test
    fun scaledBy_factor_one_is_value_equal_and_does_not_change_contents() {
        val map = NutrientMap(mapOf(K1 to 10.0, K2 to 2.5))
        val scaled = map.scaledBy(1.0)

        assertEquals(map.toCodeMap(), scaled.toCodeMap())
        assertEquals(10.0, scaled[K1], 0.0)
        assertEquals(2.5, scaled[K2], 0.0)
    }

    /**
     * scaledBy() must:
     * - multiply every present nutrient by the factor
     * - not mutate the original NutrientMap
     * - not mutate the backing MutableMap given at construction time
     */
    @Test
    fun scaledBy_scales_all_values_and_does_not_modify_original() {
        val backing = mutableMapOf(K1 to 10.0, K2 to 2.5)
        val original = NutrientMap(backing)

        val scaled = original.scaledBy(2.0)

        assertEquals(20.0, scaled[K1], 0.0)
        assertEquals(5.0, scaled[K2], 0.0)

        // Original must remain unchanged (immutability contract)
        assertEquals(10.0, original[K1], 0.0)
        assertEquals(2.5, original[K2], 0.0)
        assertEquals(mapOf(K1 to 10.0, K2 to 2.5), backing)
    }

    /**
     * Addition with EMPTY must preserve values.
     * EMPTY is used heavily in folds/aggregations across logs and recipes.
     */
    @Test
    fun plus_with_empty_preserves_values() {
        val a = NutrientMap(mapOf(K1 to 1.0))
        val b = NutrientMap.EMPTY

        val ab = a + b
        val ba = b + a

        assertEquals(a.toCodeMap(), ab.toCodeMap())
        assertEquals(a.toCodeMap(), ba.toCodeMap())
    }

    /**
     * Addition must:
     * - sum overlapping keys
     * - preserve non-overlapping keys from both sides
     */
    @Test
    fun plus_adds_overlapping_keys_and_keeps_non_overlapping_keys() {
        val a = NutrientMap(mapOf(K1 to 10.0, K2 to 2.0))
        val b = NutrientMap(mapOf(K2 to 3.5, K3 to 7.0))

        val out = a + b

        assertEquals(10.0, out[K1], 0.0)       // only in a
        assertEquals(5.5, out[K2], 1e-12)      // overlap sum
        assertEquals(7.0, out[K3], 0.0)        // only in b
    }

    /**
     * Addition values should be commutative:
     * a + b should produce the same per-key totals as b + a.
     *
     * (Implementation details may differ, but totals must match.)
     */
    @Test
    fun plus_is_commutative_for_values() {
        val a = NutrientMap(mapOf(K1 to 10.0, K2 to 2.0))
        val b = NutrientMap(mapOf(K2 to 3.5, K3 to 7.0))

        val ab = a + b
        val ba = b + a

        assertEquals(ab[K1], ba[K1], 0.0)
        assertEquals(ab[K2], ba[K2], 1e-12)
        assertEquals(ab[K3], ba[K3], 0.0)
    }

    /**
     * Addition must not mutate either input map, including backing MutableMap instances.
     *
     * This matters because NutrientMaps are reused in:
     * - planner folds
     * - recipe computations
     * - log aggregation
     *
     * Mutation would cause silent, cascading corruption of totals.
     */
    @Test
    fun plus_does_not_modify_inputs_or_backing_maps() {
        val backingA = mutableMapOf(K1 to 10.0, K2 to 2.0)
        val backingB = mutableMapOf(K2 to 3.5, K3 to 7.0)

        val a = NutrientMap(backingA)
        val b = NutrientMap(backingB)

        val ignored = a + b

        assertEquals(mapOf(K1 to 10.0, K2 to 2.0), backingA)
        assertEquals(mapOf(K2 to 3.5, K3 to 7.0), backingB)

        // sanity: output has expected values too
        assertEquals(10.0, ignored[K1], 0.0)
        assertEquals(5.5, ignored[K2], 1e-12)
        assertEquals(7.0, ignored[K3], 0.0)
    }

    /**
     * toCodeMap() must convert NutrientKey wrappers back into raw code strings
     * without losing data. This is the persistence/export bridge.
     */
    @Test
    fun toCodeMap_converts_keys_to_string_codes_exactly() {
        val map = NutrientMap(mapOf(NutrientKey("PROTEIN_G") to 6.8, NutrientKey("FAT_G") to 1.0))
        val out = map.toCodeMap()

        // Use getValue() so we get a Double (not Double?) and fail loudly if key missing.
        assertEquals(6.8, out.getValue("PROTEIN_G"), 0.0)
        assertEquals(1.0, out.getValue("FAT_G"), 0.0)
        assertEquals(2, out.size)
    }

    /**
     * asMap() is a convenience alias that must behave exactly like toCodeMap().
     */
    @Test
    fun asMap_is_alias_of_toCodeMap() {
        val map = NutrientMap(mapOf(K1 to 1.25))
        assertEquals(map.toCodeMap(), map.asMap())
    }

    /**
     * fromCodeMap() must reconstruct NutrientMap from persistence-format maps
     * without loss of codes or values.
     */
    @Test
    fun fromCodeMap_round_trip_preserves_codes_and_values() {
        val input = mapOf(
            "CALORIES_KCAL" to 59.0,
            "PROTEIN_G" to 6.8,
            "SODIUM_MG" to 120.0
        )

        val m = NutrientMap.fromCodeMap(input)
        val back = m.toCodeMap()

        assertEquals(input.size, back.size)
        assertEquals(59.0, back.getValue("CALORIES_KCAL"), 0.0)
        assertEquals(6.8, back.getValue("PROTEIN_G"), 0.0)
        assertEquals(120.0, back.getValue("SODIUM_MG"), 0.0)
    }

    /**
     * NutrientKey cannot be blank. Therefore fromCodeMap must fail fast if a blank code is present.
     */
    @Test
    fun fromCodeMap_blank_key_throws_via_NutrientKey_invariant() {
        val bad = mapOf("" to 1.0)
        assertFailsWith<IllegalArgumentException> {
            NutrientMap.fromCodeMap(bad)
        }
    }

    /**
     * dividedBy() must enforce its contract:
     * divisor must be > 0.0, otherwise throw.
     *
     * This prevents silent corruption of totals (division by zero / negative).
     */
    @Test
    fun dividedBy_requires_positive_divisor_and_throws_for_invalid() {
        val map = NutrientMap(mapOf(K1 to 10.0))
        assertFailsWith<IllegalArgumentException> { map.dividedBy(0.0) }
        assertFailsWith<IllegalArgumentException> { map.dividedBy(-1.0) }
    }

    /**
     * dividedBy() must scale values correctly using reciprocal multiplication.
     */
    @Test
    fun dividedBy_scales_correctly() {
        val map = NutrientMap(mapOf(K1 to 10.0, K2 to 2.5))
        val out = map.dividedBy(2.0)

        assertEquals(5.0, out[K1], 0.0)
        assertEquals(1.25, out[K2], 1e-12)
    }

    /**
     * dividedBy() must not mutate original maps or backing data structures.
     */
    @Test
    fun dividedBy_does_not_modify_original() {
        val backing = mutableMapOf(K1 to 10.0)
        val original = NutrientMap(backing)

        val out = original.dividedBy(2.0)

        assertEquals(10.0, original[K1], 0.0)
        assertEquals(5.0, out[K1], 0.0)
        assertEquals(mapOf(K1 to 10.0), backing)
    }

    /**
     * Distributive property check:
     *
     * (a + b) * factor == (a * factor) + (b * factor)
     *
     * This locks in predictable aggregation behavior for planner/day totals.
     */
    @Test
    fun plus_then_scaledBy_matches_scaledBy_then_plus_distribution_property() {
        val a = NutrientMap(mapOf(K1 to 10.0, K2 to 2.0))
        val b = NutrientMap(mapOf(K2 to 3.5, K3 to 7.0))
        val factor = 2.0

        val left = (a + b).scaledBy(factor)
        val right = a.scaledBy(factor) + b.scaledBy(factor)

        assertEquals(left[K1], right[K1], 0.0)
        assertEquals(left[K2], right[K2], 1e-12)
        assertEquals(left[K3], right[K3], 0.0)
    }

    /**
     * EMPTY must be safe for any key lookup:
     * always returns 0.0 and never throws.
     */
    @Test
    fun empty_map_lookup_returns_zero_for_any_key() {
        val empty = NutrientMap.EMPTY
        assertEquals(0.0, empty[K1], 0.0)
        assertEquals(0.0, empty[K2], 0.0)
        assertEquals(0.0, empty[NutrientKey("ANYTHING")], 0.0)
    }

    // -------------------------------------------------------------------------
    // Property-style “stress” tests (deterministic via fixed seeds)
    // -------------------------------------------------------------------------

    /**
     * Stress test for + operator against a reference implementation.
     *
     * Uses deterministic randomness (fixed seed) so failures are reproducible.
     *
     * Also explicitly validates the "missing keys return 0.0" rule on a fresh, absent key.
     */
    @Test
    fun property_plus_matches_reference_merge_for_random_inputs() {
        val rng = Random(12345)

        repeat(250) { iter ->
            val keys = List(12) { NutrientKey("K_${it}") }

            val aRaw = randomNutrientMap(rng, keys)
            val bRaw = randomNutrientMap(rng, keys)

            val a = NutrientMap(aRaw)
            val b = NutrientMap(bRaw)

            val out = a + b
            val expected = referencePlus(aRaw, bRaw)

            for ((k, v) in expected) {
                assertEquals(v, out[k], 1e-9)
            }

            // Validate safe lookup for a key that doesn't exist in either
            assertEquals(0.0, out[NutrientKey("NOT_PRESENT_$iter")], 0.0)
        }
    }

    /**
     * Stress test for scaledBy() against reference multiplication.
     *
     * Values include negatives and zeros to ensure there are no hidden assumptions.
     */
    @Test
    fun property_scaling_matches_reference_for_random_inputs() {
        val rng = Random(54321)
        val keys = List(10) { NutrientKey("S_${it}") }

        repeat(250) {
            val raw = randomNutrientMap(rng, keys)
            val factor = rng.nextDouble(from = -5.0, until = 5.0)

            val m = NutrientMap(raw)
            val scaled = m.scaledBy(factor)

            for ((k, v) in raw) {
                assertEquals(v * factor, scaled[k], 1e-9)
            }
        }
    }

    /**
     * Stress test: dividedBy(divisor) must equal scaledBy(1/divisor).
     *
     * Divisors are constrained to be positive (dividedBy enforces this).
     */
    @Test
    fun property_dividedBy_matches_scaledBy_inverse_for_random_inputs() {
        val rng = Random(24680)
        val keys = List(10) { NutrientKey("D_${it}") }

        repeat(250) {
            val raw = randomNutrientMap(rng, keys)
            val divisor = rng.nextDouble(from = 0.0001, until = 50.0)

            val m = NutrientMap(raw)
            val d = m.dividedBy(divisor)
            val s = m.scaledBy(1.0 / divisor)

            for ((k, _) in raw) {
                assertEquals(s[k], d[k], 1e-12)
            }
        }
    }

    /**
     * Stress test: toCodeMap() followed by fromCodeMap() must round-trip without loss.
     *
     * This protects persistence/export logic and is essential for:
     * - snapshot logging
     * - DB export/import
     * - migration safety
     */
    @Test
    fun property_toCodeMap_then_fromCodeMap_round_trips_for_random_inputs() {
        val rng = Random(13579)
        val keys = List(12) { NutrientKey("RT_${it}") }

        repeat(200) {
            val raw = randomNutrientMap(rng, keys)
            val m = NutrientMap(raw)

            val codes = m.toCodeMap()
            val back = NutrientMap.fromCodeMap(codes)

            assertEquals(codes, back.toCodeMap())
        }
    }

    /**
     * Helper to produce sparse nutrient maps with values spanning negative..positive.
     * This intentionally avoids assumptions about nutrient positivity in the math layer.
     */
    private fun randomNutrientMap(rng: Random, keys: List<NutrientKey>): Map<NutrientKey, Double> {
        val out = mutableMapOf<NutrientKey, Double>()
        for (k in keys) {
            if (rng.nextBoolean()) {
                val v = rng.nextDouble(from = -1000.0, until = 1000.0)
                out[k] = v
            }
        }
        return out
    }

    /**
     * Reference implementation of addition used to validate NutrientMap.plus().
     *
     * Behavior:
     * - For keys in a: out[k] = a[k] + (b[k] ?: 0)
     * - For keys only in b: out[k] = b[k]
     */
    private fun referencePlus(
        a: Map<NutrientKey, Double>,
        b: Map<NutrientKey, Double>
    ): Map<NutrientKey, Double> {
        val out = mutableMapOf<NutrientKey, Double>()
        for ((k, v) in a) out[k] = v + (b[k] ?: 0.0)
        for ((k, v) in b) if (k !in out) out[k] = v
        return out
    }
}

/**
 * =============================================================================
 * FOR FUTURE AI / FUTURE DEVELOPER — Test suite boundaries and invariants
 * =============================================================================
 *
 * Invariants
 *
 * - This test suite MUST always pass in CI.
 * - Do not weaken assertions to “make tests green.”
 *
 * If you change NutrientMap behavior, you must:
 * - update the KDoc contracts
 * - update these tests
 * - ensure downstream snapshot/log behavior remains correct
 *
 *
 * Non-negotiable contracts enforced here
 *
 * - Missing nutrient lookup MUST return 0.0
 * - No mutation of input maps (including backing mutable maps)
 * - Addition must sum overlaps and preserve non-overlaps
 * - Scaling must apply to every stored entry
 * - dividedBy must reject invalid divisors
 * - Persistence round-trip must not lose codes or values
 *
 *
 * Suggested future regression tests after first production release
 *
 * 1) Frozen real-world day totals fixture (macros + a few micros)
 * 2) Snapshot immutability regression (log totals unchanged after food edits)
 * 3) Large nutrient map performance regression (CI guard)
 * 4) Extreme numeric inputs:
 *    - very large doubles
 *    - NaN / Infinity handling decision (either forbid or define semantics)
 */