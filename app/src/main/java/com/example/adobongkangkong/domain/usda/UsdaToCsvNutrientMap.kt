package com.example.adobongkangkong.domain.usda

import com.example.adobongkangkong.domain.nutrition.NutrientCodes

/**
 * Maps USDA nutrientNumber → internal CSV nutrient code.
 *
 * Overview
 * --------
 * The USDA Foods API identifies nutrients using a numeric string called
 * `nutrientNumber` (e.g., "208" for calories, "203" for protein).
 *
 * The AdobongKangkong app uses its own canonical nutrient identity system
 * defined in:
 *
 *     NutrientCodes (domain layer)
 *     NutrientEntity.code (database layer)
 *
 * This object bridges those two identity systems.
 *
 * Why this mapping exists
 * -----------------------
 * USDA nutrient identifiers are:
 *
 * - numeric strings ("203", "204", etc.)
 * - USDA-specific
 * - not stable across all nutrition data sources
 *
 * The app uses a source-agnostic canonical identity model so that:
 *
 * - USDA imports
 * - manual foods
 * - CSV imports
 * - recipe snapshots
 *
 * all reference the SAME nutrient identity.
 *
 * Without this mapping:
 *
 * USDA nutrientNumber ("203")
 * would not match
 *
 * internal nutrient code ("PROTEIN_G")
 *
 * which would break:
 *
 * - logging totals
 * - recipe computation
 * - planner aggregation
 * - UI nutrient display
 *
 *
 * Data flow (USDA import pipeline)
 * --------------------------------
 *
 * USDA JSON
 *     ↓
 * UsdaFoodsSearchParser
 *     ↓
 * UsdaFoodSearchItem.foodNutrients[nutrientNumber]
 *     ↓
 * UsdaToCsvNutrientMap.byUsdaNumber[nutrientNumber]
 *     ↓
 * NutrientRepository.getByCode(csvCode)
 *     ↓
 * NutrientEntity.id
 *     ↓
 * FoodNutrientEntity persisted
 *
 *
 * Example conversion
 *
 * USDA JSON:
 *
 *     nutrientNumber = "203"
 *     value = 12.0
 *
 * Mapping:
 *
 *     "203" → NutrientCodes.PROTEIN_G
 *
 * Result:
 *
 *     nutrientId = lookup("PROTEIN_G")
 *     amount stored in canonical basis
 *
 *
 * Sources of truth (both sides)
 * ------------------------------
 *
 * USDA side:
 * - USDA FoodData Central
 * - nutrientNumber defined here:
 *   https://fdc.nal.usda.gov/
 *
 * Internal side:
 * - CsvNutrientCatalog (initial seeding)
 * - NutrientCodes constants
 * - nutrients database table
 *
 *
 * Scope policy (intentional limitation)
 * -------------------------------------
 * We ONLY map nutrients that:
 *
 * - exist in CsvNutrientCatalog
 * - are supported by the app
 *
 * Unmapped USDA nutrients are intentionally ignored.
 *
 * This prevents:
 *
 * - orphan nutrient rows
 * - UI inconsistencies
 * - planner math errors
 *
 *
 * Stability requirements
 * ----------------------
 *
 * Keys (USDA nutrientNumber):
 * MUST remain exact USDA string identifiers.
 *
 * Values (internal nutrient code):
 * MUST match NutrientEntity.code exactly.
 *
 *
 * Do NOT:
 *
 * - invent new codes here
 * - change existing mappings casually
 * - remove mappings unless nutrient is deprecated everywhere
 *
 *
 * Safe extension procedure
 * ------------------------
 *
 * To add a new nutrient:
 *
 * 1) Add nutrient to CsvNutrientCatalog
 *
 * 2) Ensure NutrientCodes constant exists
 *
 * 3) Add USDA mapping here
 *
 * 4) Verify import writes correct nutrientId
 *
 *
 * Related use cases
 * -----------------
 *
 * ImportUsdaFoodFromSearchJsonUseCase
 * BuildDraftFromParsedItem
 * MergeUsdaNutrientsIntoFoodUseCase
 * ComputeRecipeNutritionForSnapshotUseCase
 *
 */
object UsdaToCsvNutrientMap {

    val byUsdaNumber: Map<String, String> = mapOf(

        // ------------------------------------------------------------
        // Energy and macronutrients
        // ------------------------------------------------------------

        "208" to NutrientCodes.CALORIES_KCAL, // Energy
        "205" to NutrientCodes.CARBS_G,       // Carbohydrate
        "203" to NutrientCodes.PROTEIN_G,     // Protein
        "204" to NutrientCodes.FAT_G,         // Total fat


        // ------------------------------------------------------------
        // Carbohydrate breakdown
        // ------------------------------------------------------------

        "269" to "SUGARS_G",                  // Total sugars
        "212" to "FRUCTOSE_G",
        "210" to "SUCROSE_G",
        "291" to "FIBER_G",                   // Dietary fiber


        // ------------------------------------------------------------
        // Lipids / sterols
        // ------------------------------------------------------------

        "601" to "CHOLESTEROL_MG",
        "606" to "SATURATED_FAT_G",
        "605" to "TRANS_FAT_G",
        "645" to "MONO_FAT_G",
        "646" to "POLY_FAT_G",


        // ------------------------------------------------------------
        // Electrolytes
        // ------------------------------------------------------------

        "307" to "SODIUM_MG",
        "306" to "POTASSIUM_MG",


        // ------------------------------------------------------------
        // Minerals
        // ------------------------------------------------------------

        "301" to "CALCIUM_MG",
        "303" to "IRON_MG",
        "304" to "MAGNESIUM_MG",
        "315" to "MANGANESE_MG",
        "309" to "ZINC_MG",
        "312" to "COPPER_MG",
        "305" to "PHOSPHORUS_MG",
        "317" to "SELENIUM_MCG",


        // ------------------------------------------------------------
        // Vitamins
        // ------------------------------------------------------------

        "328" to "VITAMIN_D_MCG",
        "320" to "VITAMIN_A_MCG",
        "401" to "VITAMIN_C_MG",
        "430" to "VITAMIN_K_MCG",
        "415" to "VITAMIN_B6_MG",
        "418" to "VITAMIN_B12_MCG",
        "431" to "FOLIC_ACID_MCG",
        "435" to "FOLATE_DFE_MCG",

        "323" to "VITAMIN_E_MG",
        "319" to "RETINOL_MCG",
        "405" to "RIBOFLAVIN_MG",
        "406" to "NIACIN_MG",
        "421" to "CHOLINE_MG",
        "321" to "BETA_CAROTENE_MCG",


        // ------------------------------------------------------------
        // Added sugars (FDA label requirement)
        // ------------------------------------------------------------

        "539" to "ADDED_SUGARS_G",

        // ------------------------------------------------------------
        // Other
        // ------------------------------------------------------------
        "262" to "CAFFEINE_MG",
        "263" to "THEOBROMINE_MG",
        "221" to "ETHYL_ALCOHOL_G",
        "255" to "WATER_G",
        "338" to "LUTEIN_ZEAXANTHIN_MCG",
    )
}

/**
 * ============================
 * FUTURE-AI / MAINTENANCE NOTES
 * ============================
 *
 * This mapping is a CRITICAL identity bridge.
 *
 * Breaking it will silently corrupt:
 *
 * - USDA imports
 * - recipe math
 * - planner totals
 * - nutrition logging
 *
 *
 * Invariants
 * ----------
 *
 * USDA nutrientNumber must map to EXACTLY ONE internal code.
 *
 * Internal code must exist in:
 *
 * NutrientRepository
 * nutrients table
 *
 *
 * Never allow:
 *
 * nutrientNumber → multiple codes
 *
 *
 * Common bugs to watch for
 * ------------------------
 *
 * Symptom:
 * "Missing nutrients after USDA import"
 *
 * Cause:
 * mapping missing here OR nutrient not seeded in DB
 *
 *
 * Symptom:
 * duplicate nutrients
 *
 * Cause:
 * multiple basis rows, not mapping issue
 *
 *
 * Performance
 * -----------
 *
 * O(1) lookup via immutable map.
 *
 * Safe to use in tight loops.
 *
 *
 * Future improvements (optional)
 * ------------------------------
 *
 * Could generate mapping automatically from CSV catalog
 * if USDA nutrient numbers included in seed data.
 *
 * Current manual mapping is safer and explicit.
 */