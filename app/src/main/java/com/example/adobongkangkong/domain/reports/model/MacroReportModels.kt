package com.example.adobongkangkong.domain.reports.model

import java.time.LocalDate

enum class ReportRangeMode {
    ROLLING_30,
    MONTH
}

data class MacroReportsData(
    val mode: ReportRangeMode,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val title: String,
    val subtitle: String,
    val metrics: List<MacroReportMetric>
)

data class MacroReportMetric(
    val name: String,
    val unit: String,
    val nutrientCode: String,
    val dailyValues: List<MacroDailyValue>,
    val stats: MacroReportStats
)

data class MacroDailyValue(
    val date: LocalDate,
    val value: Double,
    val isLogged: Boolean
)

data class MacroReportStats(
    val average: Double?,
    val high: Double?,
    val low: Double?,
    val loggedDays: Int,
    val totalDays: Int
)
