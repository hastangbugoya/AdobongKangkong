package com.example.adobongkangkong.domain.reports

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.reports.model.IntakeWeightTrendDailyValue
import com.example.adobongkangkong.domain.reports.model.IntakeWeightTrendReport
import com.example.adobongkangkong.domain.reports.model.ReportRangeMode
import com.example.adobongkangkong.domain.reports.model.TrendDirection
import com.example.adobongkangkong.domain.repository.LogRepository
import com.example.adobongkangkong.domain.weight.BodyWeightLog
import com.example.adobongkangkong.domain.weight.BodyWeightLogRepository
import com.example.adobongkangkong.domain.weight.BodyWeightUnit
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Builds the observational intake-versus-weight report.
 *
 * Data sources:
 * - Calories consumed are calculated from AK food log nutrient snapshots.
 * - Weight uses BodyWeightLog daily trend rows, not raw BodyWeightMeasurement
 *   readings, because raw same-day scale readings are too noisy for reports.
 *
 * Interpretation rule:
 * - This use case may describe observed trend direction.
 * - It must not predict future goal dates.
 * - It must not claim calories caused the weight change. AK does not know all
 *   relevant life factors such as water, sodium, sleep, stress, workouts,
 *   illness, scale timing, or missed logs.
 */
class ObserveIntakeWeightTrendReportUseCase @Inject constructor(
    private val logs: LogRepository,
    private val bodyWeights: BodyWeightLogRepository
) {

    operator fun invoke(
        mode: ReportRangeMode,
        anchorDate: LocalDate
    ): Flow<IntakeWeightTrendReport> {
        val range = resolveRange(mode, anchorDate)

        return combine(
            logs.observeRangeByDateIso(
                startDateIsoInclusive = range.start.toString(),
                endDateIsoInclusive = range.end.toString()
            ),
            bodyWeights.observeRange(
                startDate = range.start,
                endDate = range.end
            )
        ) { entries, weightLogs ->
            val days = datesInclusive(range.start, range.end)

            val entriesByDate = entries.groupBy { entry ->
                runCatching { LocalDate.parse(entry.logDateIso) }.getOrNull()
            }

            val weightUnit = weightLogs
                .maxByOrNull { it.date }
                ?.unit
                ?: BodyWeightUnit.LB

            val weightsByDate = weightLogs.associateBy { it.date }

            val dailyValues = days.map { date ->
                val dayEntries = entriesByDate[date].orEmpty()
                val calories = dayEntries.sumOf { entry ->
                    entry.nutrients[NutrientKey.CALORIES_KCAL] ?: 0.0
                }

                IntakeWeightTrendDailyValue(
                    date = date,
                    calories = calories,
                    isFoodLogged = dayEntries.isNotEmpty(),
                    trendWeight = weightsByDate[date]?.toDisplayWeight(weightUnit)
                )
            }

            val averageCaloriesLast7 = dailyValues
                .takeLast(7)
                .loggedCaloriesAverageOrNull()

            val averageCaloriesPrevious7 = dailyValues
                .dropLast(7)
                .takeLast(7)
                .loggedCaloriesAverageOrNull()

            val calorieChangePerDay = averageCaloriesLast7
                ?.let { last -> averageCaloriesPrevious7?.let { previous -> last - previous } }

            val observedWeights = dailyValues
                .mapNotNull { value ->
                    value.trendWeight?.let { weight -> value.date to weight }
                }

            val weightChange = if (observedWeights.size >= 2) {
                observedWeights.last().second - observedWeights.first().second
            } else {
                null
            }

            IntakeWeightTrendReport(
                subtitle = buildSubtitle(mode, range.start, range.end),
                dailyValues = dailyValues,
                weightUnit = weightUnit.symbol,
                averageCaloriesLast7 = averageCaloriesLast7,
                averageCaloriesPrevious7 = averageCaloriesPrevious7,
                calorieChangePerDay = calorieChangePerDay,
                weightChange = weightChange,
                calorieDirection = calorieChangePerDay.toTrendDirection(
                    flatThreshold = CALORIE_FLAT_THRESHOLD_KCAL
                ),
                weightDirection = weightChange.toTrendDirection(
                    flatThreshold = WEIGHT_FLAT_THRESHOLD
                )
            )
        }
    }

    private fun BodyWeightLog.toDisplayWeight(unit: BodyWeightUnit): Double =
        when {
            this.unit == unit -> weight
            this.unit == BodyWeightUnit.KG && unit == BodyWeightUnit.LB ->
                weight * POUNDS_PER_KILOGRAM
            this.unit == BodyWeightUnit.LB && unit == BodyWeightUnit.KG ->
                weight / POUNDS_PER_KILOGRAM
            else -> weight
        }

    private fun List<IntakeWeightTrendDailyValue>.loggedCaloriesAverageOrNull(): Double? {
        val logged = filter { it.isFoodLogged }
            .map { it.calories }
        return if (logged.isEmpty()) null else logged.average()
    }

    private fun Double?.toTrendDirection(flatThreshold: Double): TrendDirection {
        val value = this ?: return TrendDirection.UNKNOWN
        return when {
            value > flatThreshold -> TrendDirection.UP
            value < -flatThreshold -> TrendDirection.DOWN
            else -> TrendDirection.FLAT
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

    private data class DateRange(
        val start: LocalDate,
        val end: LocalDate
    )

    private companion object {
        const val CALORIE_FLAT_THRESHOLD_KCAL = 50.0
        const val WEIGHT_FLAT_THRESHOLD = 0.2
        const val POUNDS_PER_KILOGRAM = 2.2046226218
    }
}
