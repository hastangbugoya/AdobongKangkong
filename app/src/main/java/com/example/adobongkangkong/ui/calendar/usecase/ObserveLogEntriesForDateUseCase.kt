package com.example.adobongkangkong.ui.calendar.usecase

import com.example.adobongkangkong.domain.model.LogEntry
import com.example.adobongkangkong.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Observes all [LogEntry] rows for a single calendar day.
 *
 * ## Why this exists
 * The UI needs a clean “give me logs for THIS day only” stream.
 *
 * ## Critical correctness rule
 * Day membership is determined ONLY by `logDateIso` (yyyy-MM-dd), not by timestamp bounds.
 * This prevents the Day Log bug where entries from other days appear under the selected date.
 *
 * ## Parameters
 * - [date] is the selected calendar day.
 * - [zoneId] is kept for call-site compatibility (many callers already have it),
 *   but it does NOT affect which rows are returned because the repository query is
 *   an exact match on `logDateIso`.
 */
class ObserveLogEntriesForDateUseCase @Inject constructor(
    private val repo: LogRepository
) {

    /**
     * @param date Selected calendar day.
     * @param zoneId Kept for compatibility; unused for membership filtering.
     * @return A stream containing only entries where `log_entries.logDateIso == date.toString()`.
     */
    @Suppress("UNUSED_PARAMETER")
    operator fun invoke(
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Flow<List<LogEntry>> {
        val dateIso = date.toString() // yyyy-MM-dd
        return repo.observeDay(dateIso)
    }
}