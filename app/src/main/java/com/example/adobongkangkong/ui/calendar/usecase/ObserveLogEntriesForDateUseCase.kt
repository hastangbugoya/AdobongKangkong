package com.example.adobongkangkong.ui.calendar.usecase

import com.example.adobongkangkong.domain.model.LogEntry
import com.example.adobongkangkong.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Observes all [LogEntry] rows for a single calendar day using ISO-date membership.
 *
 * ## Purpose
 * Provide a clean, authoritative stream of log entries for exactly one selected
 * calendar day, suitable for day-detail screens and per-day summaries.
 *
 * ## Rationale (why this use case exists)
 * Day-level log views are foundational to:
 * - Today screen
 * - Planner day screen
 * - Calendar drill-down
 *
 * Historically, date filtering bugs can occur when:
 * - Timestamp windows (start-of-day/end-of-day) are used,
 * - Timezone offsets shift entries across boundaries,
 * - DST transitions alter computed ranges.
 *
 * This use case exists to enforce the invariant that:
 *
 *     Day membership is determined ONLY by `logDateIso` (yyyy-MM-dd).
 *
 * By delegating to an exact ISO-date match in the repository, we prevent the
 * “Day Log bug” where entries from adjacent days leak into the selected day.
 *
 * ## Behavior
 * - Converts the provided [LocalDate] to ISO string (`yyyy-MM-dd`).
 * - Delegates to [LogRepository.observeDay].
 * - Returns a reactive stream of log entries for that date only.
 *
 * ## Parameters
 * - `date`: The selected calendar day (authoritative).
 * - `zoneId`: Kept for call-site compatibility. Does NOT affect membership filtering.
 *
 * ## Return
 * A [Flow] emitting a list of [LogEntry] where:
 *
 *     log_entries.logDateIso == date.toString()
 *
 * The stream updates whenever rows for that date change.
 *
 * ## Edge cases
 * - No logs for the date → emits empty list.
 * - Multiple updates within the same day → flow re-emits updated list.
 * - Timezone differences do not affect membership because ISO date is stored explicitly.
 *
 * ## Pitfalls / gotchas
 * - Do NOT replace this with timestamp-range filtering.
 * - Do NOT compute start/end-of-day epoch windows here.
 * - `zoneId` is intentionally unused for membership; removing it may break call-site symmetry.
 *
 * ## Architectural rules
 * - Read-only use case.
 * - Repository defines the exact storage and filtering implementation.
 * - UI must not bypass this use case and query repository directly.
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

/**
 * FUTURE-AI / MAINTENANCE KDoc (Do not remove)
 *
 * ## Invariants (must not change)
 * - Day membership MUST be determined exclusively by `logDateIso`.
 * - No timestamp-bound filtering logic may be introduced here.
 * - The ISO format must remain `yyyy-MM-dd` using `LocalDate.toString()`.
 *
 * ## Do not refactor notes
 * - Do not remove the `zoneId` parameter without auditing all call sites.
 * - Do not convert to epoch-based filtering.
 * - Do not join back to foods or recompute nutrients here; this is raw log retrieval.
 *
 * ## Architectural boundaries
 * - Domain repository provides `observeDay(dateIso)`.
 * - This use case is a thin, correctness-enforcing wrapper.
 * - UI must depend on this instead of reimplementing filtering logic.
 *
 * ## Migration notes (KMP / time APIs)
 * - If migrating to KMP, preserve ISO-date string semantics exactly.
 * - Do not introduce locale-based formatting.
 *
 * ## Performance considerations
 * - Performance depends on repository indexing of `logDateIso`.
 * - Ensure the underlying table is indexed on `logDateIso` for efficient day queries.
 */