package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.domain.repository.PlannedSeriesRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.max

/**
 * Ensures recurrence-generated planned meal occurrences exist for a bounded horizon window.
 *
 * Design:
 * - Planner reads occurrences only (planned_meals/items).
 * - This orchestrator expands series rules into occurrences *outside* the hot path.
 * - Efficient: only expands when requested horizon exceeds the last ensured end date.
 */
class EnsurePlannerHorizonUseCase @Inject constructor(
    private val seriesRepo: PlannedSeriesRepository,
    private val ensureSeries: EnsureSeriesOccurrencesWithinHorizonUseCase
) {
    companion object {
        // Phase 1 default; tweak later if needed.
        const val DEFAULT_HORIZON_DAYS: Long = 180
    }

    /**
     * @param anchorDate The date the user is viewing (start of interest).
     * @param horizonDays How far forward to ensure (inclusive).
     * @param lastEnsuredEnd Optional cache from UI layer to avoid redundant work.
     *
     * @return newEnsuredEnd (so caller can cache it)
     */
    suspend fun execute(
        anchorDate: LocalDate,
        horizonDays: Long = DEFAULT_HORIZON_DAYS,
        lastEnsuredEnd: LocalDate? = null
    ): LocalDate {
        require(horizonDays >= 0) { "horizonDays must be >= 0" }

        val targetEnd = anchorDate.plusDays(horizonDays)

        // If we already ensured far enough into the future, do nothing.
        if (lastEnsuredEnd != null && !targetEnd.isAfter(lastEnsuredEnd)) {
            return lastEnsuredEnd
        }

        // Only ensure the delta (efficient).
        val start = lastEnsuredEnd?.plusDays(1)?.takeIf { !it.isBefore(anchorDate) } ?: anchorDate
        val startIso = start.toString()
        val endIso = targetEnd.toString()

        val series = seriesRepo.getSeriesOverlappingRange(startIso, endIso)
        if (series.isEmpty()) return (lastEnsuredEnd ?: targetEnd)

        for (s in series) {
            // Idempotent; safe if called repeatedly.
            ensureSeries.execute(
                seriesId = s.id,
                startDateIso = startIso,
                endDateIso = endIso
            )
        }

        return if (lastEnsuredEnd == null) targetEnd else maxDate(lastEnsuredEnd, targetEnd)
    }

    private fun maxDate(a: LocalDate, b: LocalDate): LocalDate =
        if (a.isAfter(b)) a else b
}