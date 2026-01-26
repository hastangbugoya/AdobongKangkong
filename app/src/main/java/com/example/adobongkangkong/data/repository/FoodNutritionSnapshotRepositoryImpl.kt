package com.example.adobongkangkong.data.repository


import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.mapper.toFoodNutritionSnapshot
import com.example.adobongkangkong.domain.recipes.FoodNutritionSnapshot
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import javax.inject.Inject

/**
 * Room-backed implementation of [FoodNutritionSnapshotRepository].
 *
 * Normalizes stored (amount + basisType) rows into domain per-gram nutrient maps.
 */
class FoodNutritionSnapshotRepositoryImpl @Inject constructor(
    private val db: NutriDatabase
) : FoodNutritionSnapshotRepository {

    override suspend fun getSnapshot(foodId: Long): FoodNutritionSnapshot? {
        val food = db.foodDao().getById(foodId) ?: return null
        val rows = db.foodNutrientDao().getForFood(foodId)

        val codeById = nutrientCodeById()
        return toFoodNutritionSnapshot(
            foodId = food.id,
            gramsPerServing = food.gramsPerServing,
            rows = rows,
            nutrientCodeById = codeById
        )
    }

    suspend fun debugListNutrientCodes(): List<String> =
        db.nutrientDao().getIdCodePairs().map { it.code }

    override suspend fun getSnapshots(foodIds: Set<Long>): Map<Long, FoodNutritionSnapshot> {
        if (foodIds.isEmpty()) return emptyMap()

        val ids = foodIds.toList()
        val foods = db.foodDao().getByIds(ids)
        if (foods.isEmpty()) return emptyMap()

        val codeById = nutrientCodeById()

        val nutrientRows = db.foodNutrientDao(). getForFoods(foods.map { it.id })
        val rowsByFoodId = nutrientRows.groupBy { it.foodId }

        return foods.associate { food ->
            val rows = rowsByFoodId[food.id].orEmpty()
            val snapshot = toFoodNutritionSnapshot(
                foodId = food.id,
                gramsPerServing = food.gramsPerServing,
                rows = rows,
                nutrientCodeById = codeById
            )
            food.id to snapshot
        }
    }

    private suspend fun nutrientCodeById(): Map<Long, String> {
        // Simple + correct. If you want, later cache this in-memory since nutrients are basically static.
        return db.nutrientDao().getIdCodePairs().associate { it.id to it.code }
    }
}
