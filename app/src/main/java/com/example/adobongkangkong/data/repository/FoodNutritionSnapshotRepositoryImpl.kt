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
            gramsPerServingUnit = food.gramsPerServingUnit,
            rows = rows,
            nutrientCodeById = codeById
        )
    }

    suspend fun debugListNutrientCodes(): List<String> =
        db.nutrientDao().getIdCodePairs().map { it.code }

    override suspend fun getSnapshots(
        foodIds: Set<Long>
    ): Map<Long, FoodNutritionSnapshot> {
        if (foodIds.isEmpty()) return emptyMap()

        return foodIds
            .mapNotNull { foodId ->
                val snapshot = getSnapshot(foodId)
                snapshot?.let { foodId to it }
            }
            .toMap()
    }

    private suspend fun nutrientCodeById(): Map<Long, String> {
        // Simple + correct. If you want, later cache this in-memory since nutrients are basically static.
        return db.nutrientDao().getIdCodePairs().associate { it.id to it.code }
    }
}
