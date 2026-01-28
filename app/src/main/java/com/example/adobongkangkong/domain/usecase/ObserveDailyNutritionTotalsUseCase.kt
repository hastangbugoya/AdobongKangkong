package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.DailyNutritionTotals
import com.example.adobongkangkong.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Aggregates nutrients for a single local date by summing immutable nutrition snapshots
 * from log entries within the time window [startInclusive, endExclusive].
 *
 * Key properties:
 * - Uses user-local day boundaries.
 * - Sums immutable nutrition snapshots, guaranteeing historical correctness.
 * - Produces a normalized map: nutrientCode -> totalAmountConsumed.
 */
class ObserveDailyNutritionTotalsUseCase @Inject constructor(
    private val logRepository: LogRepository
) {
    operator fun invoke(
        date: LocalDate,
        zoneId: ZoneId
    ): Flow<DailyNutritionTotals> {
        val (startInclusive, endExclusive) = dayBounds(date, zoneId)

        return logRepository.observeRange(startInclusive, endExclusive)
            .map { entries ->
                val totals = mutableMapOf<String, Double>()

                for (entry in entries) {
                    entry.nutrients.asMap().forEach { (code, value) ->
                        totals[code] = (totals[code] ?: 0.0) + value
                    }
                }

                DailyNutritionTotals(
                    date = date,
                    totalsByCode = totals
                )
            }
    }
}

private fun dayBounds(
    date: LocalDate,
    zoneId: ZoneId
): Pair<Instant, Instant> {
    val start = date.atStartOfDay(zoneId).toInstant()
    val end = date.plusDays(1).atStartOfDay(zoneId).toInstant()
    return start to end
}
