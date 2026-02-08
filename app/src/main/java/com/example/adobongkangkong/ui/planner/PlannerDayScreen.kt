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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.planner.model.PlannedMeal
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerDayScreen(
    state: PlannerDayUiState,
    onEvent: (PlannerDayEvent) -> Unit
) {
    val dateText = state.date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Planner")
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onEvent(PlannerDayEvent.Back) }) {
                        Text("Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(PlannerDayEvent.PickDate) }) {
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

            if (state.errorMessage != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Couldn’t load plan", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(state.errorMessage, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            MealSlot.entries.forEach { slot ->
                val meals = state.mealsBySlot[slot].orEmpty()

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
                    items(
                        items = meals,
                        key = { it.id }
                    ) { meal ->
                        PlannedMealCard(
                            meal = meal,
                            onOpen = { onEvent(PlannerDayEvent.OpenMeal(meal.id)) }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
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
        Button(onClick = onPrev) { Text("<") }
        Text(dateText, style = MaterialTheme.typography.titleMedium)
        Button(onClick = onNext) { Text(">") }
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
        Button(onClick = onAdd) { Text("+ Add") }
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
            Button(onClick = onAdd) { Text("Add") }
        }
    }
}

@Composable
private fun PlannedMealCard(
    meal: PlannedMeal,
    onOpen: () -> Unit
) {
    val title = meal.title?.takeIf { it.isNotBlank() } ?: "Meal"
    val itemCount = meal.items.size

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(2.dp))
                    Text("$itemCount item${if (itemCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(onClick = onOpen) { Text("Open") }
            }
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

