package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.ServingUnit

/**
 * Resolves the grams represented by one declared serving unit for a [Food], if determinable.
 *
 * ## Purpose
 * Provide a single, consistent way to interpret the mass grounding of a food’s serving unit
 * so nutrition scaling and snapshot creation can convert servings → grams safely.
 *
 * ## Rationale (why this exists)
 * Foods in the system may originate from:
 * - USDA imports (where grams-per-serving is explicitly provided),
 * - user-entered foods (where serving units may be mass, volume, or arbitrary),
 * - recipe snapshots (which require deterministic grams scaling).
 *
 * The meaning of `gramsPerServingUnit` varies depending on the declared [ServingUnit]:
 *
 * - If the serving unit is already grams ([ServingUnit.G]), then the serving size itself
 *   represents grams directly, and no bridge field is needed.
 *
 * - If the serving unit is volume ([ServingUnit.ML]), grams cannot be inferred without density.
 *   Therefore this function returns the explicit `gramsPerServingUnit` bridge if present.
 *
 * - For all other units (piece, cup, tbsp, slice, etc.), grams must come from the explicit
 *   `gramsPerServingUnit` bridge captured at import or entered by the user.
 *
 * This abstraction prevents duplicate logic and ensures mass scaling decisions remain consistent
 * across logging, recipe computation, and nutrient canonicalization.
 *
 * ## Behavior
 * - Returns grams per one serving unit if determinable.
 * - Returns null when no reliable mass grounding exists.
 *
 * Resolution rules:
 * - ServingUnit.G → returns servingSize (already in grams).
 * - ServingUnit.ML → returns gramsPerServingUnit (density-dependent bridge).
 * - All other units → returns gramsPerServingUnit (explicit bridge only).
 *
 * ## Parameters
 * Receiver: [Food]
 * - Must have valid servingUnit and servingSize defined.
 *
 * ## Return
 * - Double representing grams per one serving unit.
 * - null if no deterministic grams conversion is available.
 *
 * ## Edge cases
 * - Volume units without density information return null.
 * - Foods with missing or zero bridge values return null.
 *
 * ## Pitfalls / gotchas
 * - Do NOT assume 1 mL = 1 g except where explicitly stored. Density varies widely.
 * - For ServingUnit.G, gramsPerServingUnit should typically remain null because servingSize
 *   already expresses grams directly.
 * - This function resolves grams-per-unit, not grams-per-serving. Callers must multiply by servingSize
 *   if they need grams-per-serving.
 *
 * ## Architectural rules
 * - Pure domain helper; no repository access.
 * - Must remain deterministic and side-effect free.
 * - Must not guess density or infer conversions.
 */
fun Food.gramsPerServingUnitResolved(): Double? {
    return when (servingUnit) {
        ServingUnit.G -> servingSize
        ServingUnit.ML -> gramsPerServingUnit // later: density
        else -> gramsPerServingUnit
    }
}

/**
 * FUTURE-AI / MAINTENANCE KDoc (Do not remove)
 *
 * ## Invariants (must not change)
 * - This function must NEVER infer grams from volume units without an explicit bridge.
 * - ServingUnit.G must always resolve directly to servingSize.
 * - Returned value represents grams per ONE unit of servingUnit, not total serving mass.
 *
 * ## Do not refactor notes
 * - Do not collapse logic into a simple `return gramsPerServingUnit` — ServingUnit.G is special.
 * - Do not auto-convert ML → grams unless density becomes an explicit field on Food.
 * - Keep this as an extension function on Food to ensure consistent resolution everywhere.
 *
 * ## Architectural boundaries
 * - Domain utility only.
 * - No database access, no logging, no UI dependencies.
 *
 * ## Migration notes (future density support)
 * When density support is added:
 * - Food will likely gain `gramsPerMl` or equivalent.
 * - ServingUnit.ML branch should compute gramsPerServingUnit using density when bridge missing.
 * - Ensure USDA-provided bridges still override computed density values.
 *
 * ## Performance considerations
 * - O(1) computation.
 * - Safe to call frequently in scaling and logging pipelines.
 */