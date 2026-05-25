package com.example.adobongkangkong.data.weight

import com.example.adobongkangkong.data.local.db.dao.BodyWeightLogDao
import com.example.adobongkangkong.data.local.db.entity.BodyWeightLogEntity
import com.example.adobongkangkong.domain.weight.BodyWeightLog
import com.example.adobongkangkong.domain.weight.BodyWeightLogRepository
import com.example.adobongkangkong.domain.weight.BodyWeightUnit
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed implementation for body-weight logs.
 *
 * Data rules:
 * - One body-weight log per dateIso for MVP.
 * - Upsert preserves the original row id and createdAtEpochMs when updating a date.
 * - Reminder settings are intentionally not handled here.
 */
@Singleton
class BodyWeightLogRepositoryImpl @Inject constructor(
    private val dao: BodyWeightLogDao
) : BodyWeightLogRepository {

    override fun observeLatest(): Flow<BodyWeightLog?> =
        dao.observeLatest()
            .map { entity -> entity?.toDomain() }

    override fun observeByDate(date: LocalDate): Flow<BodyWeightLog?> =
        dao.observeByDate(date.toString())
            .map { entity -> entity?.toDomain() }

    override fun observeRecent(limit: Int): Flow<List<BodyWeightLog>> =
        dao.observeRecent(limit)
            .map { entities ->
                entities.map { it.toDomain() }
            }

    override fun observeRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<BodyWeightLog>> =
        dao.observeRange(
            startDateIsoInclusive = startDate.toString(),
            endDateIsoInclusive = endDate.toString()
        ).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getByDate(date: LocalDate): BodyWeightLog? =
        dao.getByDate(date.toString())?.toDomain()

    override suspend fun upsertByDate(
        date: LocalDate,
        weight: Double,
        unit: BodyWeightUnit,
        note: String?
    ): Long {
        val now = System.currentTimeMillis()

        return dao.upsertByDate(
            BodyWeightLogEntity(
                dateIso = date.toString(),
                weight = weight.coerceAtLeast(0.0),
                unit = unit.code,
                note = note?.trim()?.takeIf { it.isNotBlank() },
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )
    }

    override suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun deleteByDate(date: LocalDate) {
        dao.deleteByDate(date.toString())
    }
}

private fun BodyWeightLogEntity.toDomain(): BodyWeightLog =
    BodyWeightLog(
        id = id,
        date = LocalDate.parse(dateIso),
        weight = weight,
        unit = BodyWeightUnit.fromCode(unit),
        note = note,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )