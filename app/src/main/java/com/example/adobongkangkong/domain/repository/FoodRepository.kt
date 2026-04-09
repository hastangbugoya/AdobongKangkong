package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.domain.logging.model.FoodRef
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodHardDeleteBlockers
import kotlinx.coroutines.flow.Flow

interface FoodRepository {
    fun search(query: String, limit: Int = 50): Flow<List<Food>>

    suspend fun getById(id: Long): Food?

    suspend fun upsert(food: Food): Long

    suspend fun getFoodRefForLogging(foodId: Long): FoodRef.Food?

    suspend fun isFoodsEmpty(): Boolean

    /**
     * Deletes a food and all food-owned data (nutrients, goal flags).
     *
     * @return true if deleted, false if blocked (e.g., food is referenced by a recipe).
     *
     * NOTE (soft delete refactor):
     * - This now performs a SOFT delete (default delete behavior).
     * - Hard delete is available via [hardDeleteFoodIfUnused].
     */
    suspend fun deleteFood(foodId: Long): Boolean

    // -------------------------
    // New: delete APIs
    // -------------------------

    suspend fun softDeleteFood(foodId: Long)

    suspend fun getFoodHardDeleteBlockers(foodId: Long): FoodHardDeleteBlockers

    suspend fun hardDeleteFood(foodId: Long)

    suspend fun cleanupOrphanFoodMedia(): Int

    suspend fun getByStableId(stableId: String): Food?

    // -------------------------
    // New: store pricing APIs
    // -------------------------

    /**
     * Inserts or replaces the current best-known price for a food at a store.
     *
     * Current contract:
     * - One active price row per (foodId, storeId)
     * - Re-saving the same pair replaces the old value
     */
    suspend fun upsertFoodStorePrice(
        foodId: Long,
        storeId: Long,
        estimatedPrice: Double
    ): Long

    /**
     * Removes the current price row for a food-store pair.
     */
    suspend fun deleteFoodStorePrice(
        foodId: Long,
        storeId: Long
    )

    /**
     * Returns the average price for a food across all stores.
     *
     * Today:
     * - One row per store, so this is the mean of current known store prices.
     *
     * Later:
     * - If price history is added, this method can remain while repository logic
     *   decides whether to average all rows or latest-per-store rows.
     */
    suspend fun getAveragePriceForFood(foodId: Long): Double?

    /**
     * Observable version of average price for a food across all stores.
     */
    fun observeAveragePriceForFood(foodId: Long): Flow<Double?>

    /**
     * Returns the current price for a specific food-store pair.
     *
     * Because the table currently enforces uniqueness on (foodId, storeId),
     * this is effectively the current stored value for that relationship.
     */
    suspend fun getAveragePriceForFoodAtStore(
        foodId: Long,
        storeId: Long
    ): Double?

    /**
     * Observable version of the current price for a specific food-store pair.
     *
     * Named "average" intentionally to preserve a stable contract if multi-row
     * or historical pricing is introduced later.
     */
    fun observeAveragePriceForFoodAtStore(
        foodId: Long,
        storeId: Long
    ): Flow<Double?>
}