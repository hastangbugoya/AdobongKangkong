package com.example.adobongkangkong.ui.planner

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.planner.model.QuickAddPlannedItemCandidate
import com.example.adobongkangkong.ui.log.QuickAddBottomSheet
import java.time.LocalDate

@Composable
fun PlannerDayRoute(
    date: LocalDate,
    onBack: () -> Unit,
    onPickDate: (LocalDate) -> Unit,
    onOpenPlannedMealEditor: (Long) -> Unit,
    onOpenNewPlannedMealEditor: (dateIso: String, slot: MealSlot, templateId: Long?) -> Unit,
    onOpenTemplatePicker: (slot: MealSlot?) -> Unit,
    onCreateFood: (String) -> Unit,
    onCreateFoodWithBarcode: (String) -> Unit,
    onOpenFoodEditor: (Long) -> Unit,
    templatePick: Pair<Long, MealSlot?>?,
    onTemplatePickConsumed: () -> Unit,
    viewModel: PlannerDayViewModel = hiltViewModel(),
) {
    LaunchedEffect(date) {
        viewModel.setDate(date)
    }

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

    var quickAddCandidate by remember {
        mutableStateOf<QuickAddPlannedItemCandidate?>(null)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is PlannerDayViewModel.PlannerDayUiEvent.ShowToast ->
                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()

                is PlannerDayViewModel.PlannerDayUiEvent.NavigateToPlannedMealEditor ->
                    onOpenPlannedMealEditor(e.mealId)

                is PlannerDayViewModel.PlannerDayUiEvent.NavigateToQuickAddFromPlannedItem ->
                    quickAddCandidate = e.candidate
            }
        }
    }

    PlannerDayScreen(
        state = viewModel.state,
        onEvent = { event ->
            when (event) {
                PlannerDayEvent.Back -> onBack()

                PlannerDayEvent.PickDate ->
                    onPickDate(viewModel.state.value.date)

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

                else -> viewModel.onEvent(event)
            }
        }
    )

    if (quickAddCandidate != null) {
        QuickAddBottomSheet(
            onDismiss = { quickAddCandidate = null },
            onCreateFood = onCreateFood,
            onCreateFoodWithBarcode = onCreateFoodWithBarcode,
            onOpenFoodEditor = onOpenFoodEditor,
            logDate = date,
            initialPlannedItemCandidate = quickAddCandidate,
        )
    }
}