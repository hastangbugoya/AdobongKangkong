package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.adobongkangkong.data.local.db.entity.BodyWeightMeasurementEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for raw body-weight measurements.
 *
 * This table can contain multiple readings for the same calendar date. It is
 * the import/history layer for manual scale readings and Health Connect weight
 * records. The existing body_weight_logs table remains the single official
 * daily trend value.
 *
 * MVP import behavior supported by these queries:
 * - Find the latest Health Connect weight.
 * - Skip a source record that was already imported.
 * - Detect near-duplicates from the same source in a short time window.
 * - Keep multiple same-day readings only when they are meaningfully separated,
 *   defaulting to a four-hour minimum gap in repository/use-case logic.
 */
@Dao
interface BodyWeightMeasurementDao {

    @Insert
    suspend fun insert(entity: BodyWeightMeasurementEntity): Long

    @Update
    suspend fun update(entity: BodyWeightMeasurementEntity): Int

    @Query(
        """
        SELECT *
        FROM body_weight_measurements
        WHERE id = :id
        AND isDeleted = 0
        LIMIT 1
        """
    )
    suspend fun getById(id: Long): BodyWeightMeasurementEntity?

    @Query(
        """
        SELECT *
        FROM body_weight_measurements
        WHERE dateIso = :dateIso
        AND isDeleted = 0
        ORDER BY measuredAtEpochMs ASC
        """
    )
    suspend fun getByDate(dateIso: String): List<BodyWeightMeasurementEntity>

    @Query(
        """
        SELECT *
        FROM body_weight_measurements
        WHERE dateIso = :dateIso
        AND isDeleted = 0
        ORDER BY measuredAtEpochMs ASC
        """
    )
    fun observeByDate(dateIso: String): Flow<List<BodyWeightMeasurementEntity>>

    @Query(
        """
        SELECT *
        FROM body_weight_measurements
        WHERE dateIso BETWEEN :startDateIsoInclusive AND :endDateIsoInclusive
        AND isDeleted = 0
        ORDER BY dateIso ASC, measuredAtEpochMs ASC
        """
    )
    fun observeRange(
        startDateIsoInclusive: String,
        endDateIsoInclusive: String
    ): Flow<List<BodyWeightMeasurementEntity>>

    @Query(
        """
        SELECT *
        FROM body_weight_measurements
        WHERE isDeleted = 0
        ORDER BY measuredAtEpochMs DESC
        LIMIT 1
        """
    )
    suspend fun getLatest(): BodyWeightMeasurementEntity?

    @Query(
        """
        SELECT *
        FROM body_weight_measurements
        WHERE isDeleted = 0
        AND measuredAtEpochMs > :afterEpochMsExclusive
        ORDER BY measuredAtEpochMs DESC
        LIMIT 1
        """
    )
    suspend fun getLatestAfter(afterEpochMsExclusive: Long): BodyWeightMeasurementEntity?

    @Query(
        """
        SELECT *
        FROM body_weight_measurements
        WHERE source = :source
        AND sourceRecordId = :sourceRecordId
        AND sourceRecordId IS NOT NULL
        AND isDeleted = 0
        LIMIT 1
        """
    )
    suspend fun getBySourceRecordId(
        source: String,
        sourceRecordId: String
    ): BodyWeightMeasurementEntity?

    @Query(
        """
        SELECT *
        FROM body_weight_measurements
        WHERE dateIso = :dateIso
        AND isDeleted = 0
        ORDER BY ABS(measuredAtEpochMs - :measuredAtEpochMs) ASC
        LIMIT 1
        """
    )
    suspend fun getNearestOnDate(
        dateIso: String,
        measuredAtEpochMs: Long
    ): BodyWeightMeasurementEntity?

    @Query(
        """
        SELECT *
        FROM body_weight_measurements
        WHERE dateIso = :dateIso
        AND source = :source
        AND (
            (:sourcePackage IS NULL AND sourcePackage IS NULL)
            OR sourcePackage = :sourcePackage
        )
        AND measuredAtEpochMs BETWEEN :startEpochMsInclusive AND :endEpochMsInclusive
        AND weightKg BETWEEN :minWeightKgInclusive AND :maxWeightKgInclusive
        AND isDeleted = 0
        ORDER BY ABS(measuredAtEpochMs - :measuredAtEpochMs) ASC
        LIMIT 1
        """
    )
    suspend fun findNearDuplicate(
        dateIso: String,
        source: String,
        sourcePackage: String?,
        measuredAtEpochMs: Long,
        startEpochMsInclusive: Long,
        endEpochMsInclusive: Long,
        minWeightKgInclusive: Double,
        maxWeightKgInclusive: Double
    ): BodyWeightMeasurementEntity?

    @Query(
        """
        SELECT COUNT(*)
        FROM body_weight_measurements
        WHERE dateIso = :dateIso
        AND isDeleted = 0
        """
    )
    suspend fun countByDate(dateIso: String): Int

    @Query(
        """
        UPDATE body_weight_measurements
        SET isDeleted = 1,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :id
        """
    )
    suspend fun softDeleteById(
        id: Long,
        updatedAtEpochMs: Long
    ): Int
}
