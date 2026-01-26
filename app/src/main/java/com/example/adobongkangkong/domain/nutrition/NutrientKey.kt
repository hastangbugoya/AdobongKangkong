package com.example.adobongkangkong.domain.nutrition

/**
 * Canonical nutrient key used everywhere in domain.
 * Example: "protein_g", "carbs_g", "vitamin_c_mg".
 *
 * Keep this stable; aliases map INTO these keys.
 */
@JvmInline
value class NutrientKey(val value: String) {
    init {
        require(value.isNotBlank()) { "NutrientKey cannot be blank" }
    }

    override fun toString(): String = value
}
