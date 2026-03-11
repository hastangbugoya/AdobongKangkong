package com.example.adobongkangkong.ui.planner

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.planner.model.QuickAddPlannedItemCandidate
import java.time.LocalDate

@Composable
fun PlannerDayRoute(
    date: LocalDate,
    onBack: () -> Unit,
    onPickDate: (LocalDate) -> Unit,
    onOpenPlannedMealEditor: (Long) -> Unit,
    onOpenNewPlannedMealEditor: (dateIso: String, slot: MealSlot, templateId: Long?) -> Unit,
    onOpenTemplatePicker: (slot: MealSlot?) -> Unit,
    onOpenQuickAddFromPlannedItem: (date: LocalDate, candidate: QuickAddPlannedItemCandidate) -> Unit,
    templatePick: Pair<Long, MealSlot?>?,
    onTemplatePickConsumed: () -> Unit,
    viewModel: PlannerDayViewModel = hiltViewModel(),
) {
    LaunchedEffect(date) {
        viewModel.setDate(date)
    }

    // If we have a pending pick result from the picker destination, open the NEW planned-meal editor
    // with the selected template preloaded as an in-memory draft. Saving in the editor commits it.
    LaunchedEffect(templatePick) {
        val pick = templatePick ?: return@LaunchedEffect
        val templateId = pick.first
        val overrideSlot = pick.second
        if (templateId > 0L) {
            onOpenNewPlannedMealEditor(
                date.toString(),
                overrideSlot ?: MealSlot.CUSTOM,
                templateId
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

                is PlannerDayViewModel.PlannerDayUiEvent.NavigateToQuickAddFromPlannedItem ->
                    onOpenQuickAddFromPlannedItem(
                        LocalDate.parse(e.dateIso),
                        e.candidate
                    )
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
                        event.slot,
                        null
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