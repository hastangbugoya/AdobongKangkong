package com.example.adobongkangkong.domain.nutrition

/**
 * Immutable container for nutrient values keyed by [NutrientKey].
 *
 * Domain rules:
 * - Missing nutrients return 0.0 (never crash).
 * - Operations preserve immutability.
 */
@JvmInline
value class NutrientMap(
    private val amounts: Map<NutrientKey, Double>,
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
        val EMPTY = NutrientMap(emptyMap())

        /** Builds a [NutrientMap] from a String-keyed map (codes). */
        fun fromCodeMap(map: Map<String, Double>): NutrientMap =
            NutrientMap(map.mapKeys { (k, _) -> NutrientKey(k) })
    }
}

/** Divides all nutrient values by [divisor]. */
fun NutrientMap.dividedBy(divisor: Double): NutrientMap {
    require(divisor > 0.0) { "divisor must be > 0" }
    return scaledBy(1.0 / divisor)
}
