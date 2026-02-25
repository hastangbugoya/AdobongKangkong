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
        WHERE logDateIso = :logDateIso
        ORDER BY timestamp DESC
    """)
    fun observeDayByLogDateIso(logDateIso: String): Flow<List<LogEntryEntity>>

    @Query("""
        SELECT * FROM log_entries
        WHERE logDateIso >= :startDateIsoInclusive AND logDateIso <= :endDateIsoInclusive
        ORDER BY logDateIso DESC, timestamp DESC
    """)
    fun observeRangeByLogDateIso(
        startDateIsoInclusive: String,
        endDateIsoInclusive: String
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
        WHERE le.logDateIso = :dateIso
        ORDER BY le.timestamp DESC
    """)
    fun observeTodayLogRows(dateIso: String): Flow<List<TodayLogRow>>

    @Query("DELETE FROM log_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM log_entries ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM log_entries WHERE id = :id")
    suspend fun getById(id: Long): LogEntryEntity?

    // -------------------------
    // Dependency counts
    // -------------------------

    @Query("SELECT COUNT(*) FROM log_entries WHERE foodStableId = :stableId")
    suspend fun countByFoodStableId(stableId: String): Int
}