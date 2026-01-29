package com.example.adobongkangkong.ui.daylog.usecase

import com.example.adobongkangkong.domain.nutrition.MacroKeys
import com.example.adobongkangkong.domain.repository.LogRepository
import com.example.adobongkangkong.ui.daylog.model.DayLogRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class ObserveDayLogRowsUseCase @Inject constructor(
    private val logRepository: LogRepository
) {
    operator fun invoke(
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Flow<List<DayLogRow>> {
        val start = date.atStartOfDay(zoneId).toInstant()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant()

        return logRepository.observeRange(startInclusive = start, endExclusive = end)
            .map { logs ->
                logs.map { log ->
                    val n = log.nutrients
                    DayLogRow(
                        logId = log.id,
                        itemName = log.itemName,
                        timestamp = log.timestamp,
                        caloriesKcal = n[MacroKeys.CALORIES],
                        proteinG = n[MacroKeys.PROTEIN],
                        carbsG = n[MacroKeys.CARBS],
                        fatG = n[MacroKeys.FAT]
                    )
                }
            }
    }
}
