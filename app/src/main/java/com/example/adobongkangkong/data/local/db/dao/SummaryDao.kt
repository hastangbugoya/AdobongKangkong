package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.adobongkangkong.data.local.db.entity.LogEntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class NutrientTotalRow(
    val nutrientCode: String,   // e.g. "CALORIES"
    val unit: String,           // "kcal", "g", "mg"
    val totalAmount: Double
)

@Dao
interface SummaryDao {

    @Query("""
  SELECT * FROM log_entries
  WHERE timestamp >= :startInclusive AND timestamp < :endExclusive
  ORDER BY timestamp DESC
""")
    fun observeRange(
        startInclusive: Instant,
        endExclusive: Instant
    ): Flow<List<LogEntryEntity>>
}
