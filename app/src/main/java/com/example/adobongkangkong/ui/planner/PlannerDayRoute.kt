package com.example.adobongkangkong.ui.planner

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import java.time.LocalDate

@Composable
fun PlannerDayRoute(
    date: LocalDate,
    onBack: () -> Unit,
    onPickDate: (LocalDate) -> Unit,
    onOpenPlannedMealEditor: (Long) -> Unit,
    onOpenNewPlannedMealEditor: (dateIso: String, slot: MealSlot) -> Unit,
    onOpenTemplatePicker: (slot: MealSlot?) -> Unit,
    templatePick: Pair<Long, MealSlot?>?,
    onTemplatePickConsumed: () -> Unit,
    viewModel: PlannerDayViewModel = hiltViewModel(),
) {
    LaunchedEffect(date) {
        viewModel.setDate(date)
    }

    // If we have a pending pick result from the picker destination, translate it into a VM event.
    LaunchedEffect(templatePick) {
        val pick = templatePick ?: return@LaunchedEffect
        val templateId = pick.first
        val overrideSlot = pick.second
        if (templateId > 0L) {
            viewModel.onEvent(
                PlannerDayEvent.CreateMealFromTemplate(
                    templateId = templateId,
                    overrideSlot = overrideSlot
                )
            )
        }
        onTemplatePickConsumed()
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

                // Date change lives in VM
                PlannerDayEvent.PrevDay ->
                    viewModel.setDate(viewModel.state.value.date.minusDays(1))

                PlannerDayEvent.NextDay ->
                    viewModel.setDate(viewModel.state.value.date.plusDays(1))

                is PlannerDayEvent.OpenTemplatePicker ->
                    onOpenTemplatePicker(event.slot)

                is PlannerDayEvent.OpenMealPlanner ->
                    onOpenNewPlannedMealEditor(
                        viewModel.state.value.date.toString(),
                        event.slot
                    )

                is PlannerDayEvent.OpenMeal -> {
                    onOpenPlannedMealEditor(event.mealId)
                }

                // Everything else is planner logic -> ViewModel
                else -> viewModel.onEvent(event)
            }
        }
    )
}