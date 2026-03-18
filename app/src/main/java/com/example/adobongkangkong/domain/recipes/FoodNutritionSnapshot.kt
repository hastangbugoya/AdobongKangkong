package com.example.adobongkangkong.domain.recipes

import com.example.adobongkangkong.domain.nutrition.NutrientMap

/**
 * Immutable, canonical nutrition snapshot for a food.
 *
 * Purpose
 * - Provide the minimal normalized nutrition data needed by logging, recipe math,
 *   previews, and other scaling flows.
 *
 * Canonical conventions
 * - Mass-grounded foods:
 *   - nutrientsPerGram expresses nutrient amounts for 1 gram of the food.
 * - Volume-grounded foods:
 *   - nutrientsPerMilliliter expresses nutrient amounts for 1 mL of the food.
 *
 * Bridges
 * - gramsPerServingUnit:
 *   grams in ONE servingUnit (not grams per serving unless servingSize == 1).
 * - mlPerServingUnit:
 *   milliliters in ONE servingUnit (not mL per serving unless servingSize == 1).
 *
 * Important invariants
 * - Never convert grams <-> mL here.
 * - Never guess density here.
 * - Snapshot scaling must use the matching density map only:
 *   - mass -> nutrientsPerGram
 *   - volume -> nutrientsPerMilliliter
 *
 * Import is intentionally lax
 * - Either or both density maps may be null.
 * - Callers decide whether missing nutrition is blocking.
 */
data class FoodNutritionSnapshot(
    val foodId: Long,
    val gramsPerServingUnit: Double?,
    val mlPerServingUnit: Double?,
    val nutrientsPerGram: NutrientMap?,
    val nutrientsPerMilliliter: NutrientMap?
)

/** Scales a mass-normalized snapshot by [grams]. Missing nutrients are treated as zero. */
fun FoodNutritionSnapshot.nutrientsForGrams(grams: Double): NutrientMap =
    (nutrientsPerGram ?: NutrientMap.EMPTY).scaledBy(grams)

/** Scales a volume-normalized snapshot by [milliliters]. Missing nutrients are treated as zero. */
fun FoodNutritionSnapshot.nutrientsForMilliliters(milliliters: Double): NutrientMap =
    (nutrientsPerMilliliter ?: NutrientMap.EMPTY).scaledBy(milliliters)

/**
 * FUTURE-YOU / FUTURE-AI NOTES
 *
 * - Keep this file small and boring.
 * - This is a snapshot container, not a conversion engine.
 * - Do not add grams<->mL conversion helpers here.
 * - Do not reintroduce old per100g/per100ml fields here.
 *   The normalized convention in this project is:
 *   - nutrientsPerGram
 *   - nutrientsPerMilliliter
 *
 * 2026-03 lock-in:
 * - gramsPerServingUnit and mlPerServingUnit mean "per 1 servingUnit".
 * - They do NOT mean "per full serving" unless servingSize == 1.
 */