package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesSlotRuleEntity

interface PlannedSeriesRepository {

    // ----- Series -----

    suspend fun insertSeries(entity: PlannedSeriesEntity): Long
    suspend fun updateSeries(entity: PlannedSeriesEntity)
    suspend fun deleteSeries(entity: PlannedSeriesEntity)
    suspend fun getSeriesById(id: Long): PlannedSeriesEntity?

    // ----- Slot rules -----

    suspend fun getSlotRulesForSeries(seriesId: Long): List<PlannedSeriesSlotRuleEntity>

    /**
     * Replace-all semantics (simple + efficient for Phase 1).
     * Implementation: delete existing rules for seriesId, then insert new.
     */
    suspend fun replaceSlotRules(seriesId: Long, rules: List<PlannedSeriesSlotRuleEntity>)

    suspend fun getSeriesOverlappingRange(startDateIso: String, endDateIso: String): List<PlannedSeriesEntity>
}