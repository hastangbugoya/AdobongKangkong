package com.example.adobongkangkong.data.local.db.mapper

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import com.example.adobongkangkong.domain.recipes.FoodNutritionSnapshot

/**
 * Normalize persisted food nutrient rows into a domain snapshot expressed per 1 gram OR per 1 mL.
 *
 * Supported normalization:
 * - PER_100G  -> amount / 100  => nutrientsPerGram
 * - PER_100ML -> amount / 100  => nutrientsPerMilliliters
 *
 * USDA_REPORTED_SERVING should be canonicalized earlier (before rows reach snapshot creation).
 * If it appears here, we intentionally ignore it rather than guessing a bridge.
 */
fun toFoodNutritionSnapshot(
    foodId: Long,
    gramsPerServingUnit: Double?,
    mlPerServingUnit: Double?,
    rows: List<FoodNutrientEntity>,
    nutrientCodeById: Map<Long, String>
): FoodNutritionSnapshot {

    val perGram = mutableMapOf<NutrientKey, Double>()
    val perMilliliter = mutableMapOf<NutrientKey, Double>()

    for (row in rows) {
        val code = nutrientCodeById[row.nutrientId] ?: continue
        val key = NutrientKey(code)

        when (row.basisType) {
            BasisType.PER_100G -> {
                perGram[key] = row.nutrientAmountPerBasis / 100.0
            }

            BasisType.PER_100ML -> {
                perMilliliter[key] = row.nutrientAmountPerBasis / 100.0
            }

            BasisType.USDA_REPORTED_SERVING -> {
                // Intentionally ignored: canonicalization must happen earlier.
            }
        }
    }

    return FoodNutritionSnapshot(
        foodId = foodId,
        gramsPerServingUnit = gramsPerServingUnit,
        mlPerServingUnit = mlPerServingUnit,
        nutrientsPerGram = perGram.takeIf { it.isNotEmpty() }?.let { NutrientMap(it) },
        nutrientsPerMilliliter = perMilliliter.takeIf { it.isNotEmpty() }?.let { NutrientMap(it) }
    )
}

/**
 * AI NOTE — READ BEFORE REFACTORING (2026-02-06)
 *
 * Snapshot model in this project is per-1-unit density:
 * - nutrientsPerGram (mass) and/or nutrientsPerMilliliters (volume)
 *
 * Do NOT reintroduce per100g/per100ml snapshot fields here.
 * PER_100G and PER_100ML rows normalize to per-1 (divide by 100).
 *
 * Never attempt grams <-> mL conversion here. Density is intentionally absent.
 * USDA_REPORTED_SERVING is ignored here on purpose: if it shows up, canonicalization upstream is broken.
 */
