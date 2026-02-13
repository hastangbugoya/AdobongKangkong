package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannedItemDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PlannedItemEntity): Long

    @Update
    suspend fun update(entity: PlannedItemEntity)

    @Delete
    suspend fun delete(entity: PlannedItemEntity)

    @Query("SELECT * FROM planned_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PlannedItemEntity?

    @Query("""
        SELECT *
        FROM planned_items
        WHERE mealId = :mealId
        ORDER BY sortOrder ASC, id ASC
    """)
    fun observeItemsForMeal(mealId: Long): Flow<List<PlannedItemEntity>>

    @Query("""
        SELECT *
        FROM planned_items
        WHERE mealId = :mealId
        ORDER BY sortOrder ASC, id ASC
    """)
    suspend fun getItemsForMeal(mealId: Long): List<PlannedItemEntity>

    @Query("DELETE FROM planned_items WHERE mealId = :mealId")
    suspend fun deleteItemsForMeal(mealId: Long)

    @Query("DELETE FROM planned_items WHERE id = :itemId")
    suspend fun deleteItemById(itemId: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM planned_items WHERE mealId = :mealId")
    suspend fun getMaxSortOrderForMeal(mealId: Long): Int

    // -------------------------
    // Dependency counts
    // -------------------------

    @Query("SELECT COUNT(*) FROM planned_items WHERE type = :type AND refId = :refId")
    suspend fun countByTypeAndRefId(type: PlannedItemSource, refId: Long): Int
}
