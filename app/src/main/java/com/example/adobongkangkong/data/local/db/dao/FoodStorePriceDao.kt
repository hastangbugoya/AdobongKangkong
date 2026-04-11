package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.adobongkangkong.data.local.db.entity.FoodStorePriceEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for food-store normalized pricing.
 *
 * Current model:
 * - One row per (foodId, storeId)
 * - Represents the current best-known normalized pricing snapshot for that food at that store
 * - Basis remains separate:
 *   - pricePer100g
 *   - pricePer100ml
 *
 * Design note:
 * - AVG queries are still exposed, but now they must stay basis-specific.
 * - Mass and volume prices must never be blended together.
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

    @Query(
        """
        SELECT AVG(pricePer100g)
        FROM food_store_prices
        WHERE foodId = :foodId
        """
    )
    suspend fun getAveragePricePer100gForFood(
        foodId: Long
    ): Double?

    @Query(
        """
        SELECT AVG(pricePer100g)
        FROM food_store_prices
        WHERE foodId = :foodId
        """
    )
    fun observeAveragePricePer100gForFood(
        foodId: Long
    ): Flow<Double?>

    @Query(
        """
        SELECT AVG(pricePer100ml)
        FROM food_store_prices
        WHERE foodId = :foodId
        """
    )
    suspend fun getAveragePricePer100mlForFood(
        foodId: Long
    ): Double?

    @Query(
        """
        SELECT AVG(pricePer100ml)
        FROM food_store_prices
        WHERE foodId = :foodId
        """
    )
    fun observeAveragePricePer100mlForFood(
        foodId: Long
    ): Flow<Double?>

    @Query(
        """
        SELECT AVG(pricePer100g)
        FROM food_store_prices
        WHERE foodId = :foodId
          AND storeId = :storeId
        """
    )
    suspend fun getAveragePricePer100gForFoodAtStore(
        foodId: Long,
        storeId: Long
    ): Double?

    @Query(
        """
        SELECT AVG(pricePer100g)
        FROM food_store_prices
        WHERE foodId = :foodId
          AND storeId = :storeId
        """
    )
    fun observeAveragePricePer100gForFoodAtStore(
        foodId: Long,
        storeId: Long
    ): Flow<Double?>

    @Query(
        """
        SELECT AVG(pricePer100ml)
        FROM food_store_prices
        WHERE foodId = :foodId
          AND storeId = :storeId
        """
    )
    suspend fun getAveragePricePer100mlForFoodAtStore(
        foodId: Long,
        storeId: Long
    ): Double?

    @Query(
        """
        SELECT AVG(pricePer100ml)
        FROM food_store_prices
        WHERE foodId = :foodId
          AND storeId = :storeId
        """
    )
    fun observeAveragePricePer100mlForFoodAtStore(
        foodId: Long,
        storeId: Long
    ): Flow<Double?>
}