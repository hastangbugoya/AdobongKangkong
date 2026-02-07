package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.dao.FoodNutrientDao
import com.example.adobongkangkong.data.local.db.mapper.toFoodNutritionSnapshot
import com.example.adobongkangkong.domain.recipes.FoodNutritionSnapshot
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import javax.inject.Inject

/**
 * Room-backed implementation of [FoodNutritionSnapshotRepository].
 *
 * Builds per-1-unit normalized snapshots:
 * - PER_100G  -> per gram
 * - PER_100ML -> per mL
 */
class FoodNutritionSnapshotRepositoryImpl @Inject constructor(
    private val db: NutriDatabase,
    private val foodNutrientDao: FoodNutrientDao
) : FoodNutritionSnapshotRepository {

    override suspend fun getSnapshot(foodId: Long): FoodNutritionSnapshot? {
        val food = db.foodDao().getById(foodId) ?: return null
        val rows = db.foodNutrientDao().getForFood(foodId)
        if (rows.isEmpty()) return null

        val codeById = nutrientCodeById()
        return toFoodNutritionSnapshot(
            foodId = food.id,
            gramsPerServingUnit = food.gramsPerServingUnit,
            mlPerServingUnit = food.mlPerServingUnit,
            rows = rows,
            nutrientCodeById = codeById
        )
    }

    override suspend fun getSnapshots(foodIds: Set<Long>): Map<Long, FoodNutritionSnapshot> {
        if (foodIds.isEmpty()) return emptyMap()

        // Keep this simple + correct: avoid Set/List DAO mismatches.
        return foodIds
            .mapNotNull { id ->
                val snapshot = getSnapshot(id)
                snapshot?.let { id to it }
            }
            .toMap()
    }

    private suspend fun nutrientCodeById(): Map<Long, String> {
        // Nutrients are essentially static; keep it simple & correct.
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
 */
