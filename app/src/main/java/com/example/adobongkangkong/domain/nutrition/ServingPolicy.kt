package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.domain.model.Food

/**
 * Centralized rules governing whether a [Food] can be used by *servings*.
 *
 * ## Why this exists
 * CSV import allows foods to exist without grams-per-serving.
 * That is correct — we only enforce correctness at the moment of *use*.
 *
 * This object centralizes that rule so:
 *  - logging
 *  - recipe building
 *  - future planners / quick add flows
 * all behave consistently.
 *
 * ## Core rule
 * A food can be used by servings **iff** grams-per-serving can be resolved.
 */
object ServingPolicy {

    /**
     * Returns true if the given [food] can be used by servings.
     *
     * Internally this means:
     * - The food either already has grams-per-serving
     * - OR grams-per-serving can be derived
     *
     * Currently this delegates to [Food.gramsPerServingUnitResolved].
     */
    fun canUseServings(food: Food): Boolean {
        return food.gramsPerServingUnitResolved() != null
    }

    /**
     * Returns the resolved grams-per-serving, or null if unavailable.
     *
     * This exists as a semantic wrapper so callers do NOT rely directly
     * on [Food.gramsPerServingUnitResolved].
     *
     * That gives us a clean upgrade path if rules evolve.
     */
    fun gramsPerServing(food: Food): Double? {
        return food.gramsPerServingUnitResolved()
    }

    /**
     * Returns the blocking reason message shown to the user when servings
     * cannot be used.
     *
     * Centralizing this keeps UX consistent across:
     *  - logging
     *  - recipe builder
     *  - future flows
     */
    fun blockingReason(food: Food): String {
        return "“${food.name}” cannot be used by servings until grams-per-serving is set."
    }
}
