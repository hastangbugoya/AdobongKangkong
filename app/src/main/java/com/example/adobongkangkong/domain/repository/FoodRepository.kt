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
     * - Hard delete is available via [hardDeleteFood].
     */
    suspend fun deleteFood(foodId: Long): Boolean

    // -------------------------
    // Delete APIs
    // -------------------------

    suspend fun softDeleteFood(foodId: Long)

    suspend fun getFoodHardDeleteBlockers(foodId: Long): FoodHardDeleteBlockers

    suspend fun hardDeleteFood(foodId: Long)

    suspend fun cleanupOrphanFoodMedia(): Int

    suspend fun getByStableId(stableId: String): Food?

    // -------------------------
    // Store pricing APIs
    // -------------------------

    /**
     * Inserts or replaces the current best-known normalized price for a food at a store.
     *
     * Current contract:
     * - One active pricing row per (foodId, storeId)
     * - Re-saving the same pair replaces the old normalized snapshot
     * - At least one normalized path must be present
     * - Mass and volume remain separate
     */
    suspend fun upsertFoodStorePrice(
        foodId: Long,
        storeId: Long,
        pricePer100g: Double?,
        pricePer100ml: Double?,
        updatedAtEpochMs: Long
    ): Long

    /**
     * Removes the current price row for a food-store pair.
     */
    suspend fun deleteFoodStorePrice(
        foodId: Long,
        storeId: Long
    )

    /**
     * Returns the average normalized price per 100g for a food across all stores.
     *
     * Today:
     * - One row per store, so this is the mean of current known mass-based store estimates.
     * - Stores without a mass-based estimate are ignored.
     */
    suspend fun getAveragePricePer100gForFood(foodId: Long): Double?

    /**
     * Observable version of average normalized price per 100g for a food across all stores.
     */
    fun observeAveragePricePer100gForFood(foodId: Long): Flow<Double?>

    /**
     * Returns the average normalized price per 100mL for a food across all stores.
     *
     * Today:
     * - One row per store, so this is the mean of current known volume-based store estimates.
     * - Stores without a volume-based estimate are ignored.
     */
    suspend fun getAveragePricePer100mlForFood(foodId: Long): Double?

    /**
     * Observable version of average normalized price per 100mL for a food across all stores.
     */
    fun observeAveragePricePer100mlForFood(foodId: Long): Flow<Double?>

    /**
     * Returns the current normalized price per 100g for a specific food-store pair.
     *
     * Because the table currently enforces uniqueness on (foodId, storeId),
     * this is effectively the current stored mass-based value for that relationship.
     */
    suspend fun getAveragePricePer100gForFoodAtStore(
        foodId: Long,
        storeId: Long
    ): Double?

    /**
     * Observable version of the current normalized price per 100g for a specific food-store pair.
     */
    fun observeAveragePricePer100gForFoodAtStore(
        foodId: Long,
        storeId: Long
    ): Flow<Double?>

    /**
     * Returns the current normalized price per 100mL for a specific food-store pair.
     *
     * Because the table currently enforces uniqueness on (foodId, storeId),
     * this is effectively the current stored volume-based value for that relationship.
     */
    suspend fun getAveragePricePer100mlForFoodAtStore(
        foodId: Long,
        storeId: Long
    ): Double?

    /**
     * Observable version of the current normalized price per 100mL for a specific food-store pair.
     */
    fun observeAveragePricePer100mlForFoodAtStore(
        foodId: Long,
        storeId: Long
    ): Flow<Double?>
}