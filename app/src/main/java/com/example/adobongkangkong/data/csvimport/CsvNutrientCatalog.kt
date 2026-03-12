package com.example.adobongkangkong.data.csvimport

import com.example.adobongkangkong.domain.model.NutrientCategory
import com.example.adobongkangkong.domain.nutrition.NutrientCodes

/**
 * Defines how CSV columns map to Nutrient rows in our DB.
 *
 * Strategy:
 * - Use stable NutrientCodes for core macros (so dashboards/recipes work).
 * - For the rest, use clear codes you can keep forever (e.g., VITAMIN_C_MG).
 * - Store category as NutrientCategory.dbValue to avoid enum converters.
 */
object CsvNutrientCatalog {

    data class Def(
        val csvHeader: String,
        val code: String,
        val displayName: String,
        val unit: String,
        val categoryDbValue: String,
    )

    // NOTE: units here are "best effort". You can edit them later in-app if you want.
    // If you want to be strict, we can add a "unit unknown" and fix later.
    val defs: List<Def> = listOf(
        // Energy & macros
        Def("cal", NutrientCodes.CALORIES_KCAL, "Calories", "kcal", NutrientCategory.ENERGY.dbValue),
        Def("carbs", NutrientCodes.CARBS_G, "Carbohydrates", "g", NutrientCategory.MACRO.dbValue),
        Def("prot", NutrientCodes.PROTEIN_G, "Protein", "g", NutrientCategory.MACRO.dbValue),
        Def("fat(g)", NutrientCodes.FAT_G, "Fat", "g", NutrientCategory.FAT.dbValue),
        // Fats
        Def("sat_fat", "SATURATED_FAT_G", "Saturated Fat", "g", NutrientCategory.FATTY_ACID.dbValue),
        Def("trans_fat", "TRANS_FAT_G", "Trans Fat", "g", NutrientCategory.FATTY_ACID.dbValue),
        Def("mono_fat", "MONO_FAT_G", "Monounsaturated Fat", "g", NutrientCategory.FATTY_ACID.dbValue),
        Def("poly_fat", "POLY_FAT_G", "Polyunsaturated Fat", "g", NutrientCategory.FATTY_ACID.dbValue),
        // Carb breakdown
        Def("sug", "SUGARS_G", "Sugars", "g", NutrientCategory.SUGAR.dbValue),
        Def("added_sug", "ADDED_SUGARS_G", "Added Sugars", "g", NutrientCategory.SUGAR.dbValue),
        Def("Fructose", "FRUCTOSE_G", "Fructose", "g", NutrientCategory.SUGAR.dbValue),
        Def("Sucrose", "SUCROSE_G", "Sucrose", "g", NutrientCategory.SUGAR.dbValue),

        Def("fib", "FIBER_G", "Fiber", "g", NutrientCategory.FIBER.dbValue),

        // Sterols / electrolytes / minerals etc
        Def("Chol", "CHOLESTEROL_MG", "Cholesterol", "mg", NutrientCategory.STEROL.dbValue),
        Def("Na", "SODIUM_MG", "Sodium", "mg", NutrientCategory.ELECTROLYTE.dbValue),
        Def("K", "POTASSIUM_MG", "Potassium", "mg", NutrientCategory.ELECTROLYTE.dbValue),

        Def("Ca", "CALCIUM_MG", "Calcium", "mg", NutrientCategory.MINERAL.dbValue),
        Def("Fe", "IRON_MG", "Iron", "mg", NutrientCategory.MINERAL.dbValue),
        Def("Mg", "MAGNESIUM_MG", "Magnesium", "mg", NutrientCategory.MINERAL.dbValue),
        Def("Mn", "MANGANESE_MG", "Manganese", "mg", NutrientCategory.MINERAL.dbValue),
        Def("Zn", "ZINC_MG", "Zinc", "mg", NutrientCategory.MINERAL.dbValue),

        // Copper appears twice in your CSV. We'll handle that during parsing.
        Def("Cu", "COPPER_MG", "Copper", "mg", NutrientCategory.MINERAL.dbValue),

        // Vitamins
        Def("Vit D", "VITAMIN_D_MCG", "Vitamin D", "mcg", NutrientCategory.VITAMIN.dbValue),
        Def("Vit A", "VITAMIN_A_MCG", "Vitamin A", "mcg", NutrientCategory.VITAMIN.dbValue),
        Def("Vit C", "VITAMIN_C_MG", "Vitamin C", "mg", NutrientCategory.VITAMIN.dbValue),
        Def("Vit K", "VITAMIN_C_MCG", "Vitamin K", "mcg", NutrientCategory.VITAMIN.dbValue),
        Def("B6", "VITAMIN_B6_MG", "Vitamin B6", "mg", NutrientCategory.VITAMIN.dbValue),
        Def("B12", "VITAMIN_B12_MCG", "Vitamin B12", "mcg", NutrientCategory.VITAMIN.dbValue),
        Def("Folic acid", "FOLIC_ACID_MCG", "Folic Acid", "mcg", NutrientCategory.VITAMIN.dbValue),
        Def("Folate", "FOLATE_DFE_MCG", "Folate", "mcg", NutrientCategory.VITAMIN.dbValue),

        Def("E", "VITAMIN_E_MG", "Vitamin E", "mg", NutrientCategory.VITAMIN.dbValue),
        Def("Beta-carotene", "BETA_CAROTENE_MCG", "Beta-Carotene", "mcg", NutrientCategory.VITAMIN.dbValue),

        // Other (common labels you had)
        Def("P", "PHOSPHORUS_MG", "Phosphorus", "mg", NutrientCategory.MINERAL.dbValue),
        Def("Se", "SELENIUM_MCG", "Selenium", "mcg", NutrientCategory.MINERAL.dbValue),

        Def("Retinol", "RETINOL_MCG", "Retinol", "mcg", NutrientCategory.VITAMIN.dbValue),
        Def("Niacin", "NIACIN_MG", "Niacin (B3)", "mg", NutrientCategory.VITAMIN.dbValue),

        Def("Choline", "CHOLINE_MG", "Choline", "mg", NutrientCategory.OTHER.dbValue),
        Def("Riboflavin", "RIBOFLAVIN_MG", "Riboflavin (B2)", "mg", NutrientCategory.VITAMIN.dbValue),
        Def("Caffein", "CAFFEINE_MG", "Caffeine", "mg", NutrientCategory.OTHER.dbValue),
        Def("Theobromine", "THEOBROMINE_MG", "Theobromine", "mg", NutrientCategory.OTHER.dbValue),
        Def("Ethyl", "ETHYL_ALCOHOL_G", "Ethyl Alcohol", "g", NutrientCategory.OTHER.dbValue),
        Def("Water", "WATER_G", "Water", "g", NutrientCategory.OTHER.dbValue),
        Def("Lutein and zeaxanthin", "LUTEIN_ZEAXANTHIN_MCG", "Lutein+Zeaxanthin", "mcg", NutrientCategory.OTHER.dbValue),
    )
}
