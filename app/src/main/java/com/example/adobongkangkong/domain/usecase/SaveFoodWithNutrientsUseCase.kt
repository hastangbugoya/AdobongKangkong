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

class SaveFoodWithNutrientsUseCase @Inject constructor(
    private val foods: FoodRepository,
    private val foodNutrients: FoodNutrientRepository
) {

    suspend operator fun invoke(
        food: Food,
        rows: List<FoodNutrientRow>
    ): Long {

        // Lock-in deterministic grounding bridges when safe and blank.
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

        // Only lock in for mass units where the conversion is deterministic.
        // For ServingUnit.G, gramsPerServingUnit is unnecessary; keep null.
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

        // CRITICAL RULE (2026-02-06):
        // Compute at most ONE grounding per food. If we already have mass grounding,
        // we must not auto-add a volume bridge (would create ambiguity / violate invariants).
        val alreadyMassGrounded =
            (this.servingUnit.isMassUnit()) || (this.gramsPerServingUnit?.takeIf { it > 0.0 } != null)
        if (alreadyMassGrounded) return this

        // Only lock in for volume units where conversion is deterministic.
        // For ServingUnit.ML, mlPerServingUnit is unnecessary; keep null.
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
     * - Otherwise USDA_REPORTED_SERVING (raw, not safely scalable)
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
                                row.copy(basisType = BasisType.PER_100G, basisGrams = 100.0)

                            BasisType.USDA_REPORTED_SERVING ->
                                row.copy(
                                    basisType = BasisType.PER_100G,
                                    amount = row.amount * factor,
                                    basisGrams = 100.0
                                )

                            // Never convert between grams and mL without explicit density.
                            else -> null
                        }
                    }
                }

                BasisType.PER_100ML -> {
                    val factor = 100.0 / mlPerServing!!
                    rows.mapNotNull { row ->
                        when (row.basisType) {
                            BasisType.PER_100ML ->
                                row.copy(basisType = BasisType.PER_100ML, basisGrams = 100.0)

                            BasisType.USDA_REPORTED_SERVING ->
                                row.copy(
                                    basisType = BasisType.PER_100ML,
                                    amount = row.amount * factor,
                                    basisGrams = 100.0
                                )

                            // Never convert between grams and mL without explicit density.
                            else -> null
                        }
                    }
                }

                else -> {
                    rows.map {
                        it.copy(basisType = BasisType.USDA_REPORTED_SERVING, basisGrams = null)
                    }
                }
            }

        // Hard guarantee: ONE row per nutrientId (within the chosen basis).
        // Preference order:
        // 1) if any row was already in the target basis, keep that (stable)
        // 2) else keep the first converted row (deterministic)
        return converted
            .groupBy { it.nutrient.id }
            .mapNotNull { (_, group) ->
                group.firstOrNull { it.basisType == targetBasis } ?: group.firstOrNull()
            }
    }

    private fun computeGramsPerServing(food: Food): Double? {
        // Truth-first: if an explicit bridge exists, it wins over unit-based conversions.
        val bridgedGPer1 = food.gramsPerServingUnit?.takeIf { it > 0.0 }
        val grams = when {
            bridgedGPer1 != null -> food.servingSize * bridgedGPer1
            food.servingUnit.isMassUnit() -> food.servingUnit.toGrams(food.servingSize)
            else -> null
        }
        return grams?.takeIf { it > 0.0 }
    }

    private fun computeMlPerServing(food: Food): Double? {
        // Truth-first: if an explicit bridge exists, it wins over unit-based conversions.
        // This is required for USDA liquids where servingUnit may be a parseable display unit (CUP/FLOZ/CAN/etc)
        // but USDA servingSize in mL is authoritative and stored in mlPerServingUnit.
        val bridgedMlPer1 = food.mlPerServingUnit?.takeIf { it > 0.0 }
        val ml = when {
            bridgedMlPer1 != null -> food.servingSize * bridgedMlPer1
            food.servingUnit.isVolumeUnit() -> food.servingUnit.toMilliliters(food.servingSize)
            else -> null
        }
        return ml?.takeIf { it > 0.0 }
    }
}

/**
 * FUTURE-YOU NOTE (2026-02-06) 3:33pm:
 *
 * Canonical nutrients MUST be single-basis per nutrientId per food.
 *
 * - Target basis selection:
 *     - If grounded in grams -> PER_100G
 *     - Else if grounded in mL -> PER_100ML
 *     - Else -> USDA_REPORTED_SERVING
 *
 * - "Single grounding per food" invariant:
 *     - If mass grounding is present (mass unit OR gramsPerServingUnit != null), do NOT auto-lock volume grounding.
 *     - If volume grounding is present (volume unit OR mlPerServingUnit != null), do NOT infer grams (no density).
 *
 * - Density is NOT guessed. Never convert between grams and mL without a future explicit density field.
 */


/**
 * FUTURE-YOU NOTE (2026-02-06) 1:00pm:
 *
 * Canonical nutrients MUST be single-basis per nutrientId per food.
 *
 * - We persist exactly one basis row per nutrient (enforced here before replaceForFood()).
 * - Target basis selection:
 *     - If grounded in grams -> PER_100G
 *     - Else if grounded in mL -> PER_100ML
 *     - Else -> USDA_REPORTED_SERVING
 *
 * - IMPORTANT GRAMMING RULE:
 *     - If servingUnit is mass: gramsPerServing = toGrams(servingSize)
 *     - Else if gramsPerServingUnit exists: gramsPerServing = servingSize * gramsPerServingUnit
 *       (gramsPerServingUnit means grams per 1 unit of servingUnit)
 *
 * - We may lock in gramsPerServingUnit for mass unit
 * ============================================================================
 */

/**
 * AI NOTE (2026-02-06):
 *
 * TRUTH-FIRST VOLUME GROUNDING:
 * - If mlPerServingUnit is present, it is authoritative and must be used for ml-per-serving computations
 *   even when servingUnit is a volume unit (CUP/FLOZ/etc). This is required for USDA branded liquids where
 *   USDA's servingSize in mL is the truth and household text is display-only.
 */
