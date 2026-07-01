package com.example.adobongkangkong.domain.reports

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.reports.model.MacroDailyValue
import com.example.adobongkangkong.domain.reports.model.MacroReportMetric
import com.example.adobongkangkong.domain.reports.model.MacroReportStats
import com.example.adobongkangkong.domain.reports.model.MacroReportsData
import com.example.adobongkangkong.domain.reports.model.ReportRangeMode
import com.example.adobongkangkong.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.collections.map

class ObserveMacroReportsUseCase @Inject constructor(
    private val logs: LogRepository
) {

    operator fun invoke(
        mode: ReportRangeMode,
        anchorDate: LocalDate
    ): Flow<MacroReportsData> {
        val range = resolveRange(mode, anchorDate)

        return logs.observeRangeByDateIso(
            startDateIsoInclusive = range.start.toString(),
            endDateIsoInclusive = range.end.toString()
        ).map { entries ->
            val days = datesInclusive(range.start, range.end)

            val entriesByDate = entries.groupBy { entry ->
                runCatching { LocalDate.parse(entry.logDateIso) }.getOrNull()
            }

            val metrics = macroDefinitions.map { def ->
                val dailyValues = days.map { date ->
                    val dayEntries = entriesByDate[date].orEmpty()
                    val value = dayEntries.sumOf { entry ->
                        entry.nutrients[NutrientKey(def.nutrientCode)] ?: 0.0
                    }

                    MacroDailyValue(
                        date = date,
                        value = value,
                        isLogged = dayEntries.isNotEmpty()
                    )
                }

                val loggedValues = dailyValues
                    .filter { it.isLogged }
                    .map { it.value }

                MacroReportMetric(
                    name = def.name,
                    unit = def.unit,
                    nutrientCode = def.nutrientCode,
                    dailyValues = dailyValues,
                    stats = MacroReportStats(
                        average = loggedValues.averageOrNull(),
                        high = loggedValues.maxOrNull(),
                        low = loggedValues.minOrNull(),
                        loggedDays = dailyValues.count { it.isLogged },
                        totalDays = dailyValues.size
                    )
                )
            }

            MacroReportsData(
                mode = mode,
                startDate = range.start,
                endDate = range.end,
                title = "Reports",
                subtitle = buildSubtitle(mode, range.start, range.end),
                metrics = metrics
            )
        }
    }

    private fun resolveRange(
        mode: ReportRangeMode,
        anchorDate: LocalDate
    ): DateRange {
        return when (mode) {
            ReportRangeMode.ROLLING_30 -> DateRange(
                start = anchorDate.minusDays(29),
                end = anchorDate
            )

            ReportRangeMode.MONTH -> {
                val month = YearMonth.from(anchorDate)
                DateRange(
                    start = month.atDay(1),
                    end = month.atEndOfMonth()
                )
            }
        }
    }

    private fun buildSubtitle(
        mode: ReportRangeMode,
        start: LocalDate,
        end: LocalDate
    ): String {
        val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

        return when (mode) {
            ReportRangeMode.ROLLING_30 ->
                "Rolling 30 days • ${start.format(formatter)} - ${end.format(formatter)}"

            ReportRangeMode.MONTH ->
                YearMonth.from(start)
                    .atDay(1)
                    .format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
        }
    }

    private fun datesInclusive(
        start: LocalDate,
        end: LocalDate
    ): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var d = start

        while (!d.isAfter(end)) {
            out += d
            d = d.plusDays(1)
        }

        return out
    }

    private fun List<Double>.averageOrNull(): Double? {
        return if (isEmpty()) null else average()
    }

    private data class DateRange(
        val start: LocalDate,
        val end: LocalDate
    )

    private data class MacroDefinition(
        val name: String,
        val unit: String,
        val nutrientCode: String
    )

    private companion object {
        val macroDefinitions = listOf(
            MacroDefinition("Calories", "kcal", "CALORIES_KCAL"),
            MacroDefinition("Protein", "g", "PROTEIN_G"),
            MacroDefinition("Carbs", "g", "CARBS_G"),
            MacroDefinition("Fat", "g", "FAT_G")
        )
    }
}