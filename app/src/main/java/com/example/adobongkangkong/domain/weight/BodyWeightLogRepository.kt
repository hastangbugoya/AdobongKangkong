package com.example.adobongkangkong.domain.weight

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for persisted body-weight logs.
 *
 * Data rules:
 * - Body-weight logs are historical time-series data.
 * - One log per calendar date for MVP.
 * - Upserting a log for an existing date updates that date's existing row.
 *
 * Reminder rules:
 * - Reminder mode, interval, and dismiss/reset state do not belong here.
 * - Those are user preferences handled separately through UserPreferencesRepository/DataStore.
 */
interface BodyWeightLogRepository {

    fun observeLatest(): Flow<BodyWeightLog?>

    fun observeByDate(date: LocalDate): Flow<BodyWeightLog?>

    fun observeRecent(limit: Int = 30): Flow<List<BodyWeightLog>>

    fun observeRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<BodyWeightLog>>

    suspend fun getByDate(date: LocalDate): BodyWeightLog?

    suspend fun upsertByDate(
        date: LocalDate,
        weight: Double,
        unit: BodyWeightUnit,
        note: String?
    ): Long

    suspend fun deleteById(id: Long)

    suspend fun deleteByDate(date: LocalDate)
}