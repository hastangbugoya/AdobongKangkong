package com.example.adobongkangkong.domain.nutrition

/**
 * Canonical nutrient code constants used throughout the domain layer.
 *
 * ## Purpose
 * Provides a single authoritative set of string codes that uniquely identify nutrients
 * across the entire system.
 *
 * ---
 *
 * ## Code vs Key (important distinction)
 *
 * Code (storage / persistence layer)
 * - Raw string identifier (example: `"PROTEIN_G"`)
 * - Stored in `NutrientEntity.code`
 * - Used in database rows, USDA imports, and snapshots
 * - Defined here in [NutrientCodes]
 *
 * Key (domain layer)
 * - Strongly-typed wrapper around the code (`NutrientKey`)
 * - Used in domain logic, aggregation maps, and use cases
 * - Prevents accidental misuse of arbitrary strings
 *
 *  Relationship:
 *
 * DB Layer:
 *     NutrientEntity.code  →  "PROTEIN_G"
 *
 * Domain Layer:
 *     NutrientKey("PROTEIN_G")
 *
 * This object defines the storage identity; NutrientKey wraps it for safe domain usage.
 *
 * ---
 *
 * ## Architectural layer ownership
 *
 * Database layer
 * - Stores the code string in NutrientEntity.code
 *
 * Import layer
 * - Maps USDA nutrients → these codes
 *
 * Domain layer
 * - Uses NutrientKey wrappers derived from these codes
 * - Aggregates nutrient totals using these identifiers
 *
 * UI layer
 * - Resolves display names via repository using these codes
 *
 * Snapshot / logging layer
 * - Persists codes permanently for historical accuracy
 *
 * ---
 *
 * These codes serve as the **stable identifier layer** between:
 *
 * - Database rows (`NutrientEntity.code`)
 * - Domain objects (`NutrientKey`)
 * - USDA import mapping
 * - Food nutrient storage (`FoodNutrientEntity`)
 * - Snapshot logging
 * - Planner, dashboard, and heatmap calculations
 *
 * ## Rationale
 * Nutrient identity must remain stable over time even if:
 *
 * - Display names change ("Carbohydrate" → "Carbs")
 * - Units change presentation format
 * - Aliases or search mappings evolve
 *
 * Using immutable, unit-suffixed string codes ensures:
 *
 * - No ambiguity between nutrients with similar names
 * - Explicit unit semantics (e.g., SODIUM_MG vs SODIUM_G)
 * - Safe long-term persistence in logs and snapshots
 *
 * The unit suffix is critical because nutrition math depends on unit correctness.
 *
 * Example:
 *
 * "SODIUM_MG" and "SODIUM_G" must be distinct codes, even if display name is "Sodium".
 *
 * ## Behavior
 *
 * These constants are referenced by:
 *
 * - NutrientKey wrappers
 * - NutrientRepository lookups
 * - Import mappers
 * - UI pinning and target systems
 * - Nutrition aggregation and scaling logic
 *
 * These values must match exactly the database column:
 *
 * NutrientEntity.code
 *
 * ## Edge cases
 *
 * Code mismatch:
 *
 * If a DB row uses a different code than these constants,
 * lookups and aggregations will silently fail.
 *
 * Legacy compatibility:
 *
 * Some older code may still reference legacy identifiers such as "CALORIES".
 *
 * These are now deprecated aliases.
 *
 * ## Pitfalls / gotchas
 *
 * DO NOT change these string values once released.
 *
 * Changing a code breaks:
 *
 * - existing food nutrient rows
 * - historical log snapshots
 * - planner and heatmap history
 * - USDA mapping references
 *
 * If a nutrient must be renamed, change displayName in the DB,
 * NOT the code.
 *
 * ## Architectural rules
 *
 * Nutrient code is the ONLY stable identity for a nutrient.
 *
 * Never use displayName as an identifier.
 *
 * All persistence, aggregation, and snapshot logic must use these codes.
 */
object NutrientCodes {

    /** Energy in kilocalories. Canonical energy unit. */
    const val CALORIES_KCAL = "CALORIES_KCAL"

    /** Protein mass in grams. */
    const val PROTEIN_G = "PROTEIN_G"

    /** Total carbohydrates in grams. */
    const val CARBS_G = "CARBS_G"

    /** Total fat in grams. */
    const val FAT_G = "FAT_G"

    /** Dietary fiber in grams. */
    const val FIBER_G = "FIBER_G"

    /** Total sugars in grams. */
    const val SUGARS_G = "SUGARS_G"

    /** Sodium in milligrams. */
    const val SODIUM_MG = "SODIUM_MG"

    /**
     * Legacy alias preserved for backward compatibility.
     *
     * Historically, "CALORIES" was used without unit suffix.
     *
     * This now resolves to CALORIES_KCAL to enforce explicit unit semantics.
     *
     * New code MUST use CALORIES_KCAL directly.
     */
    @Deprecated("Use CALORIES_KCAL (canonical unit-suffixed code).")
    const val CALORIES = CALORIES_KCAL
}

/**
 * =============================================================================
 * FOR FUTURE AI / FUTURE DEVELOPER — NutrientCodes invariants and migration notes
 * =============================================================================
 *
 * Invariants (DO NOT BREAK)
 *
 * - These codes must exactly match NutrientEntity.code values in the database.
 * - These codes are persisted into:
 *
 *   - food nutrient rows
 *   - nutrition snapshots
 *   - log entries
 *   - planner aggregates
 *
 * - Changing a code breaks historical data integrity.
 *
 *
 * Why unit-suffix naming exists
 *
 * Explicit units prevent silent scaling errors.
 *
 * Example risk if suffix omitted:
 *
 *   "SODIUM"
 *
 * Could represent:
 *
 *   mg, g, or µg
 *
 * Explicit form removes ambiguity:
 *
 *   SODIUM_MG
 *
 *
 * Possible future simplification
 *
 * This object could be replaced entirely by:
 *
 * - NutrientKey sealed hierarchy
 * OR
 * - Database-only identifiers loaded at runtime
 *
 * Conditions required before removal:
 *
 * - All hardcoded references replaced with DB lookups
 * - USDA import uses DB nutrient mapping table
 * - No code references NutrientCodes constants directly
 *
 *
 * Recommended long-term architecture
 *
 * Database remains the authoritative registry.
 *
 * NutrientCodes becomes optional compile-time convenience layer.
 *
 *
 * Migration safety rule
 *
 * Never delete or rename an existing nutrient code.
 *
 * If needed:
 *
 * - Mark deprecated
 * - Add new code
 * - Provide migration mapping
 *
 *
 * Performance
 *
 * Zero runtime cost (compile-time constants).
 *
 * Safe to keep indefinitely.
 */