package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.adobongkangkong.data.local.db.entity.PlannedMealEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannedMealDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PlannedMealEntity): Long

    @Update
    suspend fun update(entity: PlannedMealEntity)

    @Delete
    suspend fun delete(entity: PlannedMealEntity)

    @Query("SELECT * FROM planned_meals WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PlannedMealEntity?

    @Query("""
        SELECT *
        FROM planned_meals
        WHERE date = :date
        ORDER BY sortOrder ASC, id ASC
    """)
    fun observeMealsForDate(date: String): Flow<List<PlannedMealEntity>>

    @Query("""
        SELECT *
        FROM planned_meals
        WHERE date BETWEEN :startDate AND :endDate
        ORDER BY date ASC, sortOrder ASC, id ASC
    """)
    fun observeMealsInRange(startDate: String, endDate: String): Flow<List<PlannedMealEntity>>

    @Query("DELETE FROM planned_meals WHERE date = :date")
    suspend fun deleteMealsForDate(date: String)

    @Query("DELETE FROM planned_meals WHERE id = :mealId")
    suspend fun deleteMealById(mealId: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM planned_meals WHERE date = :date")
    suspend fun getMaxSortOrderForDate(date: String): Int

    @Query("""
        SELECT *
        FROM planned_meals
        WHERE seriesId = :seriesId
          AND date BETWEEN :startDate AND :endDate
        ORDER BY date ASC, sortOrder ASC, id ASC
    """)
    suspend fun getMealsForSeriesInRange(
        seriesId: Long,
        startDate: String,
        endDate: String
    ): List<PlannedMealEntity>
}
