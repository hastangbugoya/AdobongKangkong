package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.core.time.dayRange
import com.example.adobongkangkong.core.time.todayRange
import com.example.adobongkangkong.domain.model.LogEntry
import com.example.adobongkangkong.domain.model.TodayLogItem
import com.example.adobongkangkong.domain.nutrition.MacroKeys
import com.example.adobongkangkong.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Observes today's log items from immutable snapshot logs.
 *
 * Logs already contain `nutrients: NutrientMap` captured at log-time, so we do not
 * load Food snapshots or reference food tables here. This keeps history valid even
 * if foods are edited/deleted later.
 */
class ObserveTodayLogItemsUseCase @Inject constructor(
    private val logRepository: LogRepository,
) {

    operator fun invoke(
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Flow<List<TodayLogItem>> {
        val range = dayRange(date, zoneId)

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