package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesItemEntity
import kotlinx.coroutines.flow.Flow

interface PlannedSeriesItemRepository {

    fun observeForSeries(seriesId: Long): Flow<List<PlannedSeriesItemEntity>>

    suspend fun getForSeries(seriesId: Long): List<PlannedSeriesItemEntity>

    suspend fun insert(entity: PlannedSeriesItemEntity): Long

    suspend fun deleteForSeries(seriesId: Long)

    suspend fun deleteById(id: Long)
}