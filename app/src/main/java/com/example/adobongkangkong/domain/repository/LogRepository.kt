package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.domain.model.LogEntry
import com.example.adobongkangkong.domain.model.TodayLogItem
import kotlinx.coroutines.flow.Flow

/**
 * Domain-facing repository for reading/writing day log entries.
 *
 * Key rule for reads:
 * - Day Log and date-range queries are driven by `logDateIso` (yyyy-MM-dd), not timestamps.
 *
 * Notes:
 * - Timestamps may still be stored for posterity / ordering, but they are not used to decide which
 *   calendar day an entry belongs to for Day Log display.
 */
interface LogRepository {

    /**
     * Inserts a new log entry.
     *
     * @param entry Domain log entry to persist.
     * @return The newly inserted row id.
     */
    suspend fun insert(entry: LogEntry): Long

    /**
     * Updates an existing log entry in place.
     *
     * Used by Quick Add edit mode so an existing log row can be modified without creating a second
     * row.
     */
    suspend fun update(entry: LogEntry)

    /**
     * Returns a single log entry by primary key id, or null if missing.
     *
     * This is used by Quick Add edit mode to reopen an existing row.
     */
    suspend fun getById(logId: Long): LogEntry?

    /**
     * Observes all log entries for exactly one calendar day.
     *
     * This MUST return only entries where:
     * `log_entries.logDateIso == logDateIso`
     *
     * @param logDateIso ISO date string (yyyy-MM-dd) representing the selected day.
     * @return A stream of log entries for that day only.
     */
    fun observeDay(
        logDateIso: String
    ): Flow<List<LogEntry>>

    /**
     * Observes log entries across an inclusive date range using ISO date strings.
     *
     * This is a date-based range, not a timestamp window. It MUST include entries where:
     * `startDateIsoInclusive <= logDateIso <= endDateIsoInclusive`
     *
     * @param startDateIsoInclusive ISO date (yyyy-MM-dd), inclusive range start.
     * @param endDateIsoInclusive ISO date (yyyy-MM-dd), inclusive range end.
     * @return A stream of all log entries within the inclusive date range.
     */
    fun observeRangeByDateIso(
        startDateIsoInclusive: String,
        endDateIsoInclusive: String
    ): Flow<List<LogEntry>>

    /**
     * Observes optimized "today" rows for a dashboard list.
     *
     * This may be backed by a lightweight projection query (no joins) and returns UI-friendly
     * `TodayLogItem` models.
     *
     * @param logDateIso ISO date string (yyyy-MM-dd) for the day being displayed.
     * @return A stream of dashboard items for that day only.
     */
    fun observeTodayItems(
        logDateIso: String
    ): Flow<List<TodayLogItem>>

    /**
     * Deletes a log entry by its primary key id.
     *
     * @param logId Row id to delete.
     */
    suspend fun deleteById(logId: Long)
}