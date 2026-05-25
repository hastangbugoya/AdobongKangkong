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
 * DAO for persisted body-weight logs.
 *
 * MVP rule:
 * - One body-weight log per dateIso.
 * - Repository/use cases should upsert by dateIso rather than creating duplicate rows
 *   for the same day.
 *
 * Date semantics:
 * - dateIso is yyyy-MM-dd.
 * - Range queries are ISO-date string ranges, matching the existing AK pattern for day data.
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
     * Upserts one body-weight row for a date while preserving the original row id
     * and createdAtEpochMs if the date already exists.
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