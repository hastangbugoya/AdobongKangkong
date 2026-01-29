package com.example.adobongkangkong.ui.heatmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.model.DailyNutritionTotals
import com.example.adobongkangkong.domain.model.LogEntry
import com.example.adobongkangkong.domain.usecase.ObserveDailyNutritionTotalsUseCase
import com.example.adobongkangkong.ui.heatmap.usecase.ObserveLogEntriesForDateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class DayLogViewModel @Inject constructor(
    private val observeLogsForDate: ObserveLogEntriesForDateUseCase,
    private val observeDailyTotals: ObserveDailyNutritionTotalsUseCase
) : ViewModel() {

    private val _date = MutableStateFlow<LocalDate?>(null)

    val entries: StateFlow<List<LogEntry>> =
        _date.filterNotNull()
            .flatMapLatest { date ->
                observeLogsForDate(date, ZoneId.systemDefault())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totals: StateFlow<DailyNutritionTotals?> =
        _date.filterNotNull()
            .flatMapLatest { date ->
                observeDailyTotals(date, ZoneId.systemDefault())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(date: LocalDate) {
        _date.value = date
    }
}
