package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.domain.model.NutrientCategory
import com.example.adobongkangkong.domain.model.NutrientUnit

/**
 * ⚠️ LEGACY / MAY BE REMOVED
 *
 * Canonical in-code catalog of nutrients known to the app.
 *
 * ## Purpose
 * Provides a hardcoded reference list of nutrients with stable identifiers,
 * display defaults, and search aliases. This was originally intended to act as:
 *
 * - A bootstrap source when seeding the database
 * - A fallback lookup for nutrient metadata (display name, unit, category)
 * - A stable mapping layer between domain concepts and database nutrient IDs
 *
 * ## Rationale
 * The nutrition system requires stable nutrient identity across:
 *
 * - USDA imports
 * - user-created foods
 * - recipe nutrition computation
 * - logging snapshots
 * - planner and dashboard features
 *
 * Using a canonical in-code catalog ensures:
 *
 * - Stable nutrient codes even if DB rows are edited
 * - Consistent aliases for search
 * - Predictable units and categories for rendering and grouping
 *
 * This catalog served as the original “source of truth” before the database
 * became the authoritative nutrient registry.
 *
 * ## Current status (important)
 * This object appears to be partially superseded by:
 *
 * - NutrientKey / NutrientCodes
 * - Database-backed NutrientEntity rows
 * - Nutrient alias tables and search infrastructure
 *
 * Notably:
 * - This file still uses legacy codes such as "CALORIES"
 * - The modern canonical code is "CALORIES_KCAL"
 *
 * Because of this mismatch, this catalog should NOT be assumed authoritative.
 *
 * ## Behavior
 * Provides:
 *
 * - entries → list of known nutrient definitions
 * - idOf(key) → maps NutrientKey → DB nutrientId (if known)
 * - idOfValue(code) → maps raw code string → DB nutrientId
 *
 * Mapping is static and must match database seed IDs.
 *
 * ## Parameters
 * None (static object).
 *
 * ## Return values
 * - entries → canonical metadata list
 * - idOf(...) → nullable nutrientId if mapping exists
 *
 * ## Edge cases
 *
 * Missing mapping:
 * - idOf() returns null if the key is not mapped
 *
 * Code mismatch:
 * - Legacy codes may not match modern NutrientCodes
 *
 * Partial catalog:
 * - This list is not exhaustive; many nutrients may exist only in DB
 *
 * ## Pitfalls / gotchas
 *
 * This catalog can easily become out of sync with the database.
 *
 * If DB IDs change or new nutrients are added, this file will silently break mappings.
 *
 * Do NOT assume this contains the complete nutrient list.
 *
 * Prefer DB-backed NutrientRepository for runtime logic.
 *
 * ## Architectural rules
 *
 * Database nutrient rows are the authoritative runtime source.
 *
 * This catalog should be used only for:
 *
 * - Initial seeding
 * - Compile-time constants
 * - Migration assistance
 * - Fallback metadata when DB unavailable
 *
 * Not for ongoing runtime truth.
 */
object NutrientCatalog {

    data class Entry(
        val code: String,
        val displayName: String,
        val unit: NutrientUnit,
        val category: NutrientCategory,
        val aliases: List<String> = emptyList()
    )

    /**
     * Expand this list over time.
     * Keep codes stable once shipped.
     */
    val entries: List<Entry> = listOf(
        Entry(
            code = "CALORIES",
            displayName = "Calories",
            unit = NutrientUnit.KCAL,
            category = NutrientCategory.MACRO,
            aliases = listOf("kcal", "calories", "energy")
        ),
        Entry(
            code = "PROTEIN",
            displayName = "Protein",
            unit = NutrientUnit.G,
            category = NutrientCategory.MACRO,
            aliases = listOf("protein", "prot")
        ),
        Entry(
            code = "CARBS",
            displayName = "Carbs",
            unit = NutrientUnit.G,
            category = NutrientCategory.MACRO,
            aliases = listOf("carb", "carbs", "carbohydrate", "carbohydrates")
        ),
        Entry(
            code = "FAT",
            displayName = "Fat",
            unit = NutrientUnit.G,
            category = NutrientCategory.MACRO,
            aliases = listOf("fat", "total fat", "lipid")
        ),

        Entry(
            code = "VITAMIN_C",
            displayName = "Vitamin C",
            unit = NutrientUnit.MG,
            category = NutrientCategory.VITAMIN,
            aliases = listOf("vit c", "ascorbic acid")
        ),
    )

    private val keyToId: Map<String, Long> = mapOf(
        NutrientKey.CALORIES_KCAL.value to 1001L,
        NutrientKey.PROTEIN_G.value to 1002L,
        NutrientKey.CARBS_G.value to 1003L,
        NutrientKey.FAT_G.value to 1004L,
        NutrientKey.FIBER_G.value to 1005L,
        NutrientKey.SUGAR_G.value to 1006L,
        NutrientKey.SODIUM_MG.value to 1007L
    )

    fun idOf(key: NutrientKey): Long? = keyToId[key.value]

    fun idOfValue(keyValue: String): Long? = keyToId[keyValue]
}

/**
 * =============================================================================
 * FOR FUTURE AI / FUTURE DEVELOPER — NutrientCatalog status and migration notes
 * =============================================================================
 *
 * Current architectural direction:
 *
 * The database (NutrientEntity) is now the authoritative nutrient registry.
 *
 * Modern architecture relies on:
 *
 * - NutrientRepository
 * - NutrientKey / NutrientCodes
 * - Nutrient alias tables
 * - USDA import mappings
 *
 * This file exists primarily as a legacy bootstrap / compatibility layer.
 *
 * Known inconsistencies:
 *
 * - Uses legacy codes (CALORIES) instead of modern CALORIES_KCAL
 * - Hardcoded ID mappings assume DB seed values remain stable
 * - entries list is incomplete relative to full USDA nutrient set
 *
 * Safe future actions:
 *
 * This file may be safely deleted IF ALL of the following are true:
 *
 * - DB seeding is fully handled by migrations or import pipeline
 * - All nutrient lookups use repository-based queries
 * - No code references NutrientCatalog.entries
 * - No code relies on NutrientCatalog.idOf(...)
 *
 * Recommended replacement pattern:
 *
 * Instead of static catalogs, use:
 *
 * NutrientRepository.getAll()
 * NutrientRepository.search(...)
 * NutrientRepository.getByCode(...)
 *
 * Performance considerations:
 *
 * This object has negligible runtime cost.
 *
 * Risk considerations:
 *
 * Hardcoded ID mappings are fragile.
 *
 * If DB IDs ever change, silent corruption or lookup failures may occur.
 *
 * Verify all references before removal.
 */