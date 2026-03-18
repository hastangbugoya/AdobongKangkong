package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.ServingUnit

/**
 * Returns **grams per ONE serving unit** of this food.
 *
 * • Does NOT include servingSize multiplication
 * • Used for unit-level conversion and scaling math
 *
 * Example:
 * • servingSize = 2
 * • servingUnit = TBSP
 * • gramsPerServingUnit = 18.5
 *
 * Result:
 * • gramsPerServingUnitResolved() = 18.5 g per TBSP
 *
 * To get grams per serving, use [gramsPerServingResolved].
 *
 * Resolution rules:
 * • ServingUnit.G → returns 1.0
 * • Other deterministic mass units → returns that unit's grams value
 * • ServingUnit.ML → returns gramsPerServingUnit bridge if present
 * • All other units → returns gramsPerServingUnit bridge
 *
 * Returns:
 * • grams per ONE unit
 * • null if mass grounding unknown
 *
 * Safety:
 * • Never infers density
 * • Never guesses conversions
 * • Pure domain helper (no DB/UI)
 */
fun Food.gramsPerServingUnitResolved(): Double? {
    return when {
        servingUnit == ServingUnit.G -> 1.0
        servingUnit.asG != null -> servingUnit.asG
        servingUnit == ServingUnit.ML -> gramsPerServingUnit
        else -> gramsPerServingUnit
    }?.takeIf { it > 0.0 }
}

/**
 * Returns **grams per ONE serving**.
 *
 * This multiplies:
 *
 * • gramsPerServingUnitResolved() × servingSize
 *
 * This matches food label values.
 *
 * Example (Nutella):
 * • servingSize = 2 tbsp
 * • gramsPerServingUnit = 18.5
 *
 * Result:
 * • gramsPerServingResolved() = 37 g
 *
 * Use this for:
 * • logging servings → grams
 * • displaying "(37 g)" in UI
 * • nutrition scaling
 * • default gram suggestions
 *
 * Returns:
 * • grams per serving
 * • null if conversion unknown
 *
 * Safety:
 * • Never infers density
 * • Never guesses conversions
 */
fun Food.gramsPerServingResolved(): Double? {
    val gramsPerUnit = gramsPerServingUnitResolved() ?: return null
    if (gramsPerUnit <= 0.0) return null
    if (servingSize <= 0.0) return null
    return gramsPerUnit * servingSize
}

/**
 * FUTURE-MAINTENANCE NOTES
 *
 * These functions serve different purposes:
 *
 * gramsPerServingUnitResolved()
 * • grams per ONE unit
 *
 * gramsPerServingResolved()
 * • grams per ONE serving
 *
 * Example:
 *
 * servingSize = 2 tbsp
 * gramsPerServingUnit = 18.5
 *
 * Results:
 * • per unit    = 18.5 g
 * • per serving = 37 g
 *
 * Important:
 * • For ServingUnit.G, per-unit grams must be 1.0, not servingSize.
 * • Otherwise servingSize gets multiplied twice.
 *
 * Do NOT:
 * • merge functions
 * • multiply servingSize twice
 * • infer grams from volume
 *
 * Future density support:
 * • ServingUnit.ML may compute grams automatically
 * • explicit bridges must override computed values
 *
 * Performance:
 * • O(1)
 * • safe for hot paths
 */