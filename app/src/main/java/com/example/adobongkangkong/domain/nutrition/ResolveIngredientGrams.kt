import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.requiresGramsPerServing

/**
 * Resolves an ingredient entry into an exact gram amount for nutrition computation.
 *
 * ⚠️ STATUS: This logic may be deleted or replaced in the future as the recipe/ingredient
 * normalization pipeline evolves (especially if ingredient storage moves to always-canonical
 * gram basis at persistence time). Keep only while legacy ingredient rows or editor flows
 * depend on mixed servings/grams inputs.
 *
 * ----------------------------------------------------------------------------
 * Purpose
 * ----------------------------------------------------------------------------
 *
 * Recipe math, logging, and batch nutrition computation require all ingredient amounts
 * to be expressed in grams, since canonical nutrient storage and scaling operate on mass.
 *
 * However, ingredient inputs may be provided in two mutually exclusive forms:
 *
 * - Direct grams (amountGrams)
 * - Servings (amountServings), which must be converted using foodGramsPerServing
 *
 * This function safely resolves either form into grams while enforcing strict correctness rules.
 *
 * ----------------------------------------------------------------------------
 * Core invariants (MUST NOT BREAK)
 * ----------------------------------------------------------------------------
 *
 * Exactly one input must be set:
 *
 * ✔ allowed:
 *   servings only
 *   grams only
 *
 * ❌ blocked:
 *   both servings AND grams set
 *   neither servings NOR grams set
 *
 * This prevents ambiguity and silent double-scaling bugs.
 *
 * ----------------------------------------------------------------------------
 * Conversion rules
 * ----------------------------------------------------------------------------
 *
 * If grams provided:
 *
 *   grams = amountGrams
 *
 * No further conversion required.
 *
 * If servings provided:
 *
 *   grams = amountServings × foodGramsPerServing
 *
 * BUT ONLY if the food's serving unit requires a grams backing.
 *
 * Example:
 *
 * 2 servings × 30 g/serving = 60 g
 *
 * ----------------------------------------------------------------------------
 * Density safety rule (critical)
 * ----------------------------------------------------------------------------
 *
 * If the serving unit is not inherently mass-based (cup, tbsp, piece, etc.),
 * then grams-per-serving MUST be explicitly defined.
 *
 * This system NEVER guesses density automatically.
 *
 * If missing:
 *
 * → returns Blocked("Set grams-per-serving for this food (no density guessing).")
 *
 * This prevents major nutrition inaccuracies.
 *
 * ----------------------------------------------------------------------------
 * Result contract
 * ----------------------------------------------------------------------------
 *
 * Ok(grams)
 *   Valid gram amount ready for canonical nutrition math.
 *
 * Blocked(message)
 *   Conversion could not be performed safely.
 *   Caller must prompt user or fix data.
 *
 * ----------------------------------------------------------------------------
 * Architectural role
 * ----------------------------------------------------------------------------
 *
 * Used by:
 *
 * - Recipe ingredient normalization
 * - Batch nutrition computation
 * - Logging pipeline (if ingredient-based)
 * - Any system converting mixed unit inputs → canonical gram basis
 *
 * This function sits at the boundary between user-level units and canonical math.
 *
 * ----------------------------------------------------------------------------
 * Pitfalls / gotchas
 * ----------------------------------------------------------------------------
 *
 * Do NOT:
 *
 * - Guess grams from volume units
 * - Assume servings always equal grams
 * - Allow both grams and servings simultaneously
 *
 * Always respect the requiresGramsPerServing() contract.
 *
 * ----------------------------------------------------------------------------
 * Example cases
 * ----------------------------------------------------------------------------
 *
 * Example 1 — direct grams:
 *
 * amountGrams = 150
 *
 * → Ok(150)
 *
 *
 * Example 2 — servings with known grams backing:
 *
 * amountServings = 2
 * gramsPerServing = 30
 *
 * → Ok(60)
 *
 *
 * Example 3 — servings without grams backing:
 *
 * amountServings = 1
 * gramsPerServing = null
 * unit = CUP
 *
 * → Blocked
 */
sealed interface IngredientGramsResult {

    /** Successfully resolved gram amount. */
    data class Ok(val grams: Double) : IngredientGramsResult

    /** Conversion could not be performed safely. */
    data class Blocked(val message: String) : IngredientGramsResult
}

fun resolveIngredientGrams(
    amountServings: Double?,
    amountGrams: Double?,
    foodServingUnit: ServingUnit,
    foodGramsPerServing: Double?
): IngredientGramsResult {

    val hasServings = amountServings != null && amountServings > 0.0
    val hasGrams = amountGrams != null && amountGrams > 0.0

    if (hasServings && hasGrams) {
        return IngredientGramsResult.Blocked("Ingredient has both servings and grams set.")
    }
    if (!hasServings && !hasGrams) {
        return IngredientGramsResult.Blocked("Set either servings or grams for this ingredient.")
    }

    if (hasGrams) {
        return IngredientGramsResult.Ok(amountGrams!!)
    }

    // servings path
    val needsBacking = foodServingUnit.requiresGramsPerServing()
    if (needsBacking && foodGramsPerServing == null) {
        return IngredientGramsResult.Blocked("Set grams-per-serving for this food (no density guessing).")
    }

    val grams = amountServings!! * (foodGramsPerServing ?: 1.0)
    return IngredientGramsResult.Ok(grams)
}

/**
 * =============================================================================
 * FOR FUTURE AI ASSISTANT / FUTURE DEVELOPER — lifecycle and migration notes
 * =============================================================================
 *
 * Likely future deletion path
 *
 * Long-term architecture goal is to store ingredient amounts already normalized to grams
 * at persistence time, removing the need for runtime resolution.
 *
 * Once all ingredient rows guarantee:
 *
 * - exactly one canonical gram value
 *
 * this function becomes unnecessary.
 *
 *
 * Migration checklist before deletion
 *
 * - RecipeIngredientEntity stores canonical grams only
 * - Ingredient editor enforces grams normalization at save time
 * - USDA importer populates grams backing consistently
 * - No callers depend on resolving servings dynamically
 *
 *
 * DO NOT weaken validation rules.
 *
 * Silent fallback or density guessing will corrupt nutrition totals.
 */