package com.example.adobongkangkong.ui.planner

import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.planner.model.PlannedMeal
import com.example.adobongkangkong.domain.planner.model.QuickAddPlannedItemCandidate
import com.example.adobongkangkong.domain.usecase.share.CreatePlannedMealIcsFileUseCase
import com.example.adobongkangkong.ui.log.QuickAddBottomSheet
import java.time.LocalDate
import java.time.LocalTime

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
    val createIcs = remember {
        CreatePlannedMealIcsFileUseCase(context.applicationContext)
    }

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

                is PlannerDayEvent.ShareMealInvite -> {
                    shareMealInvite(
                        mealId = event.mealId,
                        viewModel = viewModel,
                        createIcs = createIcs,
                        context = context
                    )
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

private fun shareMealInvite(
    mealId: Long,
    viewModel: PlannerDayViewModel,
    createIcs: CreatePlannedMealIcsFileUseCase,
    context: android.content.Context
) {
    val state = viewModel.state.value

    val meal = state.day
        ?.mealsBySlot
        ?.values
        ?.flatten()
        ?.firstOrNull { it.id == mealId }

    if (meal == null) {
        Toast.makeText(context, "Meal not found.", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val title = meal.title?.takeIf { it.isNotBlank() } ?: meal.slot.display

        val notes = if (meal.items.isEmpty()) {
            "Planned meal from AdobongKangkong."
        } else {
            buildString {
                appendLine("Planned meal from AdobongKangkong.")
                appendLine()
                appendLine("Items:")
                meal.items.forEach {
                    val name = it.title ?: "Item"
                    appendLine("- $name")
                }
            }
        }

        val file = createIcs(
            CreatePlannedMealIcsFileUseCase.Input(
                date = state.date,
                title = title,
                notes = notes,
                startLocalTime = defaultTime(meal.slot)
            )
        )

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, notes)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Share calendar invite"))

    } catch (t: Throwable) {
        Toast.makeText(
            context,
            t.message ?: "Failed to share invite.",
            Toast.LENGTH_SHORT
        ).show()
    }
}

private fun defaultTime(slot: MealSlot): LocalTime {
    return when (slot) {
        MealSlot.BREAKFAST -> LocalTime.of(8, 0)
        MealSlot.LUNCH -> LocalTime.of(12, 0)
        MealSlot.DINNER -> LocalTime.of(18, 0)
        MealSlot.SNACK -> LocalTime.of(15, 0)
        MealSlot.CUSTOM -> LocalTime.of(12, 0)
    }
}