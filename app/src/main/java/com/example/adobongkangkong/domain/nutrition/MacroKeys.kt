package com.example.adobongkangkong.domain.nutrition

/**
 * Canonical nutrient keys representing the primary macronutrients and commonly tracked nutrition metrics.
 *
 * ## Purpose
 * Provide a centralized, strongly-typed set of [NutrientKey] constants for the most important
 * nutrients used throughout the app (macros, calories, and commonly tracked extras).
 *
 * ## Rationale (why this exists)
 * While the system supports arbitrary nutrients via dynamic codes ([NutrientCodes]),
 * certain nutrients are referenced frequently across many features:
 *
 * - dashboard macro totals,
 * - planner totals and comparisons,
 * - daily log summaries,
 * - pinned nutrient slots,
 * - calendar heatmaps,
 * - quick-access UI displays.
 *
 * Defining these as named constants avoids:
 * - repeated string literals (error-prone),
 * - inconsistent code usage,
 * - accidental mismatches between UI and domain logic,
 * - unnecessary object allocations.
 *
 * It also makes intent explicit in call sites:
 *
 * Example:
 * `totals[MacroKeys.PROTEIN]`
 * is clearer and safer than:
 * `totals[NutrientKey("PROTEIN_G")]`
 *
 * ## Behavior
 * Each property is a stable, reusable [NutrientKey] backed by a canonical nutrient code.
 *
 * Core macros:
 * - CALORIES (kcal)
 * - PROTEIN (g)
 * - CARBS (g)
 * - FAT (g)
 *
 * Common additional nutrients:
 * - SUGAR (g)
 * - FIBER (g)
 * - SODIUM (mg)
 *
 * ## Parameters
 * None.
 *
 * ## Return
 * Static singleton constants.
 *
 * ## Edge cases
 * - If a food lacks a given nutrient, lookups using these keys will return null.
 * - Presence depends entirely on stored nutrient data.
 *
 * ## Pitfalls / gotchas
 * - These keys do NOT guarantee the nutrient exists for a given food or snapshot.
 * - Do not assume all MacroKeys exist in all NutrientMaps.
 *
 * ## Architectural rules
 * - This object is domain-layer only.
 * - It defines semantic shortcuts, not new nutrients.
 * - Underlying canonical identity remains [NutrientCodes].
 */

/**
 * Terminology note:
 *
 * Nutrient = any measurable nutritional substance (vitamin, mineral, macro, etc.).
 *
 * Macronutrient ("macro") = a subset of nutrients required in large amounts that provide
 * energy or structural mass — primarily protein, carbohydrates, and fat (and calories as energy).
 *
 * All macros are nutrients, but not all nutrients are macros.
 */
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

/**
 * FUTURE-AI / MAINTENANCE KDoc (Do not remove)
 *
 * ## Invariants (must not change)
 * - These keys must always map directly to canonical NutrientCodes values.
 * - Do not redefine or remap codes here; this is a reference layer only.
 * - Keys must remain stable across app versions to avoid breaking saved targets, logs, or UI bindings.
 *
 * ## Do not refactor notes
 * - Do not convert these into dynamic lookups or repository calls.
 * - Do not inline string codes throughout the app; always use MacroKeys or NutrientCodes.
 * - Do not remove existing keys without auditing:
 *   - dashboard totals,
 *   - pinned nutrient system,
 *   - planner totals,
 *   - heatmap,
 *   - nutrition summaries.
 *
 * ## Architectural boundaries
 * - Domain constant definitions only.
 * - No persistence, no flows, no computation.
 *
 * ## Migration notes
 * If NutrientCodes ever changes:
 * - Update mappings here.
 * - Maintain backward compatibility with stored user targets if possible.
 *
 * ## Performance considerations
 * - Singleton constants avoid repeated NutrientKey allocations.
 * - Safe to use freely across hot paths (planner totals, log aggregation).
 */
object MacroKeysMaintenanceNote