package com.example.adobongkangkong.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.planner.model.PlannedDay
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedDayUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlannerDayViewModel @Inject constructor(
    private val observePlannedDay: ObservePlannedDayUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(PlannerDayUiState(date = LocalDate.now()))
    val state: PlannerDayUiState get() = _state.value

    private var observeJob: Job? = null

    fun onEvent(event: PlannerDayEvent) {
        when (event) {
            is PlannerDayEvent.AddMeal ->
                _state.update { it.copy(addSheetSlot = event.slot) }

            PlannerDayEvent.DismissAddSheet ->
                _state.update { it.copy(addSheetSlot = null) }

            else -> Unit
        }
    }

    fun setDate(date: LocalDate) {
        if (_state.value.date == date && observeJob != null) return

        _state.update { it.copy(date = date, isLoading = true, errorMessage = null) }

        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            try {
                // If your use case signature is invoke(LocalDate), replace this line with:
                // observePlannedDay(date).collect { ... }
                observePlannedDay(date.toString()).collect { plannedDay ->
                    _state.update { it.copy(isLoading = false, day = plannedDay, errorMessage = null) }
                }
            } catch (t: Throwable) {
                _state.update { it.copy(isLoading = false, errorMessage = t.message ?: "Failed to load plan") }
            }
        }
    }
}


