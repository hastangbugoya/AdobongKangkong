package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodEntity
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity
import com.example.adobongkangkong.domain.model.ServingUnit

/**
 * ⚠️ LEGACY / TRANSITIONAL USE CASE — may be deleted in a future migration.
 *
 * ## Purpose
 * Normalize a food’s nutrient rows into a per-gram representation (`nutrientsPerGram`),
 * which is the canonical scaling basis used by recipe computation, logging snapshots,
 * and batch nutrition calculations.
 *
 * ## Rationale (why this exists)
 * Foods can store nutrients in multiple basis types depending on origin:
 *
 * - PER_100G → already mass-normalized (ideal canonical form)
 * - USDA_REPORTED_SERVING → nutrients defined per serving (requires conversion)
 * - PER_100ML → volume-normalized (cannot convert safely without density)
 *
 * Many downstream systems (recipe batch computation, snapshot logging, planner totals)
 * require nutrients expressed per gram for deterministic scaling.
 *
 * This use case provides a strict, density-safe normalization step that:
 * - converts valid mass-grounded inputs into per-gram values,
 * - blocks unsafe conversions,
 * - prevents silent density assumptions.
 *
 * NOTE:
 * This logic may be removed once all foods are guaranteed to be canonicalized at save-time
 * (see SaveFoodWithNutrientsUseCase canonicalization pipeline).
 *
 * ## Behavior
 * Iterates all [FoodNutrientEntity] rows and converts each to:
 *
 * perGramAmount = nutrientAmountPerBasis / gramsRepresentedByBasis
 *
 * Conversion rules:
 *
 * PER_100G:
 * - nutrientAmountPerBasis is per 100 g → divide by 100.
 *
 * USDA_REPORTED_SERVING:
 * - If servingUnit == G:
 *     grams = servingSize
 * - Else:
 *     grams = gramsPerServingUnit (explicit bridge required)
 * - perGram = nutrientAmountPerBasis / grams
 *
 * PER_100ML:
 * - Blocked (no density inference allowed).
 *
 * Duplicate nutrientIds are summed deterministically.
 *
 * ## Parameters
 * - food:
 *     Provides servingUnit, servingSize, and gramsPerServingUnit bridge.
 *
 * - nutrientRows:
 *     Raw nutrient rows stored for this food.
 *
 * ## Return
 * Result.Ok(perGram):
 *     Map of nutrientId → amount per gram.
 *
 * Result.Blocked(message):
 *     Returned when safe normalization is impossible.
 *
 * ## Edge cases
 * - Empty nutrient list → Blocked.
 * - Zero or negative serving size → Blocked.
 * - Missing gramsPerServingUnit for non-gram serving → Blocked.
 * - PER_100ML rows always Blocked.
 *
 * ## Pitfalls / gotchas
 * - This use case intentionally refuses volume→mass conversion.
 * - Do NOT add density guessing here.
 * - Foods with incomplete grounding must be fixed upstream, not forced here.
 *
 * ## Architectural rules
 * - Pure domain transformation.
 * - No database access.
 * - No writes or side effects.
 */
class NormalizeFoodNutrientsPerGramUseCase {

    sealed interface Result {
        data class Ok(val perGram: Map<Long, Double>) : Result
        data class Blocked(val message: String) : Result
    }

    fun execute(
        food: FoodEntity,
        nutrientRows: List<FoodNutrientEntity>
    ): Result {
        if (nutrientRows.isEmpty()) return Result.Blocked("Food nutrition incomplete.")

        val perGram = mutableMapOf<Long, Double>()

        for (row in nutrientRows) {

            val perGramAmt: Double = when (row.basisType) {

                // Already normalized: X per 100 g → X / 100 per gram
                BasisType.PER_100G -> {
                    row.nutrientAmountPerBasis / 100.0
                }

                // Serving-based snapshot (USDA or user-defined)
                BasisType.USDA_REPORTED_SERVING -> {

                    // Case 1: serving itself is grams (e.g. servingSize = 37 g)
                    if (food.servingUnit == ServingUnit.G) {
                        val grams = food.servingSize
                        if (grams <= 0.0) {
                            return Result.Blocked("Invalid serving size.")
                        }
                        row.nutrientAmountPerBasis / grams
                    }

                    // Case 2: non-gram serving with explicit grams-per-serving backing
                    else {
                        val gpsu = food.gramsPerServingUnit
                            ?: return Result.Blocked("Set grams-per-serving (no density guessing).")

                        if (gpsu <= 0.0) {
                            return Result.Blocked("Invalid grams-per-serving.")
                        }
                        row.nutrientAmountPerBasis / gpsu
                    }
                }

                // Volume-based normalization cannot be converted to grams without density
                BasisType.PER_100ML -> {
                    return Result.Blocked("Cannot normalize PER_100ML to grams without density.")
                }
            }

            perGram[row.nutrientId] =
                (perGram[row.nutrientId] ?: 0.0) + perGramAmt
        }

        return Result.Ok(perGram)
    }
}

/**
 * FUTURE-AI / MAINTENANCE KDoc (Do not remove)
 *
 * ## Invariants
 * - Must NEVER convert PER_100ML to grams without an explicit density field.
 * - Must NEVER guess density.
 * - Must NEVER silently coerce invalid foods.
 *
 * ## Why this may be deleted
 * The modern pipeline canonicalizes nutrients at save-time:
 * - SaveFoodWithNutrientsUseCase ensures foods are stored as PER_100G or PER_100ML.
 * - FoodNutritionSnapshot stores nutrientsPerGram directly.
 *
 * Once all foods guarantee canonical mass grounding,
 * runtime normalization becomes unnecessary.
 *
 * ## Do not refactor notes
 * - Do not weaken blocking behavior.
 * - Do not silently fallback to incorrect conversions.
 *
 * ## Architectural boundaries
 * - Pure compute utility.
 * - No repository access.
 *
 * ## Migration path
 * Eventually replaced by:
 * - canonical nutrient storage
 * - snapshot-based nutrient access
 *
 * ## Performance
 * O(N) over nutrient rows.
 * Safe for occasional use, not intended for hot paths.
 */