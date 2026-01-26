package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.domain.recipes.FoodNutritionSnapshot

/**
 * Provides nutrition snapshots normalized for domain math.
 *
 * Domain contract:
 * - Returned snapshots must be normalized to "per 1 gram" via [FoodNutritionSnapshot.nutrientsPerGram].
 * - Implementation may be lax (nulls allowed); correctness is enforced at point-of-use.
 */
interface FoodNutritionSnapshotRepository {

    /**
     * Returns a nutrition snapshot for a single food, or null if the food doesn't exist.
     */
    suspend fun getSnapshot(foodId: Long): FoodNutritionSnapshot?

    /**
     * Returns snapshots for many foods. Missing foods are omitted.
     *
     * Prefer this for recipe math to avoid N+1 queries.
     */
    suspend fun getSnapshots(foodIds: Set<Long>): Map<Long, FoodNutritionSnapshot>
}