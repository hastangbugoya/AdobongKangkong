package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodNutrientRow
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.isMassUnit
import com.example.adobongkangkong.domain.model.isVolumeUnit
import com.example.adobongkangkong.domain.model.toGrams
import com.example.adobongkangkong.domain.model.toMilliliters
import com.example.adobongkangkong.domain.repository.FoodNutrientRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
import javax.inject.Inject

/**
 * Saves a [Food] and its nutrient rows, enforcing canonical single-basis nutrient persistence rules.
 *
 * ## Purpose
 * Persist a food record and replace its nutrient rows in one operation while ensuring nutrient data
 * is stored in a deterministic, scalable form (single basis per nutrient per food).
 *
 * ## Rationale (why this use case exists)
 * Foods may arrive from multiple sources (manual entry, USDA import, edits). Nutrient rows can be a
 * mix of:
 * - PER_100G / PER_100ML (scalable, canonical), and/or
 * - USDA_REPORTED_SERVING (a snapshot of a reported serving that may not be safely scalable).
 *
 * Without a centralized save step that canonicalizes rows, the app can end up with:
 * - multiple rows for the same nutrient in different bases,
 * - ambiguous scaling behavior,
 * - accidental grams↔mL conversions without density,
 * - unstable day-to-day results when different call sites choose different “preferred” bases.
 *
 * This use case exists to keep persistence deterministic and to enforce locked invariants that
 * protect nutrition correctness across the app.
 *
 * ## Behavior
 * - Applies “safe grounding lock-in” to the incoming [Food] when its conversion bridge fields are blank:
 *   - Locks in `gramsPerServingUnit` for deterministic mass units only when needed.
 *   - Locks in `mlPerServingUnit` for deterministic volume units only when needed,
 *     **but only if the food is not already mass-grounded** (single-grounding rule).
 * - Upserts the grounded food via [FoodRepository].
 * - Canonicalizes incoming nutrient [rows] into exactly one basis (PER_100G, PER_100ML, or USDA_REPORTED_SERVING)
 *   and ensures exactly one row per nutrient id.
 * - Replaces all persisted nutrient rows for the saved food via [FoodNutrientRepository].
 *
 * ## Important serving rule
 * For deterministic mass units like `g`, `oz`, `lb`, `kg`:
 * - the serving unit itself already defines the gram bridge
 * - `servingSize` tells us how many of those units make one serving
 *
 * Example:
 * - 50 g serving
 * - user enters 360 kcal as per-serving UI value
 * - canonical PER_100G stored value must become:
 *   360 * (100 / 50) = 720
 *
 * Therefore:
 * - foods with servingUnit=G must still canonicalize to PER_100G correctly
 * - but we do NOT need to persist gramsPerServingUnit for ServingUnit.G
 *
 * ## Parameters
 * - `food`: The food to upsert. May be modified only by deterministic grounding lock-in rules.
 * - `rows`: Nutrient rows for the food. May include mixed bases; will be canonicalized.
 *
 * ## Return
 * The persisted `foodId` returned from [FoodRepository.upsert].
 */
class SaveFoodWithNutrientsUseCase @Inject constructor(
    private val foods: FoodRepository,
    private val foodNutrients: FoodNutrientRepository
) {

    suspend operator fun invoke(
        food: Food,
        rows: List<FoodNutrientRow>
    ): Long {

        val foodToPersist = food
            .withLockedInMassGroundingIfPossible()
            .withLockedInVolumeGroundingIfPossible()

        val foodId = foods.upsert(foodToPersist)

        val canonicalRows = canonicalizeToSingleBasis(foodToPersist, rows)

        foodNutrients.replaceForFood(foodId, canonicalRows)
        return foodId
    }

    private fun Food.withLockedInMassGroundingIfPossible(): Food {
        val existing = this.gramsPerServingUnit?.takeIf { it > 0.0 }
        if (existing != null) return this

        // For deterministic mass units except plain grams, we can safely persist grams per 1 unit.
        // For ServingUnit.G we keep null because the unit itself already means 1 g.
        if (this.servingUnit.isMassUnit() && this.servingUnit != ServingUnit.G) {
            val gramsPer1Unit = this.servingUnit.toGrams(1.0)
            if (gramsPer1Unit != null && gramsPer1Unit > 0.0) {
                return this.copy(gramsPerServingUnit = gramsPer1Unit)
            }
        }

        return this
    }

    private fun Food.withLockedInVolumeGroundingIfPossible(): Food {
        val existing = this.mlPerServingUnit?.takeIf { it > 0.0 }
        if (existing != null) return this

        // Single-grounding rule:
        // if this food is already mass-grounded, do not also infer a volume bridge.
        val alreadyMassGrounded =
            this.servingUnit.isMassUnit() || effectiveGramsPerServingUnit(this) != null
        if (alreadyMassGrounded) return this

        // For deterministic volume units except plain mL, we can safely persist mL per 1 unit.
        // For ServingUnit.ML we keep null because the unit itself already means 1 mL.
        if (this.servingUnit.isVolumeUnit() && this.servingUnit != ServingUnit.ML) {
            val mlPer1Unit = this.servingUnit.toMilliliters(1.0)
            if (mlPer1Unit != null && mlPer1Unit > 0.0) {
                return this.copy(mlPerServingUnit = mlPer1Unit)
            }
        }

        return this
    }

    /**
     * Ensures we persist exactly ONE basis row per nutrient per food by choosing ONE target basis:
     * - PER_100G when the food can be grounded in grams
     * - PER_100ML when the food can be grounded in milliliters
     * - Otherwise USDA_REPORTED_SERVING
     *
     * Also dedupes rows so there is exactly one row per nutrientId in the returned list.
     */
    private fun canonicalizeToSingleBasis(
        food: Food,
        rows: List<FoodNutrientRow>
    ): List<FoodNutrientRow> {

        val gramsPerServing: Double? = computeGramsPerServing(food)
        val mlPerServing: Double? = computeMlPerServing(food)

        val targetBasis: BasisType =
            when {
                gramsPerServing != null -> BasisType.PER_100G
                mlPerServing != null -> BasisType.PER_100ML
                else -> BasisType.USDA_REPORTED_SERVING
            }

        val converted: List<FoodNutrientRow> =
            when (targetBasis) {

                BasisType.PER_100G -> {
                    val factor = 100.0 / gramsPerServing!!
                    rows.mapNotNull { row ->
                        when (row.basisType) {

                            BasisType.PER_100G ->
                                row.copy(
                                    basisType = BasisType.PER_100G,
                                    basisGrams = 100.0
                                )

                            BasisType.USDA_REPORTED_SERVING ->
                                row.copy(
                                    basisType = BasisType.PER_100G,
                                    amount = row.amount * factor,
                                    basisGrams = 100.0
                                )

                            // Never convert volume basis into mass basis without density.
                            BasisType.PER_100ML -> null
                        }
                    }
                }

                BasisType.PER_100ML -> {
                    val factor = 100.0 / mlPerServing!!
                    rows.mapNotNull { row ->
                        when (row.basisType) {

                            BasisType.PER_100ML ->
                                row.copy(
                                    basisType = BasisType.PER_100ML,
                                    basisGrams = 100.0
                                )

                            BasisType.USDA_REPORTED_SERVING ->
                                row.copy(
                                    basisType = BasisType.PER_100ML,
                                    amount = row.amount * factor,
                                    basisGrams = 100.0
                                )

                            // Never convert mass basis into volume basis without density.
                            BasisType.PER_100G -> null
                        }
                    }
                }

                BasisType.USDA_REPORTED_SERVING -> {
                    rows.map {
                        it.copy(
                            basisType = BasisType.USDA_REPORTED_SERVING,
                            basisGrams = null
                        )
                    }
                }
            }

        return converted
            .groupBy { it.nutrient.id }
            .mapNotNull { (_, group) ->
                group.firstOrNull { it.basisType == targetBasis } ?: group.firstOrNull()
            }
    }

    private fun effectiveGramsPerServingUnit(food: Food): Double? {
        return when {
            food.servingUnit.isMassUnit() -> food.servingUnit.toGrams(1.0)
            else -> food.gramsPerServingUnit
        }?.takeIf { it > 0.0 }
    }

    private fun effectiveMlPerServingUnit(food: Food): Double? {
        return when (food.servingUnit) {
            ServingUnit.ML -> 1.0
            ServingUnit.L -> 1000.0
            else -> food.mlPerServingUnit
        }?.takeIf { it > 0.0 }
    }

    private fun computeGramsPerServing(food: Food): Double? {
        val gramsPer1Unit = effectiveGramsPerServingUnit(food) ?: return null
        val servingSize = food.servingSize.takeIf { it > 0.0 } ?: return null
        return (servingSize * gramsPer1Unit).takeIf { it > 0.0 }
    }

    private fun computeMlPerServing(food: Food): Double? {
        // Single-grounding rule:
        // if mass grounding exists, do not also treat this as volume-grounded.
        if (computeGramsPerServing(food) != null) return null

        val mlPer1Unit = effectiveMlPerServingUnit(food) ?: return null
        val servingSize = food.servingSize.takeIf { it > 0.0 } ?: return null
        return (servingSize * mlPer1Unit).takeIf { it > 0.0 }
    }
}

/**
 * FUTURE-AI / MAINTENANCE KDoc (Do not remove)
 *
 * ## Invariants (must not change)
 * - Single-basis persistence:
 *   - Persist exactly ONE row per nutrient id per food.
 *   - The persisted rows must all share the same target basis chosen for that food.
 * - Target basis selection must remain:
 *   - If grams-per-serving can be computed -> PER_100G
 *   - Else if mL-per-serving can be computed -> PER_100ML
 *   - Else -> USDA_REPORTED_SERVING
 * - Truth-first bridges:
 *   - Deterministic mass units count as an effective grams bridge even when gramsPerServingUnit is null.
 *   - Deterministic volume units count as an effective mL bridge even when mlPerServingUnit is null.
 * - No grams↔mL conversion without explicit density.
 * - Single grounding per food:
 *   - If mass grounding exists, do NOT also canonicalize as volume.
 *
 * ## Do not refactor notes
 * - Do not rework canonicalization to store multiple bases.
 * - Do not remove deterministic mass-unit handling for ServingUnit.G / OZ / LB / KG.
 *   That is the fix for foods like “50 g serving” showing 100 g nutrition in the editor/logging path.
 * - Keep unsafe mass<->volume conversion impossible here.
 *
 * ## Architectural boundaries
 * - This use case owns persistence correctness rules for saving foods + nutrient rows.
 * - Repositories are the only persistence boundary; no direct DB/Room code may be added here.
 */