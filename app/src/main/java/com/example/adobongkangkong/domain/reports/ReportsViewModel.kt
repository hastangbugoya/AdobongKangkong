package com.example.adobongkangkong.ui.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.reports.ObserveIntakeWeightTrendReportUseCase
import com.example.adobongkangkong.domain.reports.ObserveMacroReportsUseCase
import com.example.adobongkangkong.domain.reports.model.ReportRangeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ReportsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeMacroReports: ObserveMacroReportsUseCase,
    observeIntakeWeightTrendReport: ObserveIntakeWeightTrendReportUseCase
) : ViewModel() {

    private val mode: ReportRangeMode =
        runCatching {
            ReportRangeMode.valueOf(
                savedStateHandle.get<String>("mode")
                    ?: ReportRangeMode.ROLLING_30.name
            )
        }.getOrDefault(ReportRangeMode.ROLLING_30)

    private val anchorDate: LocalDate =
        runCatching {
            LocalDate.parse(
                savedStateHandle.get<String>("anchorDateIso")
                    ?: LocalDate.now().toString()
            )
        }.getOrDefault(LocalDate.now())

    val state: StateFlow<ReportsUiState> =
        combine(
            observeMacroReports(
                mode = mode,
                anchorDate = anchorDate
            ),
            observeIntakeWeightTrendReport(
                mode = mode,
                anchorDate = anchorDate
            )
        ) { macroReports, intakeWeightTrend ->
            ReportsUiState(
                mode = mode,
                anchorDate = anchorDate,
                isLoading = false,
                data = macroReports,
                intakeWeightTrend = intakeWeightTrend,
                errorMessage = null
            )
        }
            .catch { t ->
                emit(
                    ReportsUiState(
                        mode = mode,
                        anchorDate = anchorDate,
                        isLoading = false,
                        data = null,
                        intakeWeightTrend = null,
                        errorMessage = t.message ?: "Failed to load reports."
                    )
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ReportsUiState(
                    mode = mode,
                    anchorDate = anchorDate,
                    isLoading = true
                )
            )
}