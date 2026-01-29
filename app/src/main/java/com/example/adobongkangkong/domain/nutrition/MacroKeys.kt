package com.example.adobongkangkong.domain.nutrition

object MacroKeys {
    val CALORIES = NutrientKey(NutrientCodes.CALORIES_KCAL)
    val PROTEIN  = NutrientKey(NutrientCodes.PROTEIN_G)
    val CARBS    = NutrientKey(NutrientCodes.CARBS_G)
    val FAT      = NutrientKey(NutrientCodes.FAT_G)

    // Optional extras
    val SUGAR    = NutrientKey(NutrientCodes.SUGAR_G)
    val FIBER    = NutrientKey(NutrientCodes.FIBER_G)
    val SODIUM   = NutrientKey(NutrientCodes.SODIUM_MG)
}