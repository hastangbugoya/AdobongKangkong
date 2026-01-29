package com.example.adobongkangkong.domain.nutrition


/**
 * Keep these aligned with NutrientEntity.code values in your seed/import.
 */
object NutrientCodes {
    const val CALORIES_KCAL = "CALORIES_KCAL"
    const val PROTEIN_G = "PROTEIN_G"
    const val CARBS_G = "CARBS_G"
    const val FAT_G = "FAT_G"
    const val FIBER_G = "FIBER_G"
    const val SUGAR_G = "SUGAR_G"
    const val SODIUM_MG = "SODIUM_MG"


    @Deprecated("Use CALORIES_KCAL (canonical unit-suffixed code).")
    const val CALORIES = CALORIES_KCAL
}
