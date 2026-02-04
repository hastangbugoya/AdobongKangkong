package com.example.adobongkangkong.domain.usda

import com.example.adobongkangkong.domain.nutrition.NutrientCodes

/**
 * Maps USDA nutrientNumber -> our CSV nutrient code.
 *
 * We ONLY map nutrients we track in CsvNutrientCatalog.
 */
object UsdaToCsvNutrientMap {

    val byUsdaNumber: Map<String, String> = mapOf(
        // Energy & macros
        "208" to NutrientCodes.CALORIES_KCAL, // Energy (kcal)
        "205" to NutrientCodes.CARBS_G,       // Carbohydrate
        "203" to NutrientCodes.PROTEIN_G,     // Protein
        "204" to NutrientCodes.FAT_G,         // Total lipid (fat)

        // Carb breakdown
        "269" to "SUGARS_G",                  // Total Sugars
        "291" to "FIBER_G",                   // Fiber, total dietary

        // Sterols
        "601" to "CHOLESTEROL_MG",            // Cholesterol

        // Electrolytes
        "307" to "SODIUM_MG",                 // Sodium
        "306" to "POTASSIUM_MG",              // Potassium

        // Minerals
        "301" to "CALCIUM_MG",                // Calcium
        "303" to "IRON_MG",                   // Iron
        "304" to "MAGNESIUM_MG",              // Magnesium
        "315" to "MANGANESE_MG",              // Manganese
        "309" to "ZINC_MG",                   // Zinc
        "312" to "COPPER_MG",                 // Copper
        "305" to "PHOSPHORUS_MG",             // Phosphorus
        "317" to "SELENIUM_MCG",              // Selenium

        // Vitamins / vitamin-like
        "328" to "VITAMIN_D_MCG",             // Vitamin D (D2 + D3)
        "320" to "VITAMIN_A_MCG",             // Vitamin A, RAE
        "401" to "VITAMIN_C_MG",              // Vitamin C, total ascorbic acid
        "415" to "VITAMIN_B6_MG",             // Vitamin B-6
        "418" to "VITAMIN_B12_MCG",           // Vitamin B-12
        "323" to "VITAMIN_E_MG",              // Vitamin E (alpha-tocopherol)
        "319" to "RETINOL_MCG",               // Retinol
        "405" to "RIBOFLAVIN_MG",             // Riboflavin (B2)
        "406" to "NIACIN_MG",                 // Niacin
        "421" to "CHOLINE_MG",                // Choline, total

        // Sugars
        "539" to "ADDED_SUGARS_G",   // Sugars, added

        // Fats
        "606" to "SATURATED_FAT_G", // Fatty acids, total saturated
        "605" to "TRANS_FAT_G",     // Fatty acids, total trans
    )
}
