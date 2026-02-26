package com.example.adobongkangkong.domain.nutrition

/**
 * Immutable container representing a set of nutrient amounts keyed by [NutrientKey].
 *
 * ## Purpose
 * Provides the canonical data structure used throughout the domain layer to store,
 * aggregate, and scale nutrition values.
 *
 * This is the **core representation of nutrition math** in the system and is used by:
 *
 * - Food nutrition snapshots
 * - Log entries (immutable historical records)
 * - Recipe batch computation
 * - Planner totals and projections
 * - Dashboard and heatmap aggregation
 *
 * NutrientMap replaces raw `Map<String, Double>` to enforce domain safety,
 * immutability, and predictable behavior.
 *
 * ---
 *
 * ## Rationale
 *
 * Nutrition operations require frequent:
 *
 * - addition (combining foods, recipes, days)
 * - scaling (per-serving, per-gram, batch size)
 * - lookup (retrieve a specific nutrient)
 *
 * Using a dedicated value class ensures:
 *
 * - safe default behavior (missing nutrients = 0.0)
 * - no accidental mutation
 * - consistent aggregation math
 * - explicit domain intent
 *
 * This prevents common bugs such as:
 *
 * - NullPointerExceptions when nutrients are missing
 * - accidental mutation of shared maps
 * - inconsistent scaling behavior
 *
 * ---
 *
 * ## Core behavior guarantees
 *
 * Safe lookup:
 *
 * Missing nutrients always return 0.0:
 *
 * map[NutrientKey.PROTEIN_G] → 0.0 if absent
 *
 * This makes aggregation safe without defensive null checks.
 *
 * Immutability:
 *
 * All operations return a new NutrientMap.
 *
 * Original maps are never modified.
 *
 * Efficient aggregation:
 *
 * Addition and scaling operations are optimized for frequent use in
 * recipe computation and planner totals.
 *
 * ---
 *
 * ## Supported operations
 *
 * Lookup
 *
 * operator get(key)
 *
 * Returns nutrient amount or 0.0 if missing.
 *
 * Addition
 *
 * mapA + mapB
 *
 * Combines nutrient totals.
 *
 * Scaling
 *
 * scaledBy(factor)
 * dividedBy(divisor)
 *
 * Used for:
 *
 * - per-serving conversions
 * - per-gram normalization
 * - recipe yield scaling
 *
 * Conversion
 *
 * toCodeMap()
 * fromCodeMap()
 *
 * Bridges domain layer ↔ persistence layer.
 *
 * ---
 *
 * ## Architectural layer ownership
 *
 * Domain layer
 *
 * Primary representation of nutrient values.
 *
 * Repository layer
 *
 * Converts database rows → NutrientMap.
 *
 * Logging layer
 *
 * Stores NutrientMap snapshots immutably.
 *
 * UI layer
 *
 * Reads values for display and summaries.
 *
 * Database layer
 *
 * Stores flattened maps using code-keyed representation.
 *
 * ---
 *
 * ## Edge cases handled safely
 *
 * Missing nutrients
 *
 * Returns 0.0, not null or crash.
 *
 * Empty maps
 *
 * Supported and optimized.
 *
 * Scaling by 1.0
 *
 * Returns same instance (optimization).
 *
 * ---
 *
 * ## Pitfalls / gotchas
 *
 * NutrientMap is immutable.
 *
 * Do NOT attempt to modify the internal map.
 *
 * Always use:
 *
 * map + otherMap
 *
 * or
 *
 * map.scaledBy(...)
 *
 *
 * Never assume a nutrient exists.
 *
 * Always access via:
 *
 * map[key]
 *
 * not:
 *
 * map.amounts[key]
 *
 *
 * Never use display names as keys.
 *
 * Always use NutrientKey.
 *
 * ---
 *
 * ## Performance characteristics
 *
 * Designed for frequent aggregation in planner and recipe math.
 *
 * Typical size is small (10–150 nutrients).
 *
 * Addition and scaling are O(N).
 */
@JvmInline
value class NutrientMap(
    val amounts: Map<NutrientKey, Double>,
) {

    fun asMap(): Map<String, Double> = toCodeMap()

    /** Safe lookup — returns 0.0 if the nutrient is missing. */
    operator fun get(key: NutrientKey): Double = amounts[key] ?: 0.0

    /** Returns all nutrient keys present in this map (useful for debug/UI). */
    fun keys(): Set<NutrientKey> = amounts.keys

    /** Returns all key/value entries (useful for mapping/persistence). */
    fun entries(): Set<Map.Entry<NutrientKey, Double>> = amounts.entries

    fun isEmpty(): Boolean = amounts.isEmpty()

    /** Multiplies all nutrient values by [factor]. */
    fun scaledBy(factor: Double): NutrientMap =
        if (factor == 1.0) this else NutrientMap(amounts.mapValues { (_, v) -> v * factor })

    /** Adds two nutrient maps together. */
    operator fun plus(other: NutrientMap): NutrientMap {
        if (this.isEmpty()) return other
        if (other.isEmpty()) return this

        val out = HashMap<NutrientKey, Double>(amounts.size + other.amounts.size)

        for ((k, v) in amounts) out[k] = v + (other.amounts[k] ?: 0.0)
        for ((k, v) in other.amounts) if (k !in out) out[k] = v

        return NutrientMap(out)
    }

    /** Converts to a plain String-keyed map for persistence or export. */
    fun toCodeMap(): Map<String, Double> =
        amounts.entries.associate { (k, v) -> k.value to v }

    companion object {

        /** Canonical empty nutrient map. */
        val EMPTY = NutrientMap(emptyMap())

        /** Builds a [NutrientMap] from a String-keyed map (codes). */
        fun fromCodeMap(map: Map<String, Double>): NutrientMap =
            NutrientMap(map.mapKeys { (k, _) -> NutrientKey(k) })
    }
}

/**
 * Divides all nutrient values by [divisor].
 *
 * Convenience helper used for:
 *
 * - per-serving calculation
 * - per-gram normalization
 * - yield scaling
 *
 * Throws if divisor is invalid to prevent silent corruption.
 */
fun NutrientMap.dividedBy(divisor: Double): NutrientMap {
    require(divisor > 0.0) { "divisor must be > 0" }
    return scaledBy(1.0 / divisor)
}

/**
 * =============================================================================
 * FOR FUTURE AI / FUTURE DEVELOPER — NutrientMap invariants and evolution notes
 * =============================================================================
 *
 * Core invariants (MUST NOT BREAK)
 *
 * - Missing nutrients MUST return 0.0
 *
 *   This guarantees safe aggregation across foods, recipes, and planner totals.
 *
 *
 * - NutrientMap MUST remain immutable.
 *
 *   Mutable maps would corrupt historical logs and recipe snapshots.
 *
 *
 * - Keys MUST be NutrientKey, never raw strings.
 *
 *
 * Why immutability is critical
 *
 * Log entries store NutrientMap snapshots permanently.
 *
 * If mutation were allowed, historical logs would silently change.
 *
 *
 * Architectural role
 *
 * This is the foundational container used by:
 *
 * - Recipe computation
 * - Planner totals
 * - Dashboard aggregation
 * - Logging snapshots
 *
 *
 * Possible future improvements
 *
 * Performance optimization:
 *
 * Could internally use specialized primitive maps if needed.
 *
 *
 * Memory optimization:
 *
 * Could share internal maps when scaling factor == 1.
 *
 *
 * Serialization optimization:
 *
 * Could add direct serialization helpers.
 *
 *
 * DO NOT CHANGE behavior without updating:
 *
 * - ComputeRecipeBatchNutritionUseCase
 * - Snapshot creation pipeline
 * - Planner aggregation logic
 *
 *
 * Migration safety
 *
 * Changing lookup semantics (returning null instead of 0)
 * would break large portions of domain logic.
 *
 *
 * Treat this as a core primitive type.
 */