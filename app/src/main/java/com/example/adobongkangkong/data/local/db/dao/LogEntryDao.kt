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
    val itemName: String,
    val nutrientsJson: String
)

@Dao
interface LogEntryDao {


    @Insert(onConflict = OnConflictStrategy.ABORT)
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
     *
     * NOTE: no joins. This stays valid even if foods/food_nutrients change or are deleted.
     */
    @Query("""
    SELECT
        le.id AS logId,
        le.timestamp AS timestamp,
        le.itemName AS itemName,
        le.nutrientsJson AS nutrientsJson
    FROM log_entries le
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

    @Query("SELECT * FROM log_entries ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM log_entries WHERE id = :id")
    suspend fun getById(id: Long): LogEntryEntity?
}