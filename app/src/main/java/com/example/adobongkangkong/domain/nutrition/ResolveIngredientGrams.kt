package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.requiresGramsPerServing

/**
 * Resolves an ingredient row into grams for nutrition math.
 *
 * Rule:
 * - exactly one of servings or grams must be set (>0).
 * - servings path requires grams-per-serving when the food uses a non-gram unit (no density guessing).
 */
sealed interface IngredientGramsResult {
    data class Ok(val grams: Double) : IngredientGramsResult
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
