package com.example.adobongkangkong.domain.usecase.basisconversion

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodNutrientRow
import com.example.adobongkangkong.domain.model.isMassUnit
import com.example.adobongkangkong.domain.model.isVolumeUnit
import com.example.adobongkangkong.domain.model.toGrams
import com.example.adobongkangkong.domain.model.toMilliliters
import com.example.adobongkangkong.domain.nutrition.NutrientBasisScaler

/**
 * 2026-02-07 — FOR DEV (top KDoc)
 *
 * Purpose
 * - This is the “orchestrator” use case that lets you *switch* a food’s canonical nutrient basis.
 * - It first NORMALIZES any USDA rows (USDA_REPORTED_SERVING) into a canonical PER_100* basis.
 * - Then, if needed, it CONVERTS between PER_100G and PER_100ML using food-specific density.
 *
 * Why it exists
 * - USDA normalization is source-specific. Mass↔Volume conversion is physical math.
 * - Keeping them separate prevents “USDA quirks” from leaking into general conversions.
 *
 * Contracts
 * - Input: [food] (serving definition + bridges) and [rows] (may contain mixed BasisType).
 * - Output: fully-converted rows for the requested [targetBasis].
 * - This does NOT touch DB. It only returns transformed rows + optional food suggestion.
 *
 * Preconditions
 * - To normalize USDA_REPORTED_SERVING → PER_100G, we need grams-per-serving to be computable.
 * - To normalize USDA_REPORTED_SERVING → PER_100ML, we need ml-per-serving to be computable.
 * - To convert PER_100G ↔ PER_100ML, we need density, derived ONLY from (gramsPerServing / mlPerServing).
 *   (No guessing, no “assume water”.)
 */
class NormalizeAndConvertFoodNutrientBasisUseCase {

    enum class TargetBasis {
        PER_100G,
        PER_100ML
    }

    sealed class Result {
        data class Success(
            val convertedRows: List<FoodNutrientRow>,
            /**
             * Optional: if you decide you want the Food to be “volume-grounded” or “mass-grounded” after switching.
             * This use case does not mutate persistence; caller may ignore.
             */
            val suggestedFoodAdjustment: Food? = null
        ) : Result()

        data class Blocked(val reason: String) : Result()
    }

    operator fun invoke(
        food: Food,
        rows: List<FoodNutrientRow>,
        targetBasis: TargetBasis
    ): Result {
        // 1) Normalize any USDA-reported-per-serving rows to a canonical PER_100* basis.
        val normalized = when (targetBasis) {
            TargetBasis.PER_100G -> normalizeUsdaServingToPer100g(food, rows)
            TargetBasis.PER_100ML -> normalizeUsdaServingToPer100ml(food, rows)
        } ?: return Result.Blocked(
            when (targetBasis) {
                TargetBasis.PER_100G -> "Cannot normalize USDA_REPORTED_SERVING → PER_100G: missing grams-per-serving (need gramsPerServingUnit or mass serving unit)."
                TargetBasis.PER_100ML -> "Cannot normalize USDA_REPORTED_SERVING → PER_100ML: missing ml-per-serving (need mlPerServingUnit or volume serving unit)."
            }
        )

        // 2) If target is PER_100G, ensure PER_100ML rows (if any) are converted back (requires density).
        //    If target is PER_100ML, ensure PER_100G rows (if any) are converted forward (requires density).
        val final = when (targetBasis) {
            TargetBasis.PER_100G -> convertAnyPer100mlToPer100g(food, normalized)
            TargetBasis.PER_100ML -> convertAnyPer100gToPer100ml(food, normalized)
        } ?: return Result.Blocked(
            "Cannot convert PER_100G ↔ PER_100ML: need density derived from gramsPerServing/mlPerServing."
        )

        val suggestion = when (targetBasis) {
            TargetBasis.PER_100G -> suggestMassGrounding(food)
            TargetBasis.PER_100ML -> suggestVolumeGrounding(food)
        }

        return Result.Success(
            convertedRows = final,
            suggestedFoodAdjustment = suggestion
        )
    }

    // -----------------------------
    // USDA_REPORTED_SERVING → PER_100G / PER_100ML
    // -----------------------------

    /**
     * Returns null if USDA rows exist but scaling cannot be performed.
     */
    private fun normalizeUsdaServingToPer100g(food: Food, rows: List<FoodNutrientRow>): List<FoodNutrientRow>? {
        val gramsPerServing = computeGramsPerServing(food)

        val hasUsdaRows = rows.any { it.basisType == BasisType.USDA_REPORTED_SERVING }
        if (hasUsdaRows && gramsPerServing == null) return null

        return rows.map { r ->
            if (r.basisType != BasisType.USDA_REPORTED_SERVING) return@map r

            val scaled = NutrientBasisScaler.displayPerServingToCanonical(
                uiPerServingAmount = r.amount,
                canonicalBasis = BasisType.PER_100G,
                servingSize = food.servingSize,
                gramsPerServingUnit = food.gramsPerServingUnit
            )

            // If gramsPerServing exists, scaler must have scaled.
            if (!scaled.didScale) return null

            r.copy(
                amount = scaled.amount,
                basisType = BasisType.PER_100G,
                basisGrams = 100.0
            )
        }
    }

    /**
     * Returns null if USDA rows exist but scaling cannot be performed.
     */
    private fun normalizeUsdaServingToPer100ml(food: Food, rows: List<FoodNutrientRow>): List<FoodNutrientRow>? {
        val mlPerServing = computeMlPerServing(food)

        val hasUsdaRows = rows.any { it.basisType == BasisType.USDA_REPORTED_SERVING }
        if (hasUsdaRows && mlPerServing == null) return null

        return rows.map { r ->
            if (r.basisType != BasisType.USDA_REPORTED_SERVING) return@map r

            val scaled = NutrientBasisScaler.displayPerServingToCanonicalVolume(
                uiPerServingAmount = r.amount,
                canonicalBasis = BasisType.PER_100ML,
                servingSize = food.servingSize,
                mlPerServingUnit = food.mlPerServingUnit
            )

            if (!scaled.didScale) return null

            r.copy(
                amount = scaled.amount,
                basisType = BasisType.PER_100ML,
                basisGrams = 100.0
            )
        }
    }

    // -----------------------------
    // PER_100G ↔ PER_100ML conversion (density required)
    // -----------------------------

    /**
     * Converts any PER_100G rows to PER_100ML, leaves others unchanged.
     * Returns null if conversion is needed but density cannot be derived.
     */
    private fun convertAnyPer100gToPer100ml(food: Food, rows: List<FoodNutrientRow>): List<FoodNutrientRow>? {
        val needs = rows.any { it.basisType == BasisType.PER_100G }
        if (!needs) return rows

        val density = deriveDensityGPerMl(food) ?: return null

        // amountPer100ml = amountPer100g * density(g/ml)
        return rows.map { r ->
            if (r.basisType != BasisType.PER_100G) return@map r
            r.copy(
                amount = r.amount * density,
                basisType = BasisType.PER_100ML,
                basisGrams = 100.0
            )
        }
    }

    /**
     * Converts any PER_100ML rows to PER_100G, leaves others unchanged.
     * Returns null if conversion is needed but density cannot be derived.
     */
    private fun convertAnyPer100mlToPer100g(food: Food, rows: List<FoodNutrientRow>): List<FoodNutrientRow>? {
        val needs = rows.any { it.basisType == BasisType.PER_100ML }
        if (!needs) return rows

        val density = deriveDensityGPerMl(food) ?: return null
        if (density == 0.0) return null

        // amountPer100g = amountPer100ml / density(g/ml)
        return rows.map { r ->
            if (r.basisType != BasisType.PER_100ML) return@map r
            r.copy(
                amount = r.amount / density,
                basisType = BasisType.PER_100G,
                basisGrams = 100.0
            )
        }
    }

    // -----------------------------
    // Food math helpers (NO new extensions)
    // -----------------------------

    private fun computeGramsPerServing(food: Food): Double? {
        if (food.servingSize <= 0.0) return null

        // Prefer explicit bridge.
        val bridged = food.gramsPerServingUnit
        if (bridged != null && bridged > 0.0) return food.servingSize * bridged

        // Or derive if serving unit itself is mass.
        return if (food.servingUnit.isMassUnit()) {
            food.servingUnit.toGrams(food.servingSize)
        } else null
    }

    private fun computeMlPerServing(food: Food): Double? {
        if (food.servingSize <= 0.0) return null

        // Prefer explicit bridge.
        val bridged = food.mlPerServingUnit
        if (bridged != null && bridged > 0.0) return food.servingSize * bridged

        // Or derive if serving unit itself is volume.
        return if (food.servingUnit.isVolumeUnit()) {
            food.servingUnit.toMilliliters(food.servingSize)
        } else null
    }

    /**
     * Density derivation rules (no guessing):
     * - If both grams-per-serving and ml-per-serving are computable, density = g/ml.
     */
    private fun deriveDensityGPerMl(food: Food): Double? {
        val g = computeGramsPerServing(food) ?: return null
        val ml = computeMlPerServing(food) ?: return null
        if (ml <= 0.0) return null
        val d = g / ml
        if (d <= 0.0) return null
        return d
    }

    // -----------------------------
    // Optional “grounding” suggestions (caller may ignore)
    // -----------------------------

    private fun suggestVolumeGrounding(food: Food): Food? {
        // Only safe to suggest if the serving unit is already volume-like.
        if (!food.servingUnit.isVolumeUnit()) return null

        // If gramsPerServingUnit is present, your invariant treats this as mass-grounded.
        // Clearing it is a *suggestion* only (UI may keep it).
        if (food.gramsPerServingUnit == null) return null

        return food.copy(gramsPerServingUnit = null)
    }

    private fun suggestMassGrounding(food: Food): Food? {
        // Only safe to suggest if the serving unit is already mass-like.
        if (!food.servingUnit.isMassUnit()) return null

        // If mlPerServingUnit is present, clearing it reduces ambiguity.
        if (food.mlPerServingUnit == null) return null

        return food.copy(mlPerServingUnit = null)
    }
}

/**
 * 2026-02-07 — FOR AI (bottom KDoc)
 *
 * Implementation notes
 * - This file intentionally avoids new extension functions and uses only:
 *   - NutrientBasisScaler.displayPerServingToCanonical / Volume
 *   - ServingUnitExt: isMassUnit/isVolumeUnit/toGrams/toMilliliters
 * - Normalization runs first (USDA_REPORTED_SERVING → PER_100*). Conversion runs second (PER_100G ↔ PER_100ML).
 * - We keep basisGrams=100.0 for both PER_100G and PER_100ML to match existing app conventions.
 * - If mixed basis rows exist, we only convert the ones relevant to the requested target basis.
 */
