package com.example.adobongkangkong.domain.reports

import com.example.adobongkangkong.domain.model.UserNutrientTarget
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.reports.model.MacroDailyValue
import com.example.adobongkangkong.domain.reports.model.MacroReportMetric
import com.example.adobongkangkong.domain.reports.model.MacroReportStats
import com.example.adobongkangkong.domain.reports.model.MacroReportsData
import com.example.adobongkangkong.domain.reports.model.ReportRangeMode
import com.example.adobongkangkong.domain.repository.LogRepository
import com.example.adobongkangkong.domain.repository.UserNutrientTargetRepository
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveMacroReportsUseCase @Inject constructor(
    private val logs: LogRepository,
    private val targets: UserNutrientTargetRepository
) {

    operator fun invoke(
        mode: ReportRangeMode,
        anchorDate: LocalDate
    ): Flow<MacroReportsData> {
        val range = resolveRange(mode, anchorDate)

        return combine(
            logs.observeRangeByDateIso(
                startDateIsoInclusive = range.start.toString(),
                endDateIsoInclusive = range.end.toString()
            ),
            targets.observeTargets()
        ) { entries, targetsByCode ->
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

                val target = targetsByCode[def.nutrientCode]
                val reference = referenceForMacro(
                    macro = def.macro,
                    target = target
                )

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
                    ),
                    referenceValue = reference?.value,
                    referenceLabel = reference?.label
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

    private fun referenceForMacro(
        macro: MacroKind,
        target: UserNutrientTarget?
    ): MacroReference? {
        if (target == null) return null

        return when (macro) {
            /*
             * Calories usually behaves like a ceiling when max exists.
             * If the user only set a target, show that as the reference line.
             */
            MacroKind.CALORIES -> {
                when {
                    target.maxPerDay != null -> MacroReference("Limit", target.maxPerDay)
                    target.targetPerDay != null -> MacroReference("Target", target.targetPerDay)
                    target.minPerDay != null -> MacroReference("Minimum", target.minPerDay)
                    else -> null
                }
            }

            /*
             * Protein usually behaves like a goal/floor.
             */
            MacroKind.PROTEIN -> {
                when {
                    target.targetPerDay != null -> MacroReference("Goal", target.targetPerDay)
                    target.minPerDay != null -> MacroReference("Minimum", target.minPerDay)
                    target.maxPerDay != null -> MacroReference("Limit", target.maxPerDay)
                    else -> null
                }
            }

            /*
             * Carbs and fat can be either target-style or limit-style depending on user setup.
             */
            MacroKind.CARBS,
            MacroKind.FAT -> {
                when {
                    target.targetPerDay != null -> MacroReference("Target", target.targetPerDay)
                    target.maxPerDay != null -> MacroReference("Limit", target.maxPerDay)
                    target.minPerDay != null -> MacroReference("Minimum", target.minPerDay)
                    else -> null
                }
            }
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

    private enum class MacroKind {
        CALORIES,
        PROTEIN,
        CARBS,
        FAT
    }

    private data class MacroDefinition(
        val macro: MacroKind,
        val name: String,
        val unit: String,
        val nutrientCode: String
    )

    private data class MacroReference(
        val label: String,
        val value: Double
    )

    private companion object {
        val macroDefinitions = listOf(
            MacroDefinition(
                macro = MacroKind.CALORIES,
                name = "Calories",
                unit = "kcal",
                nutrientCode = NutrientKey.CALORIES_KCAL.value
            ),
            MacroDefinition(
                macro = MacroKind.PROTEIN,
                name = "Protein",
                unit = "g",
                nutrientCode = NutrientKey.PROTEIN_G.value
            ),
            MacroDefinition(
                macro = MacroKind.CARBS,
                name = "Carbs",
                unit = "g",
                nutrientCode = NutrientKey.CARBS_G.value
            ),
            MacroDefinition(
                macro = MacroKind.FAT,
                name = "Fat",
                unit = "g",
                nutrientCode = NutrientKey.FAT_G.value
            )
        )
    }
}