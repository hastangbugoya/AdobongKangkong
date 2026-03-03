package com.example.adobongkangkong.ui.planner

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalDate

@Composable
fun PlannerDayRoute(
    date: LocalDate,
    onBack: () -> Unit,
    onPickDate: (LocalDate) -> Unit,
    onOpenPlannedMealEditor: (Long) -> Unit,
    viewModel: PlannerDayViewModel = hiltViewModel()
) {
    LaunchedEffect(date) {
        viewModel.setDate(date)
    }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is PlannerDayViewModel.PlannerDayUiEvent.ShowToast ->
                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()

                is PlannerDayViewModel.PlannerDayUiEvent.NavigateToPlannedMealEditor ->
                    onOpenPlannedMealEditor(e.mealId)
            }
        }
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