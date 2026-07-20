package com.example.adobongkangkong.domain.weight

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for body-weight trend logs and raw measurements.
 *
 * AK intentionally separates two concepts:
 * - BodyWeightLog is the one official daily trend value used by existing
 *   dashboard, reminder, and chart logic.
 * - BodyWeightMeasurement is a raw scale/manual reading. Multiple measurements
 *   may exist on the same local date.
 *
 * MVP Health Connect import rule lives above this repository in a use case:
 * - Store imported weights as raw measurements.
 * - Treat source-record repeats or near-identical readings as duplicates.
 * - Keep same-day imports only when they are meaningfully separated, defaulting
 *   to a four-hour minimum gap.
 * - Do not silently replace a daily trend value when several measurements exist.
 *
 * Future trend-selection support:
 * - upsertByDate accepts selectedMeasurementId and trendSelectionMethod so AK can
 *   later derive the daily trend weight from a user preference such as “closest
 *   to 7:00 AM,” while still preserving explicit user overrides.
 *
 * Reminder rules:
 * - Reminder mode, interval, preferred weigh-in time, default trend-selection
 *   method, and duplicate/gap preferences belong in UserPreferences/DataStore.
 */
interface BodyWeightLogRepository {

    fun observeLatest(): Flow<BodyWeightLog?>

    suspend fun getLatest(): BodyWeightLog?

    fun observeByDate(date: LocalDate): Flow<BodyWeightLog?>

    fun observeRecent(limit: Int = 30): Flow<List<BodyWeightLog>>

    fun observeRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<BodyWeightLog>>

    suspend fun getByDate(date: LocalDate): BodyWeightLog?

    /**
     * Upserts the single official trend weight for a local date.
     *
     * selectedMeasurementId/trendSelectionMethod are nullable so legacy/manual
     * rows remain valid. When the daily trend is derived from a raw measurement,
     * pass the chosen measurement id and the rule that chose it.
     */
    suspend fun upsertByDate(
        date: LocalDate,
        weight: Double,
        unit: BodyWeightUnit,
        note: String?,
        selectedMeasurementId: Long? = null,
        trendSelectionMethod: BodyWeightTrendSelectionMethod? = null,
        isTrendUserOverride: Boolean = false
    ): Long

    suspend fun deleteById(id: Long)

    suspend fun deleteByDate(date: LocalDate)

    fun observeMeasurementsByDate(date: LocalDate): Flow<List<BodyWeightMeasurement>>

    fun observeMeasurementRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<BodyWeightMeasurement>>

    suspend fun getMeasurementsByDate(date: LocalDate): List<BodyWeightMeasurement>

    suspend fun getLatestMeasurement(): BodyWeightMeasurement?

    suspend fun getLatestMeasurementAfter(after: Instant): BodyWeightMeasurement?

    suspend fun getNearestMeasurementOnDate(
        date: LocalDate,
        measuredAt: Instant
    ): BodyWeightMeasurement?

    suspend fun getMeasurementBySourceRecordId(
        source: BodyWeightMeasurementSource,
        sourceRecordId: String
    ): BodyWeightMeasurement?

    /**
     * Finds a likely duplicate of an incoming measurement.
     *
     * The repository implementation should translate the window/tolerance into
     * DAO query bounds. The import use case decides what to do with the match.
     */
    suspend fun findNearDuplicateMeasurement(
        date: LocalDate,
        source: BodyWeightMeasurementSource,
        sourcePackage: String?,
        measuredAt: Instant,
        weightKg: Double,
        duplicateWindowMinutes: Long,
        duplicateToleranceKg: Double
    ): BodyWeightMeasurement?

    suspend fun insertMeasurement(measurement: BodyWeightMeasurement): Long

    suspend fun softDeleteMeasurementById(id: Long)
}
