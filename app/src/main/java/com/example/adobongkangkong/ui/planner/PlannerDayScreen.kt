package com.example.adobongkangkong.ui.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.R
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.model.MacroTotals
import com.example.adobongkangkong.domain.planner.model.PlannedItem
import com.example.adobongkangkong.domain.planner.model.PlannedMeal
import com.example.adobongkangkong.ui.common.chevronheader.CenteredChevronHeader
import com.example.adobongkangkong.ui.theme.AppIconSize
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.round
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerDayScreen(
    state: StateFlow<PlannerDayUiState>,
    onEvent: (PlannerDayEvent) -> Unit
) {
    val s by state.collectAsState()
    val dateText = s.date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))

    val snackbarHostState = remember { SnackbarHostState() }

    val undo = s.undo
    LaunchedEffect(undo?.id) {
        val u = undo ?: return@LaunchedEffect

        val result = snackbarHostState.showSnackbar(
            message = u.message,
            actionLabel = "Undo"
        )

        if (result == SnackbarResult.ActionPerformed) {
            onEvent(PlannerDayEvent.UndoRemovePlannedItem(u.id))
        }
        onEvent(PlannerDayEvent.UndoSnackbarConsumed(u.id))
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Planner")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onEvent(PlannerDayEvent.Back) }) {
                        Icon(
                            painter = painterResource(R.drawable.angle_circle_left),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DateStrip(
                    dateText = dateText,
                    onPrev = { onEvent(PlannerDayEvent.PrevDay) },
                    onNext = { onEvent(PlannerDayEvent.NextDay) }
                )
            }

            item {
                val dayTotals = s.dayMacroTotals
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Day total",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(macrosLine(dayTotals), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (s.errorMessage != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Couldn’t load plan", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(s.errorMessage ?: "", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            MealSlot.entries.forEach { slot ->
                val meals = s.mealsBySlot[slot].orEmpty()
                val loggedNames = s.loggedNamesBySlot[slot].orEmpty()

                item {
                    SlotHeader(
                        slot = slot,
                        loggedBannerText = buildLoggedBannerText(loggedNames),
                        onAdd = { onEvent(PlannerDayEvent.AddMeal(slot)) }
                    )
                }

                if (meals.isEmpty()) {
                    item {
                        EmptySlotCard(
                            onAdd = { onEvent(PlannerDayEvent.AddMeal(slot)) }
                        )
                    }
                } else {
                    items(items = meals, key = { it.id }) { meal ->
                        PlannedMealCard(
                            meal = meal,
                            mealTotals = s.mealMacroTotals[meal.id],
                            isRecurring = meal.seriesId != null,
                            onOpenMeal = { mealId -> onEvent(PlannerDayEvent.OpenMeal(mealId)) },
                            onMakeRecurring = { mealId -> onEvent(PlannerDayEvent.MakeMealRecurring(mealId)) },
                            onRemoveItem = { itemId -> onEvent(PlannerDayEvent.RemovePlannedItem(itemId)) },
                            onRemoveEmptyMeal = { mealId -> onEvent(PlannerDayEvent.RemoveEmptyPlannedMeal(mealId)) },
                            onDuplicateMeal = { mealId -> onEvent(PlannerDayEvent.DuplicateMeal(mealId)) },
                            onLogMeal = { mealId -> onEvent(PlannerDayEvent.LogMeal(mealId)) },
                            onLogItem = { itemId -> onEvent(PlannerDayEvent.LogPlannedItem(itemId)) },
                            onSaveMealAsTemplate = { mealId -> onEvent(PlannerDayEvent.SaveMealAsTemplate(mealId)) }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onEvent(PlannerDayEvent.DebugCreateSampleSeries) }) {
                        Text("Debug: Sample Series")
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    val addSheet = s.addSheet
    if (addSheet != null) {
        AddMealChoiceBottomSheet(
            slot = addSheet.slot,
            dateText = dateText,
            onDismiss = { onEvent(PlannerDayEvent.DismissAddSheet) },
            onEmptyMeal = { onEvent(PlannerDayEvent.OpenMealPlanner(addSheet.slot)) },
            onFromTemplate = { onEvent(PlannerDayEvent.OpenTemplatePicker(addSheet.slot)) }
        )
    }

    val dupSheet = s.duplicateSheet
    if (dupSheet != null) {
        DuplicateMealBottomSheet(
            sheet = dupSheet,
            onEvent = onEvent
        )
    }

    val recurringSheet = s.recurringEditor
    if (recurringSheet != null) {
        RecurringMealBottomSheet(
            sheet = recurringSheet,
            onEvent = onEvent
        )
    }
}

@Composable
private fun DateStrip(
    dateText: String,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    CenteredChevronHeader(
        text = dateText,
        onPrev = onPrev,
        onNext = onNext,
        prevIcon = painterResource(R.drawable.angle_small_left),
        nextIcon = painterResource(R.drawable.angle_small_right),
        prevContentDescription = "Previous day",
        nextContentDescription = "Next day",
        spacing = 4.dp
    )
}

@Composable
private fun SlotHeader(
    slot: MealSlot,
    loggedBannerText: String?,
    onAdd: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = slot.display,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onAdd) {
                Icon(
                    painter = painterResource(R.drawable.add),
                    contentDescription = "Add meal",
                    modifier = Modifier.size(AppIconSize.CardAction)
                )
            }
        }

        if (!loggedBannerText.isNullOrBlank()) {
            Text(
                text = loggedBannerText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private fun buildLoggedBannerText(
    loggedNames: List<String>
): String? {
    if (loggedNames.isEmpty()) return null

    val visibleNames = loggedNames.take(3)
    val remaining = (loggedNames.size - visibleNames.size).coerceAtLeast(0)

    val suffix = if (remaining > 0) {
        visibleNames.joinToString(", ") + " +$remaining"
    } else {
        visibleNames.joinToString(", ")
    }

    val itemWord = if (loggedNames.size == 1) "item" else "items"
    return "${loggedNames.size} $itemWord logged: $suffix"
}

@Composable
private fun EmptySlotCard(
    onAdd: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Nothing planned.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Add a meal to plan foods/recipes for this slot.",
                style = MaterialTheme.typography.bodySmall
            )
//            Spacer(Modifier.height(10.dp))
//            TextButton(onClick = onAdd) { Text("Add") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMealChoiceBottomSheet(
    slot: MealSlot,
    dateText: String,
    onDismiss: () -> Unit,
    onEmptyMeal: () -> Unit,
    onFromTemplate: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Plan a meal",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = "${slot.display} • $dateText",
                style = MaterialTheme.typography.bodySmall
            )

            TextButton(
                onClick = onEmptyMeal,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Empty meal")
            }

            TextButton(
                onClick = onFromTemplate,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("From template")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringMealBottomSheet(
    sheet: RecurringEditorState,
    onEvent: (PlannerDayEvent) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onEvent(PlannerDayEvent.DismissRecurringEditor) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Make recurring",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Frequency",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.clickable { onEvent(PlannerDayEvent.UpdateRecurringFrequency(RecurrenceFrequencyUi.DAILY)) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = sheet.frequency == RecurrenceFrequencyUi.DAILY,
                        onClick = { onEvent(PlannerDayEvent.UpdateRecurringFrequency(RecurrenceFrequencyUi.DAILY)) }
                    )
                    Text("Daily")
                }
                Row(
                    modifier = Modifier.clickable { onEvent(PlannerDayEvent.UpdateRecurringFrequency(RecurrenceFrequencyUi.WEEKLY)) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = sheet.frequency == RecurrenceFrequencyUi.WEEKLY,
                        onClick = { onEvent(PlannerDayEvent.UpdateRecurringFrequency(RecurrenceFrequencyUi.WEEKLY)) }
                    )
                    Text("Weekly")
                }
            }

            Text(
                text = "Details",
                style = MaterialTheme.typography.bodyMedium
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sheet.rules.forEach { rule ->
                    RecurringDayRuleRow(
                        rule = rule,
                        anchorWeekday = sheet.anchorWeekday,
                        frequency = sheet.frequency,
                        onToggle = { enabled ->
                            onEvent(PlannerDayEvent.ToggleRecurringWeekday(rule.weekday, enabled))
                        },
                        onSlotSelected = { slot ->
                            onEvent(PlannerDayEvent.UpdateRecurringWeekdaySlot(rule.weekday, slot))
                        }
                    )
                }
            }

            sheet.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onEvent(PlannerDayEvent.DismissRecurringEditor) },
                    enabled = !sheet.isSaving
                ) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { onEvent(PlannerDayEvent.ConfirmMakeRecurring) },
                    enabled = !sheet.isSaving
                ) {
                    if (sheet.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(18.dp)
                                .height(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Save")
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RecurringDayRuleRow(
    rule: RecurringDayRuleUiState,
    anchorWeekday: Int,
    frequency: RecurrenceFrequencyUi,
    onToggle: (Boolean) -> Unit,
    onSlotSelected: (MealSlot) -> Unit,
) {
    val enabled = if (frequency == RecurrenceFrequencyUi.DAILY) true else rule.isEnabled
    val isAnchor = rule.weekday == anchorWeekday
    var showSlotMenu by remember(rule.weekday, rule.slot, enabled, frequency) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(40.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val toggleEnabled = frequency == RecurrenceFrequencyUi.WEEKLY && !isAnchor

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(
                        width = 2.dp,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        },
                        shape = RoundedCornerShape(4.dp)
                    )
                    .background(
                        if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else Color.Transparent
                    )
                    .clickable(
                        enabled = toggleEnabled,
                        onClick = { onToggle(!enabled) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (enabled) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Box(
            modifier = Modifier.width(64.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = weekdayShortLabel(rule.weekday),
                maxLines = 1
            )
        }

        Box(
            modifier = Modifier.width(88.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (isAnchor) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = "Anchor",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier.width(112.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box {
                TextButton(
                    onClick = { showSlotMenu = true },
                    enabled = enabled && !isAnchor
                ) {
                    Text(rule.slot.display)
                }

                DropdownMenu(
                    expanded = showSlotMenu,
                    onDismissRequest = { showSlotMenu = false }
                ) {
                    MealSlot.entries.forEach { slot ->
                        DropdownMenuItem(
                            text = { Text(slot.display) },
                            onClick = {
                                showSlotMenu = false
                                onSlotSelected(slot)
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun weekdayShortLabel(weekday: Int): String = when (weekday) {
    1 -> "Mon"
    2 -> "Tue"
    3 -> "Wed"
    4 -> "Thu"
    5 -> "Fri"
    6 -> "Sat"
    7 -> "Sun"
    else -> "Day $weekday"
}

@Composable
private fun PlannedMealCard(
    meal: PlannedMeal,
    mealTotals: MacroTotals?,
    isRecurring: Boolean,
    onOpenMeal: (Long) -> Unit,
    onMakeRecurring: (Long) -> Unit,
    onRemoveItem: (Long) -> Unit,
    onRemoveEmptyMeal: (Long) -> Unit,
    onDuplicateMeal: (Long) -> Unit,
    onLogMeal: (Long) -> Unit,
    onLogItem: (Long) -> Unit,
    onSaveMealAsTemplate: (Long) -> Unit
) {
    val title = meal.title?.takeIf { it.isNotBlank() } ?: meal.slot.display
    var showActionsMenu by remember(meal.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenMeal(meal.id) }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )

                if (isRecurring) {
                    Icon(
                        painter = painterResource(R.drawable.rotate_reverse),
                        contentDescription = "Recurring",
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(AppIconSize.CardAction)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    IconButton(onClick = { showActionsMenu = true }) {
                        Icon(
                            painter = painterResource(R.drawable.menu_dots_vertical),
                            contentDescription = "Meal actions",
                            modifier = Modifier.size(AppIconSize.CardAction)
                        )
                    }
                    DropdownMenu(
                        expanded = showActionsMenu,
                        onDismissRequest = { showActionsMenu = false }
                    ) {
                        if (!isRecurring) {
                            DropdownMenuItem(
                                text = { Text("Make recurring") },
                                onClick = {
                                    showActionsMenu = false
                                    onMakeRecurring(meal.id)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Save as template") },
                            onClick = {
                                showActionsMenu = false
                                onSaveMealAsTemplate(meal.id)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            onClick = {
                                showActionsMenu = false
                                onDuplicateMeal(meal.id)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Log meal") },
                            onClick = {
                                showActionsMenu = false
                                onLogMeal(meal.id)
                            }
                        )
                    }
                }
            }

            if (mealTotals != null) {
                Text(
                    macrosLine(mealTotals),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(6.dp))
            } else {
                Spacer(Modifier.height(6.dp))
            }

            val items = meal.items
            val visible = items.take(3)
            val remaining = (items.size - visible.size).coerceAtLeast(0)

            Spacer(Modifier.height(6.dp))

            if (items.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "No items yet.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )

                    TextButton(onClick = { onRemoveEmptyMeal(meal.id) }) {
                        Text("Remove meal")
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    visible.forEach { item ->
                        PlannedItemRow(
                            title = item.title?.takeIf { it.isNotBlank() } ?: fallbackItemTitle(item),
                            qtySummary = qtySummary(item),
                            onLog = { onLogItem(item.id) },
                            onRemove = { onRemoveItem(item.id) }
                        )
                    }
                    if (remaining > 0) {
                        Text("+$remaining more", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlannedItemRow(
    title: String,
    qtySummary: String,
    onLog: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(qtySummary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onLog) {
            Icon(
                painter = painterResource(R.drawable.log_file),
                contentDescription = "Log",
                modifier = Modifier.size(AppIconSize.CardAction)
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                painter = painterResource(R.drawable.trash),
                contentDescription = "Remove",
                modifier = Modifier.size(AppIconSize.CardAction)
            )
        }
    }
}

private fun fallbackItemTitle(item: PlannedItem): String {
    return "${item.sourceType.name.lowercase().replaceFirstChar { it.uppercase() }} #${item.sourceId}"
}

private fun qtySummary(item: PlannedItem): String {
    val parts = buildList {
        item.qtyGrams?.let { add("${formatNumber(it)} g") }
        item.qtyServings?.let {
            val label = if (it == 1.0) "serving" else "servings"
            add("${formatNumber(it)} $label")
        }
    }
    return if (parts.isEmpty()) "Qty not set" else parts.joinToString(" • ")
}

private fun formatNumber(value: Double): String {
    val rounded = round(value * 10) / 10
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else String.format("%.1f", rounded)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DuplicateMealBottomSheet(
    sheet: DuplicateSheetState,
    onEvent: (PlannerDayEvent) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { onEvent(PlannerDayEvent.DismissDuplicateSheet) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Duplicate meal", style = MaterialTheme.typography.titleLarge)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onEvent(PlannerDayEvent.DuplicateAddToday) }) { Text("Today") }
                TextButton(onClick = { onEvent(PlannerDayEvent.DuplicateAddTomorrow) }) { Text("Tomorrow") }
                TextButton(onClick = { showPicker = true }) { Text("Pick date…") }
            }

            if (sheet.selectedDates.isEmpty()) {
                Text("Select at least one date.", style = MaterialTheme.typography.bodySmall)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sheet.selectedDates.forEach { d ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(d.toString(), style = MaterialTheme.typography.bodyMedium)
                            TextButton(
                                onClick = { onEvent(PlannerDayEvent.DuplicateRemoveDate(d.toString())) }
                            ) { Text("Remove") }
                        }
                    }
                }
            }

            sheet.errorMessage?.let { msg ->
                Text(msg, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = sheet.selectedDates.isNotEmpty() && !sheet.isDuplicating,
                onClick = { onEvent(PlannerDayEvent.ConfirmDuplicateDates) }
            ) {
                Text(if (sheet.isDuplicating) "Saving duplicate(s)…" else "Save duplicate(s)")
            }
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = pickerState.selectedDateMillis
                        if (millis != null) {
                            val picked = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            onEvent(PlannerDayEvent.DuplicateAddDate(picked.toString()))
                        }
                        showPicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun macrosLine(m: MacroTotals): String {
    val kcal = m.caloriesKcal.roundToInt()
    val p = m.proteinG.roundToInt()
    val c = m.carbsG.roundToInt()
    val f = m.fatG.roundToInt()
    return "$kcal kcal • P $p • C $c • F $f"
}