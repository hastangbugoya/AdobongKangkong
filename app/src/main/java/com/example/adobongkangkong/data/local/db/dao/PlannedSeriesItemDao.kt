package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannedSeriesItemDao {

    @Query("""
        SELECT * FROM planned_series_items
        WHERE seriesId = :seriesId
        ORDER BY sortOrder ASC, id ASC
    """)
    fun observeForSeries(seriesId: Long): Flow<List<PlannedSeriesItemEntity>>

    @Query("""
        SELECT * FROM planned_series_items
        WHERE seriesId = :seriesId
        ORDER BY sortOrder ASC, id ASC
    """)
    suspend fun getForSeries(seriesId: Long): List<PlannedSeriesItemEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PlannedSeriesItemEntity): Long

    @Query("DELETE FROM planned_series_items WHERE seriesId = :seriesId")
    suspend fun deleteForSeries(seriesId: Long)

    @Query("DELETE FROM planned_series_items WHERE id = :id")
    suspend fun deleteById(id: Long)
}