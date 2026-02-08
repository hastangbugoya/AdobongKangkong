package com.example.adobongkangkong.ui.planner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalDate

@Composable
fun PlannerDayRoute(
    date: LocalDate,
    onBack: () -> Unit,
    onPickDate: (LocalDate) -> Unit,
    onNavigateToDate: (LocalDate) -> Unit,
    viewModel: PlannerDayViewModel = hiltViewModel()
) {
    LaunchedEffect(date) {
        viewModel.setDate(date)
    }

    PlannerDayScreen(
        state = viewModel.state,
        onEvent = { event ->
            when (event) {
                PlannerDayEvent.Back -> onBack()

                PlannerDayEvent.PickDate -> onPickDate(viewModel.state.value.date)

                PlannerDayEvent.PrevDay -> onNavigateToDate(viewModel.state.value.date.minusDays(1))
                PlannerDayEvent.NextDay -> onNavigateToDate(viewModel.state.value.date.plusDays(1))

                // Local-only events handled by VM/UI
                is PlannerDayEvent.AddMeal,
                PlannerDayEvent.DismissAddSheet,
                is PlannerDayEvent.UpdateAddSheetCustomLabel,
                is PlannerDayEvent.UpdateAddSheetName,
                PlannerDayEvent.CreateMealIfNeeded -> viewModel.onEvent(event)

                // Not navigating yet
                is PlannerDayEvent.OpenMeal -> {
                    // no-op for now
                }

                PlannerDayEvent.CreateAnotherMeal -> {

                }
            }
        }
    )
}
