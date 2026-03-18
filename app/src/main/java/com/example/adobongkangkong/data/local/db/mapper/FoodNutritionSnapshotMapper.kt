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
 * - PER_100ML -> amount / 100  => nutrientsPerMilliliter
 *
 * USDA_REPORTED_SERVING should be canonicalized earlier (before rows reach snapshot creation).
 * If it appears here, we intentionally ignore it rather than guessing a bridge.
 *
 * IMPORTANT:
 * - Snapshot must expose one coherent scaling system for a food:
 *   - mass-backed snapshot via nutrientsPerGram, or
 *   - volume-backed snapshot via nutrientsPerMilliliter.
 * - If bad historical data contains both PER_100G and PER_100ML rows, prefer mass and ignore volume.
 * - Never guess density here.
 */
fun toFoodNutritionSnapshot(
    foodId: Long,
    gramsPerServingUnit: Double?,
    mlPerServingUnit: Double?,
    rows: List<FoodNutrientEntity>,
    nutrientCodeById: Map<Long, String>
): FoodNutritionSnapshot {

    val hasPer100g = rows.any { it.basisType == BasisType.PER_100G }
    val hasPer100ml = rows.any { it.basisType == BasisType.PER_100ML }

    // Single-system rule for snapshot creation:
    // prefer mass if present; otherwise volume.
    val chosenBasis: BasisType? = when {
        hasPer100g -> BasisType.PER_100G
        hasPer100ml -> BasisType.PER_100ML
        else -> null
    }

    val perGram = mutableMapOf<NutrientKey, Double>()
    val perMilliliter = mutableMapOf<NutrientKey, Double>()

    for (row in rows) {
        val code = nutrientCodeById[row.nutrientId] ?: continue
        val key = NutrientKey(code)

        when (chosenBasis) {
            BasisType.PER_100G -> {
                if (row.basisType == BasisType.PER_100G) {
                    perGram[key] = row.nutrientAmountPerBasis / 100.0
                }
            }

            BasisType.PER_100ML -> {
                if (row.basisType == BasisType.PER_100ML) {
                    perMilliliter[key] = row.nutrientAmountPerBasis / 100.0
                }
            }

            null -> {
                // USDA_REPORTED_SERVING only (or no usable rows) -> intentionally no normalized snapshot maps.
            }

            BasisType.USDA_REPORTED_SERVING -> {
                // Not expected as chosenBasis; keep exhaustive when.
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
 * - nutrientsPerGram (mass) and/or nutrientsPerMilliliter (volume)
 *
 * Do NOT reintroduce per100g/per100ml snapshot fields here.
 * PER_100G and PER_100ML rows normalize to per-1 (divide by 100).
 *
 * Never attempt grams <-> mL conversion here. Density is intentionally absent.
 * USDA_REPORTED_SERVING is ignored here on purpose: if it shows up, canonicalization upstream is broken.
 *
 * Additional invariant:
 * - Snapshot should expose only one density system at a time.
 * - If historical bad data contains both PER_100G and PER_100ML rows for the same food,
 *   prefer PER_100G and ignore PER_100ML to avoid ambiguous logging math.
 */