package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.adobongkangkong.data.local.db.entity.LogEntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface LogEntryDao {

    @Insert
    suspend fun insert(entry: LogEntryEntity): Long

    @Query("""
        SELECT * FROM log_entries
        WHERE timestamp >= :startInclusive AND timestamp < :endExclusive
        ORDER BY timestamp DESC
    """)
    fun observeRange(startInclusive: Instant, endExclusive: Instant): Flow<List<LogEntryEntity>>

    @Query("DELETE FROM log_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
