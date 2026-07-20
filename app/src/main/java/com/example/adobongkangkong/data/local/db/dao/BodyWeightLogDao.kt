package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.adobongkangkong.data.local.db.entity.BodyWeightLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for persisted daily body-weight trend logs.
 *
 * This table intentionally remains one row per dateIso. Multiple same-day scale
 * readings belong in BodyWeightMeasurementDao / body_weight_measurements. The
 * row stored here is the official daily trend value used by existing dashboard,
 * reminder, and trend logic.
 *
 * MVP rule:
 * - One body-weight trend log per dateIso.
 * - Repository/use cases should upsert by dateIso rather than creating duplicate
 *   trend rows for the same day.
 *
 * Future multi-read support:
 * - selectedMeasurementId may point to the raw measurement chosen for the day.
 * - trendSelectionMethod records how the daily trend value was selected.
 * - isTrendUserOverride protects an explicit user choice from automatic
 *   re-selection.
 *
 * Date semantics:
 * - dateIso is yyyy-MM-dd.
 * - Range queries are ISO-date string ranges, matching the existing AK pattern
 *   for day data.
 */
@Dao
interface BodyWeightLogDao {

    @Insert
    suspend fun insert(entity: BodyWeightLogEntity): Long

    @Update
    suspend fun update(entity: BodyWeightLogEntity): Int

    @Delete
    suspend fun delete(entity: BodyWeightLogEntity)

    @Query(
        """
        SELECT *
        FROM body_weight_logs
        WHERE id = :id
        LIMIT 1
        """
    )
    suspend fun getById(id: Long): BodyWeightLogEntity?

    @Query(
        """
        SELECT *
        FROM body_weight_logs
        WHERE dateIso = :dateIso
        LIMIT 1
        """
    )
    suspend fun getByDate(dateIso: String): BodyWeightLogEntity?

    @Query(
        """
        SELECT *
        FROM body_weight_logs
        WHERE selectedMeasurementId = :measurementId
        LIMIT 1
        """
    )
    suspend fun getBySelectedMeasurementId(measurementId: Long): BodyWeightLogEntity?

    @Query(
        """
        SELECT *
        FROM body_weight_logs
        WHERE dateIso = :dateIso
        LIMIT 1
        """
    )
    fun observeByDate(dateIso: String): Flow<BodyWeightLogEntity?>

    @Query(
        """
        SELECT *
        FROM body_weight_logs
        ORDER BY dateIso DESC
        LIMIT 1
        """
    )
    fun observeLatest(): Flow<BodyWeightLogEntity?>

    @Query(
        """
        SELECT *
        FROM body_weight_logs
        ORDER BY dateIso DESC
        LIMIT 1
        """
    )
    suspend fun getLatest(): BodyWeightLogEntity?

    @Query(
        """
        SELECT *
        FROM body_weight_logs
        WHERE dateIso BETWEEN :startDateIsoInclusive AND :endDateIsoInclusive
        ORDER BY dateIso ASC
        """
    )
    fun observeRange(
        startDateIsoInclusive: String,
        endDateIsoInclusive: String
    ): Flow<List<BodyWeightLogEntity>>

    @Query(
        """
        SELECT *
        FROM body_weight_logs
        ORDER BY dateIso DESC
        LIMIT :limit
        """
    )
    fun observeRecent(limit: Int = 30): Flow<List<BodyWeightLogEntity>>

    @Query(
        """
        DELETE FROM body_weight_logs
        WHERE id = :id
        """
    )
    suspend fun deleteById(id: Long)

    @Query(
        """
        DELETE FROM body_weight_logs
        WHERE dateIso = :dateIso
        """
    )
    suspend fun deleteByDate(dateIso: String)

    /**
     * Upserts one daily trend row while preserving the original row id and
     * createdAtEpochMs when the date already exists.
     *
     * Callers may pass selectedMeasurementId/trendSelectionMethod when a raw
     * measurement is selected as the daily trend value. Passing null keeps the
     * row migration-friendly for manual or legacy entries.
     */
    @Transaction
    suspend fun upsertByDate(entity: BodyWeightLogEntity): Long {
        val existing = getByDate(entity.dateIso)

        if (existing == null) {
            return insert(entity)
        }

        update(
            entity.copy(
                id = existing.id,
                createdAtEpochMs = existing.createdAtEpochMs
            )
        )

        return existing.id
    }
}
