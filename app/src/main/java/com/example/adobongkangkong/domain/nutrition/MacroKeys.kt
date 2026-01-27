package com.example.adobongkangkong.domain.nutrition

object MacroKeys {
    val CALORIES = NutrientKey(NutrientCodes.CALORIES)
    val PROTEIN  = NutrientKey(NutrientCodes.PROTEIN_G)
    val CARBS    = NutrientKey(NutrientCodes.CARBS_G)
    val FAT      = NutrientKey(NutrientCodes.FAT_G)

    // Optional extras
    val SUGAR    = NutrientKey("SUGARS_G")
    val FIBER    = NutrientKey("FIBER_G")
    val SODIUM   = NutrientKey("SODIUM_MG")
}