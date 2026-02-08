package com.example.adobongkangkong.ui.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.example.adobongkangkong.R
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.planner.model.PlannedMeal
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerDayScreen(
    state: StateFlow<PlannerDayUiState>,
    onEvent: (PlannerDayEvent) -> Unit
) {
    val s by state.collectAsState()
    val dateText = s.date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))

    Scaffold(
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
                    TextButton(onClick = { onEvent(PlannerDayEvent.PickDate) }) {
                        Text("Date")
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

                item {
                    SlotHeader(
                        slot = slot,
                        onAdd = { onEvent(PlannerDayEvent.AddMeal(slot)) }
                    )
                }

                if (meals.isEmpty()) {
                    item {
                        EmptySlotCard(onAdd = { onEvent(PlannerDayEvent.AddMeal(slot)) })
                    }
                } else {
                    items(items = meals, key = { it.id }) { meal ->
                        PlannedMealCard(meal = meal)
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
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = onPrev) { Text("<") }
        Text(dateText, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onNext) { Text(">") }
    }
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
            text = slotLabel(slot),
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
    meal: PlannedMeal
) {
    val title = meal.title?.takeIf { it.isNotBlank() } ?: "Meal"
    val itemCount = meal.items.size

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                "$itemCount item${if (itemCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToPlanBottomSheet(
    dateText: String,
    sheet: AddSheetState,
    onEvent: (PlannerDayEvent) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = { onEvent(PlannerDayEvent.DismissAddSheet) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add to plan", style = MaterialTheme.typography.titleLarge)
            Text("${slotLabel(sheet.slot)} • $dateText", style = MaterialTheme.typography.bodySmall)

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

            when {
                sheet.isCreating -> {
                    Text("Creating meal…", style = MaterialTheme.typography.bodyMedium)
                }
                sheet.createdMealId != null -> {
                    Text("Meal created.", style = MaterialTheme.typography.bodyMedium)
                    Text("MealId: ${sheet.createdMealId}", style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    Text("Create a meal container to start adding items.", style = MaterialTheme.typography.bodyMedium)
                }
            }

            TextButton(
                onClick = { onEvent(PlannerDayEvent.CreateMealIfNeeded) },
                enabled = !sheet.isCreating && sheet.createdMealId == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create meal")
            }

            TextButton(
                onClick = { /* later: open search */ },
                enabled = sheet.createdMealId != null && !sheet.isCreating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add foods/recipes (coming soon)")
            }

            TextButton(
                onClick = { /* later: add from template */ },
                enabled = sheet.createdMealId != null && !sheet.isCreating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add from template (coming soon)")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = { onEvent(PlannerDayEvent.DismissAddSheet) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Close")
                }

                TextButton(
                    onClick = { onEvent(PlannerDayEvent.CreateAnotherMeal) },
                    enabled = sheet.createdMealId != null && !sheet.isCreating,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Create another")
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun slotLabel(slot: MealSlot): String = when (slot) {
    MealSlot.BREAKFAST -> "Breakfast"
    MealSlot.LUNCH -> "Lunch"
    MealSlot.DINNER -> "Dinner"
    MealSlot.SNACK -> "Snack"
    MealSlot.CUSTOM -> "Custom"
}
