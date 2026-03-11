package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.DbTypeConverters
import com.example.adobongkangkong.data.local.db.dao.LogEntryDao
import com.example.adobongkangkong.data.local.db.mapper.toDomain
import com.example.adobongkangkong.data.local.db.mapper.toDomainLogEntry
import com.example.adobongkangkong.data.local.db.mapper.toLogEntryEntity
import com.example.adobongkangkong.domain.model.LogEntry
import com.example.adobongkangkong.domain.model.TodayLogItem
import com.example.adobongkangkong.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed implementation of [LogRepository].
 *
 * Important behavior:
 * - Day and date-range reads are filtered by `logDateIso` (yyyy-MM-dd) so the Day Log UI cannot
 *   accidentally show entries from other days.
 * - Timestamps can still be used for ordering, but NOT for determining membership in a day.
 */
class LogRepositoryImpl @Inject constructor(
    private val dao: LogEntryDao,
    private val converters: DbTypeConverters
) : LogRepository {

    /**
     * Inserts a log entry into the database.
     *
     * Writes the entity form via [toLogEntryEntity] and returns the new row id.
     */
    override suspend fun insert(logEntry: LogEntry): Long {
        return dao.insert(logEntry.toLogEntryEntity(converters))
    }

    override suspend fun update(entry: LogEntry) {
        dao.update(entry.toLogEntryEntity(converters))
    }

    override suspend fun getById(logId: Long): LogEntry? {
        return dao.getById(logId)?.toDomainLogEntry(converters)
    }

    /**
     * Observes log entries for exactly one ISO day.
     *
     * Delegates to a DAO query that MUST filter by `logDateIso = :logDateIso`.
     * This is the critical guard that prevents Day Log from showing entries from other dates.
     */
    override fun observeDay(logDateIso: String): Flow<List<LogEntry>> =
        dao.observeDayByLogDateIso(logDateIso)
            .map { entities -> entities.map { it.toDomainLogEntry(converters) } }

    /**
     * Observes log entries across an inclusive ISO date range.
     *
     * Delegates to a DAO query that MUST filter by ISO date (string) range:
     * `start <= logDateIso <= end`.
     *
     * Ordering is left to the DAO (commonly by date then timestamp).
     */
    override fun observeRangeByDateIso(
        startDateIsoInclusive: String,
        endDateIsoInclusive: String
    ): Flow<List<LogEntry>> =
        dao.observeRangeByLogDateIso(startDateIsoInclusive, endDateIsoInclusive)
            .map { entities -> entities.map { it.toDomainLogEntry(converters) } }

    /**
     * Observes optimized projection rows for the dashboard "Logged Today" list.
     *
     * Uses a DAO projection query (no joins) and maps each row into a [TodayLogItem].
     * The DAO query MUST still filter by `logDateIso = :logDateIso`.
     */
    override fun observeTodayItems(logDateIso: String): Flow<List<TodayLogItem>> =
        dao.observeTodayLogRows(logDateIso)
            .map { rows -> rows.map { it.toDomain(converters) } }

    /**
     * Deletes a log entry row by id.
     */
    override suspend fun deleteById(logId: Long) {
        dao.deleteById(logId)
    }
}