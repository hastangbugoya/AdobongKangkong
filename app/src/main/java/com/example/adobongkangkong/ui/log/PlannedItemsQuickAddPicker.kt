package com.example.adobongkangkong.ui.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.planner.model.QuickAddPlannedItemCandidate

@Composable
fun PlannedItemsQuickAddPicker(
    sections: Map<MealSlot, List<QuickAddPlannedItemCandidate>>,
    onItemSelected: (QuickAddPlannedItemCandidate) -> Unit
) {

    LazyColumn {

        sections.forEach { (slot, itemsForSlot) ->

            item {
                Text(
                    text = slot.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }

            items(itemsForSlot) { item ->

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onItemSelected(item) }
                        .padding(12.dp)
                ) {

                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    val subtitle =
                        item.plannedServings?.let { "$it servings" }
                            ?: item.plannedGrams?.let { "$it g" }

                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}