package com.example.adobongkangkong.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.planner.model.PlannedDay
import com.example.adobongkangkong.domain.planner.usecase.CreatePlannedMealUseCase
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedDayUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlannerDayViewModel @Inject constructor(
    private val observePlannedDay: ObservePlannedDayUseCase,
    private val createPlannedMeal: CreatePlannedMealUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(PlannerDayUiState(date = LocalDate.now()))
    val state: StateFlow<PlannerDayUiState> = _state.asStateFlow()

    private var observeJob: Job? = null

    fun setDate(date: LocalDate) {
        if (_state.value.date == date && observeJob != null) return

        _state.update { it.copy(date = date, isLoading = true, errorMessage = null) }

        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            try {
                // Your use case expects ISO string
                observePlannedDay(date.toString()).collect { plannedDay ->
                    _state.update { it.copy(isLoading = false, day = plannedDay, errorMessage = null) }
                }
            } catch (t: Throwable) {
                _state.update { it.copy(isLoading = false, errorMessage = t.message ?: "Failed to load plan") }
            }
        }
    }

    fun onEvent(event: PlannerDayEvent) {
        when (event) {
            PlannerDayEvent.Back,
            PlannerDayEvent.PickDate,
            PlannerDayEvent.PrevDay,
            PlannerDayEvent.NextDay,
            is PlannerDayEvent.OpenMeal -> {
                // Route handles these (or they are no-ops for now)
            }

            is PlannerDayEvent.AddMeal -> {
                openAddSheet(slot = event.slot)
            }

            PlannerDayEvent.DismissAddSheet -> {
                _state.update { it.copy(addSheet = null) }
            }

            is PlannerDayEvent.UpdateAddSheetName -> {
                _state.update { s ->
                    val sheet = s.addSheet ?: return@update s
                    s.copy(addSheet = sheet.copy(nameOverride = event.value))
                }
            }

            is PlannerDayEvent.UpdateAddSheetCustomLabel -> {
                _state.update { s ->
                    val sheet = s.addSheet ?: return@update s
                    s.copy(addSheet = sheet.copy(customLabel = event.value))
                }
            }

            PlannerDayEvent.CreateMealIfNeeded -> {
                createMealIfNeeded()
            }

            PlannerDayEvent.CreateAnotherMeal -> {
                val sheet = _state.value.addSheet ?: return
                // Reset just the creation result; keep the user's text fields
                _state.update { s ->
                    val current = s.addSheet ?: return@update s
                    s.copy(addSheet = current.copy(isCreating = false, createdMealId = null, errorMessage = null))
                }
                createMealIfNeeded()
            }
        }
    }

    private fun openAddSheet(slot: MealSlot) {
        _state.update {
            it.copy(
                addSheet = AddSheetState(
                    slot = slot,
                    isCreating = false,
                    createdMealId = null,
                    customLabel = if (slot == MealSlot.CUSTOM) "" else null,
                    nameOverride = ""
                ),
                errorMessage = null
            )
        }
        // IMPORTANT: do not auto-create here anymore.
        // User will press "Create meal" in the sheet.
    }

    private fun createMealIfNeeded() {
        val current = _state.value
        val sheet = current.addSheet ?: return

        if (sheet.isCreating || sheet.createdMealId != null) return

        _state.update { it.copy(addSheet = sheet.copy(isCreating = true, errorMessage = null)) }

        viewModelScope.launch {
            try {
                val dateIso = current.date.toString()

                val customLabel: String? =
                    if (sheet.slot == MealSlot.CUSTOM) sheet.customLabel?.trim().takeUnless { it.isNullOrBlank() }
                    else null

                val nameOverride: String? =
                    sheet.nameOverride?.trim().takeUnless { it.isNullOrBlank() }

                val mealId = createPlannedMeal(
                    dateIso = dateIso,
                    slot = sheet.slot,
                    customLabel = customLabel,
                    nameOverride = nameOverride,
                    sortOrder = null
                )

                _state.update { s ->
                    val sSheet = s.addSheet
                    if (sSheet == null) s
                    else s.copy(addSheet = sSheet.copy(isCreating = false, createdMealId = mealId))
                }
            } catch (t: Throwable) {
                _state.update { s ->
                    val sSheet = s.addSheet
                    if (sSheet == null) s.copy(errorMessage = t.message ?: "Failed to create meal")
                    else s.copy(addSheet = sSheet.copy(isCreating = false, errorMessage = t.message ?: "Failed to create meal"))
                }
            }
        }
    }
}



