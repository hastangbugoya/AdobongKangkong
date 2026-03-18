package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.data.local.db.entity.FoodBarcodeEntity

@Dao
interface FoodBarcodeDao {

    @Query("SELECT * FROM food_barcodes WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): FoodBarcodeEntity?

    @Query("SELECT foodId FROM food_barcodes WHERE barcode = :barcode LIMIT 1")
    suspend fun getFoodIdForBarcode(barcode: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FoodBarcodeEntity)

    @Query("DELETE FROM food_barcodes WHERE barcode = :barcode")
    suspend fun deleteByBarcode(barcode: String)

    @Query("UPDATE food_barcodes SET lastSeenAtEpochMs = :epochMs WHERE barcode = :barcode")
    suspend fun touchLastSeen(barcode: String, epochMs: Long)

    @Query("SELECT COUNT(*) FROM food_barcodes WHERE foodId = :foodId")
    suspend fun countForFood(foodId: Long): Int

    /**
     * Convenience: write the row while also ensuring the freshest observed timestamp is stored.
     *
     * Current behavior:
     * - Upserts a single active barcode row.
     * - Does not yet implement tombstoning/history.
     *
     * Future tombstone support:
     * - When barcode reassignment becomes tombstone-based, this helper should remain the single
     *   place where active barcode write semantics are centralized.
     */
    @Transaction
    suspend fun upsertAndTouch(entity: FoodBarcodeEntity, nowEpochMs: Long) {
        upsert(entity.copy(lastSeenAtEpochMs = nowEpochMs))
    }

    @Query(
        """
        SELECT * FROM food_barcodes
        WHERE source = :source
        ORDER BY barcode ASC
        """
    )
    suspend fun getAllBySource(source: BarcodeMappingSource): List<FoodBarcodeEntity>

    @Query(
        """
        SELECT * FROM food_barcodes
        WHERE foodId = :foodId
        ORDER BY barcode ASC
        """
    )
    suspend fun getAllForFood(foodId: Long): List<FoodBarcodeEntity>

    @Query(
        """
        UPDATE food_barcodes
        SET foodId = :toFoodId
        WHERE foodId = :fromFoodId
        """
    )
    suspend fun reassignAllToFood(
        fromFoodId: Long,
        toFoodId: Long
    )
}