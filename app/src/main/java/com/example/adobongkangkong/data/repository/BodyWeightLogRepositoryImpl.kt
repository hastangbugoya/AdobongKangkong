package com.example.adobongkangkong.data.weight

import com.example.adobongkangkong.data.local.db.dao.BodyWeightLogDao
import com.example.adobongkangkong.data.local.db.dao.BodyWeightMeasurementDao
import com.example.adobongkangkong.data.local.db.entity.BodyWeightLogEntity
import com.example.adobongkangkong.data.local.db.entity.BodyWeightMeasurementEntity
import com.example.adobongkangkong.domain.weight.BodyWeightLog
import com.example.adobongkangkong.domain.weight.BodyWeightLogRepository
import com.example.adobongkangkong.domain.weight.BodyWeightMeasurement
import com.example.adobongkangkong.domain.weight.BodyWeightMeasurementSource
import com.example.adobongkangkong.domain.weight.BodyWeightTrendSelectionMethod
import com.example.adobongkangkong.domain.weight.BodyWeightUnit
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed implementation for body-weight trend logs and raw measurements.
 *
 * AK intentionally keeps two layers:
 * - BodyWeightLogEntity remains the one official daily trend weight used by
 *   the existing dashboard, reminder, and chart flows.
 * - BodyWeightMeasurementEntity stores raw manual/imported readings, allowing
 *   multiple measurements on the same local date without polluting the daily
 *   trend line.
 *
 * Import/use-case rules such as the four-hour minimum same-day gap and
 * near-duplicate handling are kept above this repository. This class only
 * exposes the persistence operations those rules need.
 */
@Singleton
class BodyWeightLogRepositoryImpl @Inject constructor(
    private val dao: BodyWeightLogDao,
    private val measurementDao: BodyWeightMeasurementDao
) : BodyWeightLogRepository {

    override fun observeLatest(): Flow<BodyWeightLog?> =
        dao.observeLatest()
            .map { entity -> entity?.toDomain() }

    override suspend fun getLatest(): BodyWeightLog? =
        dao.getLatest()?.toDomain()

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
        note: String?,
        selectedMeasurementId: Long?,
        trendSelectionMethod: BodyWeightTrendSelectionMethod?,
        isTrendUserOverride: Boolean
    ): Long {
        val now = System.currentTimeMillis()
        val hasTrendSelection = selectedMeasurementId != null || trendSelectionMethod != null

        return dao.upsertByDate(
            BodyWeightLogEntity(
                dateIso = date.toString(),
                weight = weight.coerceAtLeast(0.0),
                unit = unit.code,
                selectedMeasurementId = selectedMeasurementId,
                trendSelectionMethod = trendSelectionMethod?.name,
                isTrendUserOverride = isTrendUserOverride,
                trendSelectedAtEpochMs = if (hasTrendSelection) now else null,
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

    override fun observeMeasurementsByDate(date: LocalDate): Flow<List<BodyWeightMeasurement>> =
        measurementDao.observeByDate(date.toString())
            .map { entities -> entities.map { it.toDomain() } }

    override fun observeMeasurementRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<BodyWeightMeasurement>> =
        measurementDao.observeRange(
            startDateIsoInclusive = startDate.toString(),
            endDateIsoInclusive = endDate.toString()
        ).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getMeasurementsByDate(
        date: LocalDate
    ): List<BodyWeightMeasurement> =
        measurementDao.getByDate(date.toString())
            .map { it.toDomain() }

    override suspend fun getLatestMeasurement(): BodyWeightMeasurement? =
        measurementDao.getLatest()?.toDomain()

    override suspend fun getLatestMeasurementAfter(
        after: Instant
    ): BodyWeightMeasurement? =
        measurementDao.getLatestAfter(after.toEpochMilli())?.toDomain()

    override suspend fun getNearestMeasurementOnDate(
        date: LocalDate,
        measuredAt: Instant
    ): BodyWeightMeasurement? =
        measurementDao.getNearestOnDate(
            dateIso = date.toString(),
            measuredAtEpochMs = measuredAt.toEpochMilli()
        )?.toDomain()

    override suspend fun getMeasurementBySourceRecordId(
        source: BodyWeightMeasurementSource,
        sourceRecordId: String
    ): BodyWeightMeasurement? =
        measurementDao.getBySourceRecordId(
            source = source.name,
            sourceRecordId = sourceRecordId
        )?.toDomain()

    override suspend fun findNearDuplicateMeasurement(
        date: LocalDate,
        source: BodyWeightMeasurementSource,
        sourcePackage: String?,
        measuredAt: Instant,
        weightKg: Double,
        duplicateWindowMinutes: Long,
        duplicateToleranceKg: Double
    ): BodyWeightMeasurement? {
        val measuredAtMs = measuredAt.toEpochMilli()
        val windowMs = duplicateWindowMinutes.coerceAtLeast(0L) * 60_000L
        val toleranceKg = duplicateToleranceKg.coerceAtLeast(0.0)

        return measurementDao.findNearDuplicate(
            dateIso = date.toString(),
            source = source.name,
            sourcePackage = sourcePackage?.trim()?.takeIf { it.isNotBlank() },
            measuredAtEpochMs = measuredAtMs,
            startEpochMsInclusive = measuredAtMs - windowMs,
            endEpochMsInclusive = measuredAtMs + windowMs,
            minWeightKgInclusive = (weightKg - toleranceKg).coerceAtLeast(0.0),
            maxWeightKgInclusive = weightKg + toleranceKg
        )?.toDomain()
    }

    override suspend fun insertMeasurement(
        measurement: BodyWeightMeasurement
    ): Long {
        return measurementDao.insert(measurement.toEntity())
    }

    override suspend fun softDeleteMeasurementById(id: Long) {
        measurementDao.softDeleteById(
            id = id,
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }
}

private fun BodyWeightLogEntity.toDomain(): BodyWeightLog =
    BodyWeightLog(
        id = id,
        date = LocalDate.parse(dateIso),
        weight = weight,
        unit = BodyWeightUnit.fromCode(unit),
        note = note,
        selectedMeasurementId = selectedMeasurementId,
        trendSelectionMethod = trendSelectionMethod.toBodyWeightTrendSelectionMethod(),
        isTrendUserOverride = isTrendUserOverride,
        trendSelectedAtEpochMs = trendSelectedAtEpochMs,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )

private fun BodyWeightMeasurementEntity.toDomain(): BodyWeightMeasurement =
    BodyWeightMeasurement(
        id = id,
        date = LocalDate.parse(dateIso),
        measuredAt = Instant.ofEpochMilli(measuredAtEpochMs),
        weightKg = weightKg,
        source = source.toBodyWeightMeasurementSource(),
        sourcePackage = sourcePackage,
        sourceRecordId = sourceRecordId,
        importedAt = importedAtEpochMs?.let(Instant::ofEpochMilli),
        note = note,
        isDeleted = isDeleted,
        createdAt = Instant.ofEpochMilli(createdAtEpochMs),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMs)
    )

private fun BodyWeightMeasurement.toEntity(): BodyWeightMeasurementEntity =
    BodyWeightMeasurementEntity(
        id = id,
        dateIso = date.toString(),
        measuredAtEpochMs = measuredAt.toEpochMilli(),
        weightKg = weightKg.coerceAtLeast(0.0),
        source = source.name,
        sourcePackage = sourcePackage?.trim()?.takeIf { it.isNotBlank() },
        sourceRecordId = sourceRecordId?.trim()?.takeIf { it.isNotBlank() },
        importedAtEpochMs = importedAt?.toEpochMilli(),
        note = note?.trim()?.takeIf { it.isNotBlank() },
        isDeleted = isDeleted,
        createdAtEpochMs = createdAt.toEpochMilli(),
        updatedAtEpochMs = updatedAt.toEpochMilli()
    )


private fun String?.toBodyWeightTrendSelectionMethod(): BodyWeightTrendSelectionMethod? =
    this?.let { value ->
        runCatching { BodyWeightTrendSelectionMethod.valueOf(value) }.getOrNull()
    }

private fun String.toBodyWeightMeasurementSource(): BodyWeightMeasurementSource =
    runCatching { BodyWeightMeasurementSource.valueOf(this) }
        .getOrDefault(BodyWeightMeasurementSource.MANUAL)
