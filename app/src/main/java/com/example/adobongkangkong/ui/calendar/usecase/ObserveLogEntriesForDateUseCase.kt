package com.example.adobongkangkong.ui.calendar.usecase

import com.example.adobongkangkong.domain.model.LogEntry
import com.example.adobongkangkong.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class ObserveLogEntriesForDateUseCase @Inject constructor(
    private val repo: LogRepository
) {
    operator fun invoke(
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Flow<List<LogEntry>> {
        val startInclusive: Instant =
            date.atStartOfDay(zoneId).toInstant()

        val endExclusive: Instant =
            date.plusDays(1).atStartOfDay(zoneId).toInstant()

        return repo.observeRange(
            startInclusive = startInclusive,
            endExclusive = endExclusive
        )
    }
}