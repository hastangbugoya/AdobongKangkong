package com.example.adobongkangkong.ui.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.planner.model.QuickAddPlannedItemCandidate

@Composable
fun PlannedItemsQuickAddPicker(
    sections: Map<MealSlot, List<QuickAddPlannedItemCandidate>>,
    onItemSelected: (QuickAddPlannedItemCandidate) -> Unit,
    onLogMeal: (Long) -> Unit,
) {
    val groupedSections = sections.mapValues { (_, items) ->
        groupItemsByPlannedMeal(items)
    }

    Text(
        text = "Planned items for the day",
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth(),
        style = MaterialTheme.typography.titleMedium
    )

    if (sections.values.all { it.isEmpty() }) {
        Text(
            text = "No planned items for this day.",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    LazyColumn {
        groupedSections.forEach { (slot, mealGroups) ->
            if (mealGroups.isNotEmpty()) {
                item(key = "slot-${slot.name}") {
                    Text(
                        text = slot.display,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                items(
                    items = mealGroups,
                    key = { it.key }
                ) { group ->
                    PlannedMealQuickAddGroup(
                        group = group,
                        onItemSelected = onItemSelected,
                        onLogMeal = onLogMeal
                    )

                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PlannedMealQuickAddGroup(
    group: QuickAddPlannedMealGroup,
    onItemSelected: (QuickAddPlannedItemCandidate) -> Unit,
    onLogMeal: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    text = "${group.items.size} planned ${if (group.items.size == 1) "item" else "items"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            group.mealId?.let { mealId ->
                TextButton(
                    onClick = { onLogMeal(mealId) }
                ) {
                    Text("Log meal")
                }
            }
        }

        group.items.forEach { item ->
            PlannedItemQuickAddRow(
                item = item,
                onClick = { onItemSelected(item) }
            )
        }
    }
}

@Composable
private fun PlannedItemQuickAddRow(
    item: QuickAddPlannedItemCandidate,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge
        )

        val subtitle = plannedItemSubtitle(item)
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class QuickAddPlannedMealGroup(
    val key: String,
    val mealId: Long?,
    val title: String,
    val items: List<QuickAddPlannedItemCandidate>,
)

private fun groupItemsByPlannedMeal(
    items: List<QuickAddPlannedItemCandidate>
): List<QuickAddPlannedMealGroup> {
    val grouped = linkedMapOf<String, MutableList<QuickAddPlannedItemCandidate>>()

    items.forEach { item ->
        val key = item.plannedMealId?.let { "meal-$it" } ?: "item-${item.id}"
        grouped.getOrPut(key) { mutableListOf() } += item
    }

    return grouped.map { (key, groupedItems) ->
        val first = groupedItems.first()

        QuickAddPlannedMealGroup(
            key = key,
            mealId = first.plannedMealId,
            title = first.plannedMealTitle
                ?.takeIf { it.isNotBlank() }
                ?: first.slot.display,
            items = groupedItems
        )
    }
}

private fun plannedItemSubtitle(
    item: QuickAddPlannedItemCandidate
): String? {
    return item.plannedServings?.let { servings ->
        "${servings.clean()} ${if (servings == 1.0) "serving" else "servings"}"
    } ?: item.plannedGrams?.let { grams ->
        "${grams.clean()} g"
    }
}

private fun Double.clean(): String {
    return if (this % 1.0 == 0.0) {
        this.toLong().toString()
    } else {
        "%.1f".format(this)
    }
}