package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.dao.FoodNutrientDao
import com.example.adobongkangkong.data.local.db.mapper.toFoodNutritionSnapshot
import com.example.adobongkangkong.domain.model.isMassUnit
import com.example.adobongkangkong.domain.model.isVolumeUnit
import com.example.adobongkangkong.domain.model.toGrams
import com.example.adobongkangkong.domain.model.toMilliliters
import com.example.adobongkangkong.domain.recipes.FoodNutritionSnapshot
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import javax.inject.Inject

/**
 * Room-backed implementation of [FoodNutritionSnapshotRepository].
 *
 * Builds per-1-unit normalized snapshots:
 * - PER_100G  -> per gram
 * - PER_100ML -> per mL
 *
 * Important:
 * - For deterministic mass units (for example G, KG, OZ, LB), snapshot creation should behave as if
 *   the food has an effective gramsPerServingUnit of "grams per 1 servingUnit", even when the
 *   persisted Food leaves gramsPerServingUnit null.
 * - Likewise for deterministic volume units and mlPerServingUnit.
 *
 * This keeps logging / scaling correct for foods whose serving unit itself already carries the
 * physical grounding.
 */
class FoodNutritionSnapshotRepositoryImpl @Inject constructor(
    private val db: NutriDatabase,
    private val foodNutrientDao: FoodNutrientDao
) : FoodNutritionSnapshotRepository {

    override suspend fun getSnapshot(foodId: Long): FoodNutritionSnapshot? {
        val food = db.foodDao().getById(foodId) ?: return null
        val rows = db.foodNutrientDao().getForFood(foodId)
        if (rows.isEmpty()) return null

        val effectiveGramsPerServingUnit =
            food.gramsPerServingUnit?.takeIf { it > 0.0 }
                ?: if (food.servingUnit.isMassUnit()) {
                    food.servingUnit.toGrams(1.0)?.takeIf { it > 0.0 }
                } else {
                    null
                }

        val effectiveMlPerServingUnit =
            food.mlPerServingUnit?.takeIf { it > 0.0 }
                ?: if (
                    !food.servingUnit.isMassUnit() &&
                    food.servingUnit.isVolumeUnit()
                ) {
                    food.servingUnit.toMilliliters(1.0)?.takeIf { it > 0.0 }
                } else {
                    null
                }

        val codeById = nutrientCodeById()
        return toFoodNutritionSnapshot(
            foodId = food.id,
            gramsPerServingUnit = effectiveGramsPerServingUnit,
            mlPerServingUnit = effectiveMlPerServingUnit,
            rows = rows,
            nutrientCodeById = codeById
        )
    }

    override suspend fun getSnapshots(foodIds: Set<Long>): Map<Long, FoodNutritionSnapshot> {
        if (foodIds.isEmpty()) return emptyMap()

        return foodIds
            .mapNotNull { id ->
                val snapshot = getSnapshot(id)
                snapshot?.let { id to it }
            }
            .toMap()
    }

    private suspend fun nutrientCodeById(): Map<Long, String> {
        return db.nutrientDao().getIdCodePairs().associate { it.id to it.code }
    }
}

/**
 * AI NOTE — READ BEFORE REFACTORING (2026-02-06)
 *
 * I will be tempted to "optimize" getSnapshots by calling bulk DAO methods.
 * If I do, I must be careful: many DAOs in this project take List<Long>, not Set<Long>.
 * Convert explicitly (foodIds.toList()) and keep ordering/uniqueness in mind.
 *
 * Do NOT use a non-existent helper like getNutrientCodeMap().
 * The real source of nutrient id->code is nutrientDao().getIdCodePairs().
 *
 * 2026-03-17 note:
 * - Snapshot creation must honor deterministic unit grounding from ServingUnit itself.
 * - Example: servingUnit = G with servingSize = 50 means the food is mass-grounded even if
 *   gramsPerServingUnit is null.
 * - Do not require gramsPerServingUnit to be physically persisted for mass units whose unit
 *   already provides deterministic gram conversion.
 */