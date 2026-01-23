package com.example.adobongkangkong.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.usecase.ObserveTodayMacrosUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeTodayMacrosUseCase: ObserveTodayMacrosUseCase
) : ViewModel() {

    val state: StateFlow<DashboardState> =
        observeTodayMacrosUseCase()
            .map { totals -> DashboardState(totals = totals) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())
}
