package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.LogEntry
import com.example.adobongkangkong.domain.model.TodayLogItem
import com.example.adobongkangkong.domain.nutrition.MacroKeys
import com.example.adobongkangkong.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class ObserveLogItemsForDateUseCase @Inject constructor(
    private val logRepository: LogRepository,
) {
    operator fun invoke(
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Flow<List<TodayLogItem>> {
        val range = dateRange(date, zoneId)

        return logRepository.observeRange(range.startInclusive, range.endExclusive)
            .map { logs -> logs.map { it.toTodayLogItem() } }
    }

    private fun LogEntry.toTodayLogItem(): TodayLogItem {
        val n = nutrients
        return TodayLogItem(
            logId = id,
            itemName = itemName,
            timestamp = timestamp,
            caloriesKcal = n[MacroKeys.CALORIES],
            proteinG = n[MacroKeys.PROTEIN],
            carbsG = n[MacroKeys.CARBS],
            fatG = n[MacroKeys.FAT],
        )
    }
}

/** Local-day range in [start, end) for a given date + zone. */
private data class LocalDayRange(
    val startInclusive: java.time.Instant,
    val endExclusive: java.time.Instant
)

private fun dateRange(date: LocalDate, zoneId: ZoneId): LocalDayRange {
    val start = date.atStartOfDay(zoneId).toInstant()
    val end = date.plusDays(1).atStartOfDay(zoneId).toInstant()
    return LocalDayRange(startInclusive = start, endExclusive = end)
}
