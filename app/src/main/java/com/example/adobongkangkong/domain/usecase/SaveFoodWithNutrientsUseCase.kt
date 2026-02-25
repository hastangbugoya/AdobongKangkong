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
 *   - Locks in `gramsPerServingUnit` for deterministic mass units (except grams itself).
 *   - Locks in `mlPerServingUnit` for deterministic volume units (except milliliters itself),
 *     **but only if the food is not already mass-grounded** (single-grounding rule).
 * - Upserts the grounded food via [FoodRepository].
 * - Canonicalizes incoming nutrient [rows] into exactly one basis (PER_100G, PER_100ML, or USDA_REPORTED_SERVING)
 *   and ensures exactly one row per nutrient id.
 * - Replaces all persisted nutrient rows for the saved food via [FoodNutrientRepository].
 *
 * ## Parameters
 * - `food`: The food to upsert. May be modified only by deterministic grounding lock-in rules.
 * - `rows`: Nutrient rows for the food. May include mixed bases; will be canonicalized.
 *
 * ## Return
 * The persisted `foodId` returned from [FoodRepository.upsert].
 *
 * ## Edge cases
 * - Empty [rows] is allowed; nutrients are replaced with an empty set.
 * - If the food cannot be safely grounded in grams or mL, nutrients are persisted as USDA_REPORTED_SERVING
 *   (raw basis, not safely scalable).
 * - If mixed bases are provided and cannot be converted safely to the target basis, rows that require
 *   unsafe conversion are dropped (e.g., grams↔mL without density).
 *
 * ## Pitfalls / gotchas
 * - **Single grounding per food:** Do not auto-lock both mass and volume bridges. If mass grounding exists,
 *   volume grounding must not be inferred (and vice versa) to avoid ambiguity.
 * - **No density guessing:** Never convert between grams and mL without an explicit density field.
 * - **Truth-first bridges:** If a bridge field exists (gramsPerServingUnit / mlPerServingUnit), it is
 *   authoritative for “per serving” computations even if the unit is convertible.
 * - **Single-basis persistence:** Downstream logic assumes exactly one persisted row per nutrient id
 *   (within one basis). Do not allow multi-basis duplicates to slip into storage.
 *
 * ## Architectural rules
 * - Domain-layer correctness gate for persistence: grounding + canonicalization happens here.
 * - No UI state, no navigation, no side effects beyond repository writes.
 * - Repository contracts are the only persistence boundary (no direct DB/Room usage here).
 */
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
 *   - If `gramsPerServingUnit` is present (> 0), it is authoritative for gram grounding computations.
 *   - If `mlPerServingUnit` is present (> 0), it is authoritative for volume grounding computations.
 * - No grams↔mL conversion without explicit density (never guess density).
 * - “Single grounding per food” invariant:
 *   - If mass grounding is present (mass unit OR gramsPerServingUnit != null), do NOT auto-lock volume grounding.
 *   - If volume grounding is present (volume unit OR mlPerServingUnit != null), do NOT infer grams.
 *
 * ## Do not refactor notes
 * - Do not rework canonicalization to store multiple bases “for convenience”; downstream assumes one basis.
 * - Do not change the drop behavior in `mapNotNull` that rejects unsafe conversions between mass and volume.
 * - Keep the deterministic dedupe rule (prefer already-target-basis row, else first converted row).
 * - Keep “lock-in” behavior limited to deterministic unit conversions only; do not add heuristics.
 *
 * ## Architectural boundaries
 * - This use case owns persistence correctness rules for saving foods + nutrient rows.
 * - Repositories are the only persistence boundary; no direct DB/Room code may be added here.
 * - This use case must not join nutrients back to foods/recipes or query other tables during save.
 *
 * ## Migration notes (KMP / time APIs)
 * - No time APIs involved.
 *
 * ## Performance considerations
 * - Canonicalization is O(N) over nutrient rows and performed on each save. This is acceptable for the
 *   typical number of nutrients per food. Avoid adding additional passes unless required.
 */