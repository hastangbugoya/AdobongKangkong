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
import com.example.adobongkangkong.R
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.planner.model.PlannedItem
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.planner.model.PlannedMeal
import com.example.adobongkangkong.ui.common.chevronheader.CenteredChevronHeader
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
                CenteredChevronHeader(
                    text = dateText,
                    onPrev = { onEvent(PlannerDayEvent.PrevDay) },
                    onNext = { onEvent(PlannerDayEvent.NextDay) },
                    prevIcon = painterResource(R.drawable.angle_small_left),
                    nextIcon = painterResource(R.drawable.angle_small_right),
                    prevContentDescription = "Previous day",
                    nextContentDescription = "Next day",
                    spacing = 4.dp
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = slot.display,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onEvent(PlannerDayEvent.AddMeal(slot)) }) {
                            Text("+ Add")
                        }
                    }
                }

                if (meals.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Nothing planned.", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(6.dp))
                                TextButton(onClick = { onEvent(PlannerDayEvent.AddMeal(slot)) }) { Text("Add") }
                            }
                        }
                    }
                } else {
                    items(items = meals, key = { it.id }) { meal ->
                        PlannedMealCard(
                            meal = meal,
                            onRemoveItem = { onEvent(PlannerDayEvent.RemovePlannedItem(it)) }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // ✅ Bottom sheet (this is the “there was a bottomsheet” part)
    s.addSheet?.let { sheet ->
        AddToPlanBottomSheet(
            dateText = dateText,
            sheet = sheet,
            onEvent = onEvent
        )
    }
}

@Composable
private fun PlannedMealCard(
    meal: PlannedMeal,
    onRemoveItem: (Long) -> Unit
) {
    val title = meal.title?.takeIf { it.isNotBlank() } ?: "Meal"

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)

            val items = meal.items
            val visible = items.take(3)
            val remaining = (items.size - visible.size).coerceAtLeast(0)

            Spacer(Modifier.height(6.dp))

            if (items.isEmpty()) {
                Text("No items yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    visible.forEach { item ->
                        PlannedItemRow(
                            title = itemTitle(item),
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

private fun itemTitle(item: PlannedItem): String {
    // If you added PlannedItem.title in domain, prefer it:
    val titleProp = try {
        val field = item::class.members.firstOrNull { it.name == "title" }
        (field?.call(item) as? String)?.takeIf { it.isNotBlank() }
    } catch (_: Throwable) { null }

    return titleProp ?: when (item.sourceType) {
        PlannedItemSource.FOOD -> "Food #${item.sourceId}"
        PlannedItemSource.RECIPE -> "Recipe #${item.sourceId}"
    }
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
    val rounded = kotlin.math.round(value * 10) / 10
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
            Text("${sheet.slot.display} • $dateText", style = MaterialTheme.typography.bodySmall)

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
                sheet.isCreating -> Text("Creating meal…", style = MaterialTheme.typography.bodyMedium)
                sheet.createdMealId != null -> {
                    Text("Meal created.", style = MaterialTheme.typography.bodyMedium)

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { onEvent(PlannerDayEvent.StartAddItem(AddItemMode.FOOD)) }) {
                            Text("Add food")
                        }
                    }

                    if (sheet.addItemMode == AddItemMode.FOOD) {
                        TextField(
                            value = sheet.query,
                            onValueChange = { onEvent(PlannerDayEvent.UpdateAddQuery(it)) },
                            label = { Text("Search foods") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        if (sheet.isSearching) Text("Searching…", style = MaterialTheme.typography.bodySmall)

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(sheet.results, key = { it.id }) { row ->
                                TextButton(
                                    onClick = { onEvent(PlannerDayEvent.SelectSearchResult(row.id, row.title)) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(row.title)
                                        row.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                    }
                                }
                            }
                        }

                        if (sheet.selectedRefId != null) {
                            Text("Selected: ${sheet.selectedTitle}", style = MaterialTheme.typography.bodySmall)

                            TextField(
                                value = sheet.gramsText,
                                onValueChange = { onEvent(PlannerDayEvent.UpdateAddGrams(it)) },
                                label = { Text("Grams (optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            TextField(
                                value = sheet.servingsText,
                                onValueChange = { onEvent(PlannerDayEvent.UpdateAddServings(it)) },
                                label = { Text("Servings (optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            sheet.addItemError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton(
                                    onClick = { onEvent(PlannerDayEvent.CancelAddItem) },
                                    enabled = !sheet.isAddingItem
                                ) { Text("Cancel") }

                                TextButton(
                                    onClick = { onEvent(PlannerDayEvent.ConfirmAddItem) },
                                    enabled = !sheet.isAddingItem
                                ) { Text(if (sheet.isAddingItem) "Adding…" else "Add") }
                            }
                        }
                    }
                }
                else -> Text("Create a meal container to start adding items.", style = MaterialTheme.typography.bodyMedium)
            }

            TextButton(
                onClick = { onEvent(PlannerDayEvent.CreateMealIfNeeded) },
                enabled = !sheet.isCreating && sheet.createdMealId == null,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Create meal") }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = { onEvent(PlannerDayEvent.DismissAddSheet) },
                    modifier = Modifier.weight(1f)
                ) { Text("Close") }

                TextButton(
                    onClick = { onEvent(PlannerDayEvent.CreateAnotherMeal) },
                    enabled = sheet.createdMealId != null && !sheet.isCreating,
                    modifier = Modifier.weight(1f)
                ) { Text("Create another") }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
