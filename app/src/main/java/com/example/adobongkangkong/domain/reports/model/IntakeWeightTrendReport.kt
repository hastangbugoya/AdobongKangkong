package com.example.adobongkangkong.domain.reports.model

import java.time.LocalDate

/**
 * Report model for comparing AK-owned intake logs with official daily trend weight.
 *
 * This report is intentionally observational:
 * - Calories come from AK's logged food totals.
 * - Weight comes from BodyWeightLog daily trend rows, not raw scale readings.
 * - The report may show that two trends moved together, but it must not claim
 *   cause and effect.
 *
 * AK does not know every factor that affects weight: water retention, sodium,
 * carbohydrate intake, sleep, stress, training soreness, illness, bowel contents,
 * scale timing, missed logs, and device/import timing can all affect the visible
 * relationship between intake and weight.
 */
data class IntakeWeightTrendReport(
    val subtitle: String,
    val dailyValues: List<IntakeWeightTrendDailyValue>,
    val weightUnit: String,
    val averageCaloriesLast7: Double?,
    val averageCaloriesPrevious7: Double?,
    val calorieChangePerDay: Double?,
    val weightChange: Double?,
    val calorieDirection: TrendDirection,
    val weightDirection: TrendDirection,
    val note: String = DEFAULT_NOTE
) {
    companion object {
        const val DEFAULT_NOTE: String =
            "Trends may be related, but this does not prove cause and effect. " +
                "AK only sees logged food and weight; water, sodium, sleep, stress, " +
                "workouts, illness, missed logs, and scale timing can also affect weight."
    }
}

data class IntakeWeightTrendDailyValue(
    val date: LocalDate,
    val calories: Double,
    val isFoodLogged: Boolean,
    val trendWeight: Double?
)

enum class TrendDirection {
    UP,
    DOWN,
    FLAT,
    UNKNOWN
}
