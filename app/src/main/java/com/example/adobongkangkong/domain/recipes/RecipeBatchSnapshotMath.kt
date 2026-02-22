package com.example.adobongkangkong.domain.recipes

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import com.example.adobongkangkong.domain.nutrition.dividedBy

/**
 * Pure math for converting recipe TOTAL nutrients into a cooked-batch snapshot representation.
 *
 * Invariants:
 * - Recipe totals do NOT change when you choose a different cooked yield weight.
 * - Cooked yield only changes "density" (nutrients per gram / per 100g).
 * - If the user provides servingsYieldUsed (number of servings in the cooked batch),
 *   we can compute gramsPerServingCooked = cookedYieldGrams / servingsYieldUsed.
 *
 * Snapshot outputs used by persistence/UI:
 * - per100g: nutrient amounts per 100g of cooked batch.
 * - gramsPerServingCooked: grams per serving for the cooked batch (optional).
 */
object RecipeBatchSnapshotMath {

    data class Result(
        val per100g: NutrientMap,
        val gramsPerServingCooked: Double?
    )

    /**
     * @param totals Total nutrients for the entire recipe (the whole pot/pan).
     * @param cookedYieldGrams Final cooked batch weight in grams (measured).
     * @param servingsYieldUsed Optional number of servings produced in this cooked batch.
     */
    fun compute(
        totals: NutrientMap,
        cookedYieldGrams: Double,
        servingsYieldUsed: Double?
    ): Result {
        require(cookedYieldGrams > 0.0) { "cookedYieldGrams must be > 0" }

        // totals / cookedYieldGrams = per-gram.
        // per100g = per-gram * 100.
        val per100g = totals
            .dividedBy(cookedYieldGrams)
            .scaledBy(100.0)

        val gramsPerServingCooked = when {
            servingsYieldUsed == null -> null
            servingsYieldUsed <= 0.0 -> null
            else -> cookedYieldGrams / servingsYieldUsed
        }

        return Result(
            per100g = per100g,
            gramsPerServingCooked = gramsPerServingCooked
        )
    }
}