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
 * Builds per-serving-grounded snapshots for recipe nutrition:
 * - PER_100G              -> nutrients per gram
 * - PER_100ML             -> nutrients per mL
 * - USDA_REPORTED_SERVING -> nutrients per gram / per mL when serving bridges exist
 *
 * Important:
 * - The snapshot's gramsPerServingUnit / mlPerServingUnit fields are consumed by
 *   recipe computation as the physical amount for the CURRENT serving, not merely
 *   the amount for one raw unit.
 *
 * Example:
 * - servingSize = 10
 * - servingUnit = G
 *
 * The effective gram amount must be 10g, not 1g.
 *
 * Otherwise recipe tally math undercounts mass-unit foods by the serving size.
 */
class FoodNutritionSnapshotRepositoryImpl @Inject constructor(
    private val db: NutriDatabase,
    private val foodNutrientDao: FoodNutrientDao
) : FoodNutritionSnapshotRepository {

    override suspend fun getSnapshot(foodId: Long): FoodNutritionSnapshot? {
        val food = db.foodDao().getById(foodId) ?: return null
        val rows = foodNutrientDao.getForFood(foodId)
        if (rows.isEmpty()) return null

        val servingSize = food.servingSize.takeIf { it > 0.0 } ?: 1.0

        val effectiveGramsPerCurrentServing =
            when {
                food.servingUnit.isMassUnit() ->
                    food.servingUnit.toGrams(servingSize)?.takeIf { it > 0.0 }

                food.gramsPerServingUnit != null && food.gramsPerServingUnit > 0.0 ->
                    (servingSize * food.gramsPerServingUnit).takeIf { it > 0.0 }

                else -> null
            }

        val effectiveMlPerCurrentServing =
            when {
                !food.servingUnit.isMassUnit() && food.servingUnit.isVolumeUnit() ->
                    food.servingUnit.toMilliliters(servingSize)?.takeIf { it > 0.0 }

                food.mlPerServingUnit != null && food.mlPerServingUnit > 0.0 ->
                    (servingSize * food.mlPerServingUnit).takeIf { it > 0.0 }

                else -> null
            }

        val codeById = nutrientCodeById()

        return toFoodNutritionSnapshot(
            foodId = food.id,
            servingSize = food.servingSize,
            gramsPerServingUnit = effectiveGramsPerCurrentServing,
            mlPerServingUnit = effectiveMlPerCurrentServing,
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
 * AI NOTE — READ BEFORE REFACTORING
 *
 * Snapshot computation must provide the CURRENT serving's physical amount.
 *
 * Do NOT pass only "grams per one unit" for deterministic mass units.
 * Example:
 * - Food servingSize = 10
 * - Food servingUnit = G
 * - Effective recipe gram amount per serving must be 10g, not 1g.
 *
 * Recipe computation multiplies:
 * - ingredientServings * snapshot.gramsPerServingUnit
 *
 * Therefore snapshot.gramsPerServingUnit must mean:
 * - grams per current food serving
 *
 * Not:
 * - grams per one raw ServingUnit.
 */