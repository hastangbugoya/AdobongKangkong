package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.adobongkangkong.data.local.db.entity.FoodStorePriceEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for food-store pricing.
 *
 * Current model:
 * - One row per (foodId, storeId)
 * - Represents the current best-known price for that food at that store
 *
 * Design note:
 * - AVG queries are intentionally exposed now even though the current schema makes
 *   some of them trivial. This preserves a stable read contract if multi-row or
 *   historical pricing is added later.
 */
@Dao
interface FoodStorePriceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFoodStorePrice(entity: FoodStorePriceEntity): Long

    @Update
    suspend fun updateFoodStorePrice(entity: FoodStorePriceEntity)

    @Query(
        """
        DELETE FROM food_store_prices
        WHERE foodId = :foodId
          AND storeId = :storeId
        """
    )
    suspend fun deleteByFoodIdAndStoreId(
        foodId: Long,
        storeId: Long
    )

    @Query(
        """
        SELECT *
        FROM food_store_prices
        WHERE foodId = :foodId
          AND storeId = :storeId
        LIMIT 1
        """
    )
    suspend fun getByFoodIdAndStoreId(
        foodId: Long,
        storeId: Long
    ): FoodStorePriceEntity?

    /**
     * Returns the average price of a food across all stores.
     *
     * Today:
     * - One row per store, so this is the average of current known store prices.
     */
    @Query(
        """
        SELECT AVG(estimatedPrice)
        FROM food_store_prices
        WHERE foodId = :foodId
        """
    )
    suspend fun getAveragePriceForFood(
        foodId: Long
    ): Double?

    /**
     * Observable version of average price across all stores for a food.
     */
    @Query(
        """
        SELECT AVG(estimatedPrice)
        FROM food_store_prices
        WHERE foodId = :foodId
        """
    )
    fun observeAveragePriceForFood(
        foodId: Long
    ): Flow<Double?>

    /**
     * Returns the average price for a specific food-store pair.
     *
     * With the current unique (foodId, storeId) constraint, this is effectively
     * the current stored price for that pair.
     */
    @Query(
        """
        SELECT AVG(estimatedPrice)
        FROM food_store_prices
        WHERE foodId = :foodId
          AND storeId = :storeId
        """
    )
    suspend fun getAveragePriceForFoodAtStore(
        foodId: Long,
        storeId: Long
    ): Double?

    /**
     * Observable version of the current food-store pair price.
     */
    @Query(
        """
        SELECT AVG(estimatedPrice)
        FROM food_store_prices
        WHERE foodId = :foodId
          AND storeId = :storeId
        """
    )
    fun observeAveragePriceForFoodAtStore(
        foodId: Long,
        storeId: Long
    ): Flow<Double?>
}

/**
 * =============================================================================
 * FUTURE-YOU / FUTURE AI NOTES — DO NOT DELETE
 * =============================================================================
 *
 * Why AVG methods exist now
 * - They preserve a stable DAO contract for future history/multi-row pricing.
 *
 * Current semantics
 * - One row per (foodId, storeId)
 * - One average across all stores for a food
 *
 * Future likely additions
 * - min/max price by food
 * - cheapest store for a food
 * - joins with StoreEntity for shopping/list summaries
 *
 * Guardrail
 * - If this table later becomes price history, review whether writes should still
 *   replace or should append.
 */