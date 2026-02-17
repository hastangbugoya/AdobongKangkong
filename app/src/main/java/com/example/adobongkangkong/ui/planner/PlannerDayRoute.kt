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
    viewModel: PlannerDayViewModel = hiltViewModel()
) {
    LaunchedEffect(date) {
        viewModel.setDate(date)
    }

    PlannerDayScreen(
        state = viewModel.state,
        onEvent = { event ->
            when (event) {
                // Navigation-only events live here
                PlannerDayEvent.Back -> onBack()

                PlannerDayEvent.PickDate ->
                    onPickDate(viewModel.state.value.date)

                // ✅ Option 1: do NOT navigate; just update VM date
                PlannerDayEvent.PrevDay ->
                    viewModel.setDate(viewModel.state.value.date.minusDays(1))

                PlannerDayEvent.NextDay ->
                    viewModel.setDate(viewModel.state.value.date.plusDays(1))

                // Not navigating yet
                is PlannerDayEvent.OpenMeal -> {
                    // no-op for now
                }

                // Everything else is planner logic -> ViewModel
                else -> viewModel.onEvent(event)
            }
        }
    )
}