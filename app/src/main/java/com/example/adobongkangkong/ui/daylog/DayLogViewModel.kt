package com.example.adobongkangkong.ui.daylog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.model.DailyNutritionTotals
import com.example.adobongkangkong.domain.planner.usecase.DeleteIouUseCase
import com.example.adobongkangkong.domain.usecase.DeleteLogEntryUseCase
import com.example.adobongkangkong.domain.usecase.ObserveDailyNutritionTotalsUseCase
import com.example.adobongkangkong.ui.daylog.model.DayLogIouRow
import com.example.adobongkangkong.ui.daylog.model.DayLogRow
import com.example.adobongkangkong.ui.daylog.usecase.LogAgainTodayUseCase
import com.example.adobongkangkong.ui.daylog.usecase.ObserveDayLogIousUseCase
import com.example.adobongkangkong.ui.daylog.usecase.ObserveDayLogRowsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DayLogViewModel @Inject constructor(
    private val observeDayLogRows: ObserveDayLogRowsUseCase,
    private val observeDayLogIous: ObserveDayLogIousUseCase,
    private val observeDailyTotals: ObserveDailyNutritionTotalsUseCase,
    private val deleteLogEntry: DeleteLogEntryUseCase,
    private val deletePlannerIou: DeleteIouUseCase,
    private val logAgainTodayUseCase: LogAgainTodayUseCase,
    private val zoneId: ZoneId
) : ViewModel() {

    data class PendingLogAgainTodayConfirmation(
        val logId: Long,
        val message: String
    )

    private val selectedDate = MutableStateFlow<LocalDate?>(null)
    private val messageFlow = MutableStateFlow<String?>(null)
    private val pendingLogAgainTodayConfirmationFlow =
        MutableStateFlow<PendingLogAgainTodayConfirmation?>(null)

    val entries: StateFlow<List<DayLogRow>> =
        selectedDate
            .filterNotNull()
            .flatMapLatest { date -> observeDayLogRows(date.toString()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ious: StateFlow<List<DayLogIouRow>> =
        selectedDate
            .filterNotNull()
            .flatMapLatest { date -> observeDayLogIous(date.toString()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totals: StateFlow<DailyNutritionTotals?> =
        selectedDate
            .filterNotNull()
            .flatMapLatest { date -> observeDailyTotals(date, zoneId) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val message: StateFlow<String?> = messageFlow

    val pendingLogAgainTodayConfirmation: StateFlow<PendingLogAgainTodayConfirmation?> =
        pendingLogAgainTodayConfirmationFlow

    fun load(date: LocalDate) {
        selectedDate.value = date
    }

    fun deleteEntry(logId: Long) {
        viewModelScope.launch {
            deleteLogEntry(logId)
            // no manual refresh needed; flows update from Room invalidation
        }
    }

    fun deleteIou(iouId: Long) {
        viewModelScope.launch {
            deletePlannerIou(iouId)
            // no manual refresh needed; flows update from Room invalidation
        }
    }

    fun logAgainToday(logId: Long) {
        viewModelScope.launch {
            when (val result = logAgainTodayUseCase(logId = logId, allowBatch = false)) {
                is LogAgainTodayUseCase.Result.Success -> {
                    pendingLogAgainTodayConfirmationFlow.value = null
                    messageFlow.value = "Logged again for today."
                }

                is LogAgainTodayUseCase.Result.ConfirmationRequired -> {
                    pendingLogAgainTodayConfirmationFlow.value =
                        PendingLogAgainTodayConfirmation(
                            logId = logId,
                            message = result.message
                        )
                }

                is LogAgainTodayUseCase.Result.Blocked -> {
                    pendingLogAgainTodayConfirmationFlow.value = null
                    messageFlow.value = result.message
                }

                is LogAgainTodayUseCase.Result.Error -> {
                    pendingLogAgainTodayConfirmationFlow.value = null
                    messageFlow.value = result.message
                }
            }
        }
    }

    fun confirmLogAgainToday(logId: Long) {
        viewModelScope.launch {
            when (val result = logAgainTodayUseCase(logId = logId, allowBatch = true)) {
                is LogAgainTodayUseCase.Result.Success -> {
                    pendingLogAgainTodayConfirmationFlow.value = null
                    messageFlow.value = "Logged again for today."
                }

                is LogAgainTodayUseCase.Result.ConfirmationRequired -> {
                    pendingLogAgainTodayConfirmationFlow.value =
                        PendingLogAgainTodayConfirmation(
                            logId = logId,
                            message = result.message
                        )
                }

                is LogAgainTodayUseCase.Result.Blocked -> {
                    pendingLogAgainTodayConfirmationFlow.value = null
                    messageFlow.value = result.message
                }

                is LogAgainTodayUseCase.Result.Error -> {
                    pendingLogAgainTodayConfirmationFlow.value = null
                    messageFlow.value = result.message
                }
            }
        }
    }

    fun dismissLogAgainTodayConfirmation() {
        pendingLogAgainTodayConfirmationFlow.value = null
    }

    fun consumeMessage() {
        messageFlow.value = null
    }
}
