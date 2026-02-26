package com.example.adobongkangkong.domain.nutrition

/**
 * Canonical domain-layer identifier for a nutrient.
 *
 * ## Purpose
 * Provides a stable, strongly-typed identifier used throughout the **domain layer**
 * to represent a nutrient. This replaces fragile raw strings and ensures consistent
 * identity across logging, aggregation, planner, and nutrition math.
 *
 * Example values:
 *
 * - CALORIES_KCAL
 * - PROTEIN_G
 * - CARBS_G
 * - FAT_G
 * - VITAMIN_C_MG
 *
 * These identifiers correspond directly to database nutrient codes.
 *
 * ---
 *
 * ## Code vs Key (important distinction)
 *
 * Code (storage layer)
 *
 * - Stored in the database (`NutrientEntity.code`)
 * - Raw string value (example: `"PROTEIN_G"`)
 * - Used in persistence, imports, and migrations
 *
 * Key (domain layer)
 *
 * - Wrapped version of the code (`NutrientKey`)
 * - Used in business logic, maps, and use cases
 * - Prevents accidental misuse of arbitrary strings
 *
 * Relationship:
 *
 * NutrientEntity.code (DB)
 *        ⇅
 * NutrientKey.value (domain)
 *
 * NutrientKey is the domain-safe representation of the DB code.
 *
 * ---
 *
 * ## Architectural layer ownership
 *
 * Database layer:
 *
 * - NutrientEntity.code → stored string identifier
 *
 * Domain layer:
 *
 * - NutrientKey → canonical identity used everywhere in domain logic
 * - NutrientMap keys
 * - planner aggregation
 * - snapshot logging
 * - heatmaps and dashboards
 *
 * UI layer:
 *
 * - Reads NutrientKey
 * - Resolves displayName via NutrientRepository
 *
 * Import layer:
 *
 * - Maps USDA nutrients → NutrientKey
 *
 * ---
 *
 * ## Behavior
 *
 * This is a JVM inline value class wrapping a String.
 *
 * Benefits:
 *
 * - zero runtime overhead
 * - type safety
 * - prevents mixing nutrient identifiers with arbitrary strings
 *
 * Validation:
 *
 * Blank keys are rejected at construction time.
 *
 * ---
 *
 * ## Edge cases
 *
 * Unknown nutrients:
 *
 * NutrientKey may contain codes not listed in the companion object,
 * especially when imported from USDA.
 *
 * This is valid as long as the DB contains the matching nutrient row.
 *
 * Equality:
 *
 * Equality is string-based and case-sensitive.
 *
 * ---
 *
 * ## Pitfalls / gotchas
 *
 * DO NOT use display names as identifiers.
 *
 * Always use NutrientKey.
 *
 * Example of incorrect logic:
 *
 * if (nutrient.displayName == "Protein") ❌
 *
 * Correct:
 *
 * if (nutrient.key == NutrientKey.PROTEIN_G) ✅
 *
 * Display names can change; keys must never change.
 *
 * ---
 *
 * ## Why this exists instead of using String directly
 *
 * Prevents bugs such as:
 *
 * - typos ("protien_g")
 * - mismatched casing
 * - accidental use of display names
 * - mixing unrelated string identifiers
 *
 * Provides compile-time safety while preserving runtime efficiency.
 */
@JvmInline
value class NutrientKey(val value: String) {

    init {
        require(value.isNotBlank()) { "NutrientKey cannot be blank" }
    }

    override fun toString(): String = value

    companion object {

        /** Energy in kilocalories. */
        val CALORIES_KCAL = NutrientKey("CALORIES_KCAL")

        /** Protein mass in grams. */
        val PROTEIN_G = NutrientKey("PROTEIN_G")

        /** Total carbohydrates in grams. */
        val CARBS_G = NutrientKey("CARBS_G")

        /** Total fat in grams. */
        val FAT_G = NutrientKey("FAT_G")

        /** Dietary fiber in grams. */
        val FIBER_G = NutrientKey("FIBER_G")

        /** Total sugar in grams. */
        val SUGAR_G = NutrientKey("SUGAR_G")

        /** Sodium in milligrams. */
        val SODIUM_MG = NutrientKey("SODIUM_MG")
    }
}

/**
 * =============================================================================
 * FOR FUTURE AI / FUTURE DEVELOPER — NutrientKey invariants and evolution rules
 * =============================================================================
 *
 * Invariants (MUST NOT CHANGE)
 *
 * - NutrientKey.value MUST exactly match NutrientEntity.code in DB.
 *
 * - Historical log snapshots store nutrient codes permanently.
 *
 * - Changing a key string breaks:
 *
 *   - historical logs
 *   - recipe snapshots
 *   - planner totals
 *   - pinned nutrients
 *   - heatmaps
 *
 *
 * Allowed future changes
 *
 * Safe:
 *
 * - Adding new keys
 * - Deprecating keys (but keeping value intact)
 *
 * Unsafe:
 *
 * - Renaming existing keys
 * - Changing unit suffixes
 *
 *
 * Architectural boundary
 *
 * Domain logic must always use NutrientKey, not raw strings.
 *
 * Repository layer converts DB rows → NutrientKey.
 *
 *
 * Possible future improvements
 *
 * Could evolve into:
 *
 * sealed interface NutrientKey
 *
 * OR
 *
 * DB-driven keys only
 *
 * But must preserve string compatibility.
 *
 *
 * Performance considerations
 *
 * Inline value class → zero allocation in most cases.
 *
 * Safe for use in:
 *
 * - maps
 * - flows
 * - aggregation loops
 *
 *
 * Migration notes
 *
 * If nutrient codes ever need renaming:
 *
 * MUST:
 *
 * - create new code
 * - migrate DB rows
 * - migrate snapshot logs (or provide compatibility mapping)
 *
 * NEVER silently rename.
 */