package com.example.adobongkangkong.domain.nutrition

/**
 * Immutable container for nutrient values keyed by [NutrientKey].
 *
 * Domain rules:
 * - Values are "amount per X" depending on context (per gram, per serving, totals, etc.)
 * - Missing nutrients return 0.0 (never crash).
 * - All operations preserve immutability.
 */
@JvmInline
value class NutrientMap(
    private val amounts: Map<NutrientKey, Double>
) {
    /** Safe lookup — returns 0.0 if the nutrient is missing. */
    operator fun get(key: NutrientKey): Double = amounts[key] ?: 0.0

    /** Returns all nutrient keys present in this map (debug + UI use). */
    fun keys(): Set<NutrientKey> = amounts.keys

    /** Returns the backing map (use sparingly; mostly for debug/UI). */
    fun asMap(): Map<NutrientKey, Double> = amounts

    /** True if no nutrients exist. */
    fun isEmpty(): Boolean = amounts.isEmpty()

    /** Multiplies all nutrient values by [factor]. */
    fun scaledBy(factor: Double): NutrientMap =
        when {
            factor == 1.0 -> this
            factor == 0.0 -> EMPTY
            else -> NutrientMap(amounts.mapValues { (_, v) -> v * factor })
        }

    /** Adds two nutrient maps together. */
    operator fun plus(other: NutrientMap): NutrientMap {
        if (this.isEmpty()) return other
        if (other.isEmpty()) return this

        val out = HashMap<NutrientKey, Double>(amounts.size + other.amounts.size)

        for ((k, v) in amounts) out[k] = v + (other.amounts[k] ?: 0.0)
        for ((k, v) in other.amounts) if (k !in out) out[k] = v

        return NutrientMap(out)
    }

    companion object {
        val EMPTY = NutrientMap(emptyMap())
    }
}

/**
 * Divides all nutrient values by [divisor].
 *
 * Use this for deriving per-serving or per-cooked-gram values from totals.
 */
fun NutrientMap.dividedBy(divisor: Double): NutrientMap {
    require(divisor > 0.0) { "divisor must be > 0" }
    return scaledBy(1.0 / divisor)
}
