package com.example.adobongkangkong.ui.planner

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalDate

@Composable
fun PlannerDayRoute(
    date: LocalDate,
    onBack: () -> Unit,
    onPickDate: (LocalDate) -> Unit,
    onNavigateToDate: (LocalDate) -> Unit,
    onAddToPlan: (date: LocalDate, slot: com.example.adobongkangkong.data.local.db.entity.MealSlot) -> Unit,
    onOpenMeal: (mealId: Long) -> Unit,
    viewModel: PlannerDayViewModel = hiltViewModel()
) {
    PlannerDayScreen(
        state = viewModel.state,
        onEvent = { event ->
            when (event) {
                PlannerDayEvent.Back -> onBack()

                PlannerDayEvent.PickDate -> onPickDate(viewModel.state.date)

                PlannerDayEvent.PrevDay -> onNavigateToDate(viewModel.state.date.minusDays(1))
                PlannerDayEvent.NextDay -> onNavigateToDate(viewModel.state.date.plusDays(1))

                is PlannerDayEvent.AddMeal -> onAddToPlan(viewModel.state.date, event.slot)

                is PlannerDayEvent.OpenMeal -> onOpenMeal(event.mealId)
                PlannerDayEvent.DismissAddSheet -> { }
            }
        }
    )

    // Keep VM in sync with the route date (supports prev/next/picker navigation).
    viewModel.setDate(date)
}
