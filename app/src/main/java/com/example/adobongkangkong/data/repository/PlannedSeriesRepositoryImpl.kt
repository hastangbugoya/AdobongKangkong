package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.PlannedSeriesDao
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesSlotRuleEntity
import com.example.adobongkangkong.domain.repository.PlannedSeriesRepository
import javax.inject.Inject

class PlannedSeriesRepositoryImpl @Inject constructor(
    private val dao: PlannedSeriesDao
) : PlannedSeriesRepository {

    override suspend fun insertSeries(entity: PlannedSeriesEntity): Long =
        dao.insertSeries(entity)

    override suspend fun updateSeries(entity: PlannedSeriesEntity) =
        dao.updateSeries(entity)

    override suspend fun deleteSeries(entity: PlannedSeriesEntity) =
        dao.deleteSeries(entity)

    override suspend fun getSeriesById(id: Long): PlannedSeriesEntity? =
        dao.getSeriesById(id)

    override suspend fun getSlotRulesForSeries(seriesId: Long): List<PlannedSeriesSlotRuleEntity> =
        dao.getSlotRulesForSeries(seriesId)

    override suspend fun replaceSlotRules(seriesId: Long, rules: List<PlannedSeriesSlotRuleEntity>) {
        dao.replaceSlotRules(seriesId, rules)
    }

    override suspend fun getSeriesOverlappingRange(
        startDateIso: String,
        endDateIso: String
    ): List<PlannedSeriesEntity> =
        dao.getSeriesOverlappingRange(startDateIso, endDateIso)
}