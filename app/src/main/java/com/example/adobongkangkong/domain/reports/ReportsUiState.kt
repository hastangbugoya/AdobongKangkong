package com.example.adobongkangkong.ui.reports

import com.example.adobongkangkong.domain.reports.model.MacroReportsData
import com.example.adobongkangkong.domain.reports.model.ReportRangeMode
import java.time.LocalDate

data class ReportsUiState(
    val mode: ReportRangeMode = ReportRangeMode.ROLLING_30,
    val anchorDate: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    val data: MacroReportsData? = null,
    val errorMessage: String? = null
)
