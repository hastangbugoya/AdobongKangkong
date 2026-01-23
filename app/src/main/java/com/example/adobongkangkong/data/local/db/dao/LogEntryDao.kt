package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.adobongkangkong.data.local.db.entity.LogEntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Projection row for dashboard "Logged Today" list.
 */
data class TodayLogRow(
    val logId: Long,
    val timestamp: Instant,
    val foodId: Long,
    val foodName: String,
    val servings: Double,
    val caloriesKcal: Double?
)

@Dao
interface LogEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LogEntryEntity): Long

    @Query("""
        SELECT * FROM log_entries
        WHERE timestamp >= :startInclusive AND timestamp < :endExclusive
        ORDER BY timestamp DESC
    """)
    fun observeRange(
        startInclusive: Instant,
        endExclusive: Instant
    ): Flow<List<LogEntryEntity>>

    /**
     * Optimized projection for dashboard list.
     */
    @Query("""
        SELECT 
            le.id AS logId,
            le.timestamp AS timestamp,
            le.foodId AS foodId,
            f.name AS foodName,
            le.servings AS servings,
            (fn.nutrientAmountPerBasis * le.servings) AS caloriesKcal
        FROM log_entries le
        JOIN foods f ON f.id = le.foodId
        LEFT JOIN nutrients n ON n.code = 'CALORIES'
        LEFT JOIN food_nutrients fn 
            ON fn.foodId = le.foodId 
           AND fn.nutrientId = n.id
        WHERE le.timestamp >= :startInclusive 
          AND le.timestamp < :endExclusive
        ORDER BY le.timestamp DESC
    """)
    fun observeTodayLogRows(
        startInclusive: Instant,
        endExclusive: Instant
    ): Flow<List<TodayLogRow>>

    @Query("DELETE FROM log_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
