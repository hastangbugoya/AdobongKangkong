package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.adobongkangkong.data.local.db.entity.RecipeBatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeBatchDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(batch: RecipeBatchEntity): Long

    @Query("SELECT * FROM recipe_batches WHERE recipeId = :recipeId ORDER BY createdAt DESC")
    fun observeBatchesForRecipe(recipeId: Long): Flow<List<RecipeBatchEntity>>

    @Query("SELECT * FROM recipe_batches WHERE id = :batchId")
    suspend fun getById(batchId: Long): RecipeBatchEntity?

    @Query("SELECT * FROM recipe_batches WHERE id IN (:batchIds)")
    suspend fun getByIds(batchIds: List<Long>): List<RecipeBatchEntity>


    // -------------------------
    // Dependency counts
    // -------------------------

    @Query("SELECT COUNT(*) FROM recipe_batches WHERE batchFoodId = :foodId")
    suspend fun countByBatchFoodId(foodId: Long): Int
}
