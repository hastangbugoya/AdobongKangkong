package com.example.adobongkangkong.data.local.db.mapper

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import com.example.adobongkangkong.domain.recipes.FoodNutritionSnapshot

/**
 * Normalize persisted food nutrient rows into a domain snapshot expressed per 1 gram and/or per 1 mL.
 *
 * Supported normalization:
 * - PER_100G              -> amount / 100 => nutrientsPerGram
 * - PER_100ML             -> amount / 100 => nutrientsPerMilliliter
 * - USDA_REPORTED_SERVING -> amount / (servingSize * bridge) => per-1-unit density map(s)
 *
 * IMPORTANT:
 * - Snapshot may expose mass and/or volume maps when the source data supports them explicitly.
 * - USDA_REPORTED_SERVING may normalize to:
 *   - nutrientsPerGram, if servingSize * gramsPerServingUnit is known
 *   - nutrientsPerMilliliter, if servingSize * mlPerServingUnit is known
 * - Never guess density here.
 * - If historical bad data contains PER_100G and PER_100ML rows, prefer PER_100G and ignore PER_100ML.
 */
fun toFoodNutritionSnapshot(
    foodId: Long,
    servingSize: Double,
    gramsPerServingUnit: Double?,
    mlPerServingUnit: Double?,
    rows: List<FoodNutrientEntity>,
    nutrientCodeById: Map<Long, String>
): FoodNutritionSnapshot {

    val hasPer100g = rows.any { it.basisType == BasisType.PER_100G }
    val hasPer100ml = rows.any { it.basisType == BasisType.PER_100ML }
    val hasUsdaReportedServing = rows.any { it.basisType == BasisType.USDA_REPORTED_SERVING }

    val perGram = mutableMapOf<NutrientKey, Double>()
    val perMilliliter = mutableMapOf<NutrientKey, Double>()

    // Historical canonical basis still wins if present.
    when {
        hasPer100g -> {
            for (row in rows) {
                if (row.basisType != BasisType.PER_100G) continue
                val code = nutrientCodeById[row.nutrientId] ?: continue
                perGram[NutrientKey(code)] = row.nutrientAmountPerBasis / 100.0
            }
        }

        hasPer100ml -> {
            for (row in rows) {
                if (row.basisType != BasisType.PER_100ML) continue
                val code = nutrientCodeById[row.nutrientId] ?: continue
                perMilliliter[NutrientKey(code)] = row.nutrientAmountPerBasis / 100.0
            }
        }

        hasUsdaReportedServing -> {
            val servingSizePositive = servingSize.takeIf { it > 0.0 }

            val gramsPerReportedServing =
                if (servingSizePositive != null && gramsPerServingUnit != null && gramsPerServingUnit > 0.0) {
                    servingSizePositive * gramsPerServingUnit
                } else {
                    null
                }

            val mlPerReportedServing =
                if (servingSizePositive != null && mlPerServingUnit != null && mlPerServingUnit > 0.0) {
                    servingSizePositive * mlPerServingUnit
                } else {
                    null
                }

            for (row in rows) {
                if (row.basisType != BasisType.USDA_REPORTED_SERVING) continue
                val code = nutrientCodeById[row.nutrientId] ?: continue
                val key = NutrientKey(code)

                if (gramsPerReportedServing != null) {
                    perGram[key] = row.nutrientAmountPerBasis / gramsPerReportedServing
                }

                if (mlPerReportedServing != null) {
                    perMilliliter[key] = row.nutrientAmountPerBasis / mlPerReportedServing
                }
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
 * AI NOTE — READ BEFORE REFACTORING
 *
 * Snapshot model in this project is per-1-unit density:
 * - nutrientsPerGram (mass)
 * - nutrientsPerMilliliter (volume)
 *
 * Do NOT reintroduce per100g/per100ml snapshot fields here.
 * PER_100G and PER_100ML rows normalize to per-1 (divide by 100).
 *
 * USDA_REPORTED_SERVING is valid snapshot input when explicit serving bridges exist.
 * Normalize it using:
 * - servingSize * gramsPerServingUnit, and/or
 * - servingSize * mlPerServingUnit
 *
 * Never attempt grams <-> mL conversion here.
 * Only emit maps that are explicitly grounded by persisted bridges.
 *
 * Additional invariant:
 * - If historical bad data contains both PER_100G and PER_100ML rows for the same food,
 *   prefer PER_100G and ignore PER_100ML to avoid ambiguous logging math.
 */