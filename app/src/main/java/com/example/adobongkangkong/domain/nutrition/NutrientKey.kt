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

    companion object {
        val CALORIES_KCAL = NutrientKey("CALORIES_KCAL")
        val PROTEIN_G = NutrientKey("PROTEIN_G")
        val CARBS_G = NutrientKey("CARBS_G")
        val FAT_G = NutrientKey("FAT_G")

        val FIBER_G = NutrientKey("FIBER_G")
        val SUGAR_G = NutrientKey("SUGAR_G")
        val SODIUM_MG = NutrientKey("SODIUM_MG")
    }
}
