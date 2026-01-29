package com.example.adobongkangkong.ui.daylog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.usecase.ObserveDailyNutritionTotalsUseCase
import com.example.adobongkangkong.ui.daylog.model.DayLogRow
import com.example.adobongkangkong.ui.daylog.usecase.ObserveDayLogRowsUseCase
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class DayLogViewModel @Inject constructor(
    private val observeDayLogRows: ObserveDayLogRowsUseCase,
    private val observeDailyTotals: ObserveDailyNutritionTotalsUseCase,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val _date = MutableStateFlow<LocalDate?>(null)

    val logRows: StateFlow<List<DayLogRow>> =
        _date.filterNotNull()
            .flatMapLatest { date ->
                observeDayLogRows(date, zoneId)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totals =
        _date.filterNotNull()
            .flatMapLatest { date ->
                observeDailyTotals(date, zoneId)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(date: LocalDate) {
        _date.value = date
    }
}
