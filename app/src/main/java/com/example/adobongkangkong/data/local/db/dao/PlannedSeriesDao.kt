package com.example.adobongkangkong.data.local.db.dao

import androidx.room.*
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesSlotRuleEntity

@Dao
interface PlannedSeriesDao {

    // ----- Series -----

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSeries(entity: PlannedSeriesEntity): Long

    @Update
    suspend fun updateSeries(entity: PlannedSeriesEntity)

    @Delete
    suspend fun deleteSeries(entity: PlannedSeriesEntity)

    @Query("SELECT * FROM planned_series WHERE id = :id LIMIT 1")
    suspend fun getSeriesById(id: Long): PlannedSeriesEntity?

    // ----- Slot rules -----

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSlotRule(entity: PlannedSeriesSlotRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSlotRules(entities: List<PlannedSeriesSlotRuleEntity>)

    @Query("SELECT * FROM planned_series_slot_rules WHERE seriesId = :seriesId ORDER BY weekday ASC, id ASC")
    suspend fun getSlotRulesForSeries(seriesId: Long): List<PlannedSeriesSlotRuleEntity>

    @Query("DELETE FROM planned_series_slot_rules WHERE seriesId = :seriesId")
    suspend fun deleteSlotRulesForSeries(seriesId: Long)

    @Transaction
    suspend fun replaceSlotRules(seriesId: Long, rules: List<PlannedSeriesSlotRuleEntity>) {
        deleteSlotRulesForSeries(seriesId)
        if (rules.isNotEmpty()) insertSlotRules(rules)
    }

    @Query("""
    SELECT *
    FROM planned_series
    WHERE effectiveStartDate <= :endDateIso
      AND (effectiveEndDate IS NULL OR effectiveEndDate >= :startDateIso)
    ORDER BY effectiveStartDate ASC, id ASC
    """)
        suspend fun getSeriesOverlappingRange(
            startDateIso: String,
            endDateIso: String
        ): List<PlannedSeriesEntity>
}