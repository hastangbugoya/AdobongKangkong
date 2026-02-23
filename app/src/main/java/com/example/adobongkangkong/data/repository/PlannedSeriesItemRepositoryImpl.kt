package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.PlannedSeriesItemDao
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesItemEntity
import com.example.adobongkangkong.domain.repository.PlannedSeriesItemRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class PlannedSeriesItemRepositoryImpl @Inject constructor(
    private val dao: PlannedSeriesItemDao
) : PlannedSeriesItemRepository {

    override fun observeForSeries(seriesId: Long): Flow<List<PlannedSeriesItemEntity>> =
        dao.observeForSeries(seriesId)

    override suspend fun getForSeries(seriesId: Long): List<PlannedSeriesItemEntity> =
        dao.getForSeries(seriesId)

    override suspend fun insert(entity: PlannedSeriesItemEntity): Long =
        dao.insert(entity)

    override suspend fun deleteForSeries(seriesId: Long) =
        dao.deleteForSeries(seriesId)

    override suspend fun deleteById(id: Long) =
        dao.deleteById(id)
}