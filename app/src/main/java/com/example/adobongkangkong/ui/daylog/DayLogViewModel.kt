package com.example.adobongkangkong.ui.daylog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.model.DailyNutritionTotals
import com.example.adobongkangkong.domain.usecase.DeleteLogEntryUseCase
import com.example.adobongkangkong.domain.usecase.ObserveDailyNutritionTotalsUseCase
import com.example.adobongkangkong.ui.daylog.model.DayLogRow
import com.example.adobongkangkong.ui.daylog.usecase.ObserveDayLogRowsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class DayLogViewModel @Inject constructor(
    private val observeDayLogRows: ObserveDayLogRowsUseCase,
    private val observeDailyTotals: ObserveDailyNutritionTotalsUseCase,
    private val deleteLogEntry: DeleteLogEntryUseCase,
    private val zoneId: ZoneId
) : ViewModel() {

    private val selectedDate = MutableStateFlow<LocalDate?>(null)

    val entries: StateFlow<List<DayLogRow>> =
        selectedDate
            .filterNotNull()
            .flatMapLatest { date -> observeDayLogRows(date) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totals: StateFlow<DailyNutritionTotals?> =
        selectedDate
            .filterNotNull()
            .flatMapLatest { date -> observeDailyTotals(date, zoneId) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(date: LocalDate) {
        selectedDate.value = date
    }

    fun deleteEntry(logId: Long) {
        viewModelScope.launch {
            deleteLogEntry(logId)
            // no manual refresh needed; flows update from Room invalidation
        }
    }
}
