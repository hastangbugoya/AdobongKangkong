import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.nutrition.gramsPerServingUnitResolved

/**
 * Centralized policy defining whether a [Food] can be used by *servings* and how
 * grams-per-serving is resolved.
 *
 * ----------------------------------------------------------------------------
 * Purpose
 * ----------------------------------------------------------------------------
 *
 * Nutrition math in this system is fundamentally mass-based (grams).
 *
 * However, users and external data sources often work in *servings*
 * (cup, piece, slice, bottle, etc.).
 *
 * To safely use servings in logging, recipes, or planner math, the system must
 * be able to convert:
 *
 * servings → grams → nutrients
 *
 * This object provides the single authoritative rule for determining whether that
 * conversion is possible.
 *
 * ----------------------------------------------------------------------------
 * Rationale
 * ----------------------------------------------------------------------------
 *
 * Foods are allowed to exist without grams-per-serving.
 *
 * This is intentional and required for:
 *
 * • CSV imports with incomplete metadata
 * • Early food creation workflows
 * • USDA imports where serving backing is missing
 *
 * Enforcing grams-per-serving at import time would incorrectly reject valid foods.
 *
 * Instead, correctness is enforced at the moment of *use*.
 *
 * This ensures:
 *
 * • flexible food creation and import
 * • strict nutrition accuracy at computation time
 * • consistent behavior across all domain features
 *
 * ----------------------------------------------------------------------------
 * Core invariant (DO NOT BREAK)
 * ----------------------------------------------------------------------------
 *
 * A food may be used by servings **iff grams-per-serving can be resolved**.
 *
 * Resolution may come from:
 *
 * • explicit gramsPerServingUnit
 * • inherent gram-based serving units (e.g., servingUnit == G)
 * • future density-backed conversions (if implemented)
 *
 * If grams-per-serving cannot be resolved, servings usage MUST be blocked.
 *
 * This prevents silent nutrition corruption.
 *
 * ----------------------------------------------------------------------------
 * Architectural role
 * ----------------------------------------------------------------------------
 *
 * Domain policy layer.
 *
 * Used by:
 *
 * • Logging flows (CreateLogEntryUseCase)
 * • Recipe ingredient math
 * • Planner projections
 * • Editor validation
 * • Future quick-add and automation flows
 *
 * This prevents duplicated logic and inconsistent validation.
 *
 * Callers MUST use this policy instead of directly checking gramsPerServingUnit.
 *
 * ----------------------------------------------------------------------------
 * UX integration
 * ----------------------------------------------------------------------------
 *
 * blockingReason() provides a consistent, user-facing explanation when servings
 * cannot be used.
 *
 * Centralizing messaging prevents inconsistent UX across screens.
 *
 * ----------------------------------------------------------------------------
 * Gotchas / pitfalls
 * ----------------------------------------------------------------------------
 *
 * DO NOT assume servings are always usable.
 *
 * Many imported foods initially lack grams backing.
 *
 * Always check:
 *
 * ServingPolicy.canUseServings(food)
 *
 * before enabling serving-based logging or recipe math.
 *
 * DO NOT bypass this policy by calling gramsPerServingUnitResolved() directly
 * from UI or use-cases.
 *
 * This object exists specifically to isolate and evolve that rule safely.
 *
 * ----------------------------------------------------------------------------
 * Future evolution
 * ----------------------------------------------------------------------------
 *
 * Possible upgrades:
 *
 * • density-based conversions (ml ↔ g)
 * • automatic grams inference from USDA data
 * • stricter validation at editor save time
 *
 * This wrapper ensures those changes can occur without breaking callers.
 */
object ServingPolicy {

    /**
     * Returns true if [food] can safely be used by servings.
     *
     * Internally this means grams-per-serving can be resolved.
     */
    fun canUseServings(food: Food): Boolean {
        return food.gramsPerServingUnitResolved() != null
    }

    /**
     * Returns resolved grams-per-serving, or null if unavailable.
     *
     * Callers should use this instead of directly accessing Food fields,
     * preserving policy abstraction.
     */
    fun gramsPerServing(food: Food): Double? {
        return food.gramsPerServingUnitResolved()
    }

    /**
     * User-facing explanation shown when servings cannot be used.
     *
     * Centralized here to ensure consistent UX messaging across features.
     */
    fun blockingReason(food: Food): String {
        return "“${food.name}” cannot be used by servings until grams-per-serving is set."
    }
}