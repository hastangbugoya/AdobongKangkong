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

/**
 * Observes "today log items" for a single calendar day.
 *
 * ## What this use case returns
 * A UI-friendly list of [TodayLogItem] rows derived from log snapshot entries for the selected day.
 *
 * ## Critical correctness rule (prevents the Day Log bug)
 * Day membership is determined ONLY by `logDateIso` (yyyy-MM-dd), not by timestamp ranges.
 * This use case therefore queries the repository using the ISO date string for the selected day.
 *
 * ## Why no joins
 * Log entries store an immutable snapshot of nutrients at log-time. We intentionally avoid
 * joining foods/recipes/tables so historical totals and display remain correct even if:
 * - foods are edited later,
 * - foods/recipes are deleted,
 * - nutrient definitions change.
 *
 * ## About ZoneId
 * The [zoneId] parameter is kept for call-site compatibility. Once a [LocalDate] is provided,
 * the calendar day is already chosen, and the query uses `date.toString()` (yyyy-MM-dd).
 * Timezone does not affect which rows are included in the day.
 */
class ObserveTodayLogItemsUseCase @Inject constructor(
    private val logRepository: LogRepository,
) {

    /**
     * Observes dashboard log items for the given [date].
     *
     * Implementation detail:
     * - Uses [LogRepository.observeTodayItems] which is backed by an optimized DAO projection
     *   for the dashboard list (no joins).
     *
     * @param date The calendar day to display.
     * @param zoneId Kept for compatibility; unused for membership filtering when [date] is provided.
     * @return A stream of [TodayLogItem] for that day only.
     */
    @Suppress("UNUSED_PARAMETER")
    operator fun invoke(
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Flow<List<TodayLogItem>> {
        val dateIso = date.toString() // yyyy-MM-dd
        return logRepository.observeTodayItems(dateIso)
    }

    /**
     * Legacy mapper kept as a local utility in case a call-site ever needs to build [TodayLogItem]
     * from full [LogEntry] objects (e.g., if the optimized projection is temporarily bypassed).
     *
     * Not used by the current implementation, which prefers [LogRepository.observeTodayItems].
     */
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