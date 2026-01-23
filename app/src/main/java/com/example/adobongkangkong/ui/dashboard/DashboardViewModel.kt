package com.example.adobongkangkong.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.usecase.DeleteLogEntryUseCase
import com.example.adobongkangkong.domain.usecase.ObserveTodayLogItemsUseCase
import com.example.adobongkangkong.domain.usecase.ObserveTodayMacrosUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeTodayMacrosUseCase: ObserveTodayMacrosUseCase,
    observeTodayLogItemsUseCase: ObserveTodayLogItemsUseCase,
    private val deleteLogEntry: DeleteLogEntryUseCase
) : ViewModel() {

//    val state: StateFlow<DashboardState> =
//        observeTodayMacrosUseCase()
//            .map { totals -> DashboardState(totals = totals) }
//            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    val state: StateFlow<DashboardState> =
        combine(
            observeTodayMacrosUseCase(),
            observeTodayLogItemsUseCase()
        ) { totals, items ->
            DashboardState(
                totals = totals,
                todayItems = items
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    fun delete(logId: Long) {
        viewModelScope.launch { deleteLogEntry(logId) }
    }
}
