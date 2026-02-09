package com.example.adobongkangkong.ui.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.R
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.planner.model.PlannedItem
import com.example.adobongkangkong.domain.planner.model.PlannedMeal
import com.example.adobongkangkong.ui.common.chevronheader.CenteredChevronHeader
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.round

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerDayScreen(
    state: StateFlow<PlannerDayUiState>,
    onEvent: (PlannerDayEvent) -> Unit
) {
    val s by state.collectAsState()
    val dateText = s.date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))

    val snackbarHostState = remember { SnackbarHostState() }

    // Show Undo snackbar when requested
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
                        Text(text = dateText, style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onEvent(PlannerDayEvent.Back) }) {
                        Icon(
                            painter = painterResource(R.drawable.angle_circle_left),
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(PlannerDayEvent.PickDate) }) {
                        Icon(
                            painter = painterResource(R.drawable.tasks),
                            contentDescription = "Open date picker"
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

                item { SlotHeader(slot = slot, onAdd = { onEvent(PlannerDayEvent.AddMeal(slot)) }) }

                if (meals.isEmpty()) {
                    item { EmptySlotCard(onAdd = { onEvent(PlannerDayEvent.AddMeal(slot)) }) }
                } else {
                    items(items = meals, key = { it.id }) { meal ->
                        PlannedMealCard(
                            meal = meal,
                            onRemoveItem = { itemId -> onEvent(PlannerDayEvent.RemovePlannedItem(itemId)) },
                            onRemoveEmptyMeal = { mealId -> onEvent(PlannerDayEvent.RemoveEmptyPlannedMeal(mealId)) }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    val sheet = s.addSheet
    if (sheet != null) {
        AddToPlanBottomSheet(
            dateText = dateText,
            sheet = sheet,
            onEvent = onEvent
        )
    }
}

@Composable
private fun DateStrip(
    dateText: String,
    onPrev: () -> Unit,
    onNext: () -> Unit
){
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
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = slot.display,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onAdd) { Text("+ Add") }
    }
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
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onAdd) { Text("Add") }
        }
    }
}

@Composable
private fun PlannedMealCard(
    meal: PlannedMeal,
    onRemoveItem: (Long) -> Unit,
    onRemoveEmptyMeal: (Long) -> Unit
) {
    val title = meal.title?.takeIf { it.isNotBlank() } ?: meal.slot.display

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)

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
                    TextButton(onClick = { onRemoveEmptyMeal(meal.id) }) { Text("Remove meal") }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    visible.forEach { item ->
                        PlannedItemRow(
                            title = item.title?.takeIf { it.isNotBlank() } ?: fallbackItemTitle(item),
                            qtySummary = qtySummary(item),
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
        TextButton(onClick = onRemove) { Text("Remove") }
    }
}

private fun fallbackItemTitle(item: PlannedItem): String {
    // Minimal fallback; should rarely be seen now that titles resolve
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
private fun AddToPlanBottomSheet(
    dateText: String,
    sheet: AddSheetState,
    onEvent: (PlannerDayEvent) -> Unit
) {
    ModalBottomSheet(onDismissRequest = { onEvent(PlannerDayEvent.DismissAddSheet) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add to plan", style = MaterialTheme.typography.titleLarge)
            Text("${sheet.slot.display} • ${dateText}", style = MaterialTheme.typography.bodySmall)

            if (sheet.slot == MealSlot.CUSTOM) {
                TextField(
                    value = sheet.customLabel.orEmpty(),
                    onValueChange = { onEvent(PlannerDayEvent.UpdateAddSheetCustomLabel(it)) },
                    label = { Text("Custom slot label") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            TextField(
                value = sheet.nameOverride.orEmpty(),
                onValueChange = { onEvent(PlannerDayEvent.UpdateAddSheetName(it)) },
                label = { Text("Meal name (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (sheet.errorMessage != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Error", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(sheet.errorMessage, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { onEvent(PlannerDayEvent.CreateMealIfNeeded) }) {
                            Text("Retry create meal")
                        }
                    }
                }
            }

            // (rest of your sheet unchanged)
            // Keep your existing add-item controls here as you already had them.
            Spacer(Modifier.height(8.dp))
        }
    }
}
