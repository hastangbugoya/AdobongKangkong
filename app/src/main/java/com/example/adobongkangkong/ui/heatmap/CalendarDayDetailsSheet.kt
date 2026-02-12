package com.example.adobongkangkong.ui.heatmap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.planner.model.PlannedDay
import com.example.adobongkangkong.domain.planner.model.PlannedMeal
import com.example.adobongkangkong.ui.heatmap.model.HeatmapDay
import java.time.LocalDate

@Composable
fun CalendarDayDetailsSheet(
    heatmapDay: HeatmapDay,
    nutrientDisplayName: String?,
    nutrientUnit: String?,
    plannedDay: PlannedDay?, // null => show heatmap-only behavior
    onViewLogs: (LocalDate) -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
    onOpenPlannerDay: (LocalDate) -> Unit,
) {

    Column(modifier = Modifier.fillMaxWidth()) {
        // Existing content (unchanged)
        HeatmapDayDetailsSheet(
            day = heatmapDay,
            nutrientDisplayName = nutrientDisplayName,
            nutrientUnit = nutrientUnit,
            onViewLogs = onViewLogs,
            onShare = onShare,
            onClose = onClose
        )

        // Compact planner section (only if needed)
        if (plannedDay != null) {
            Spacer(Modifier.height(10.dp))
            PlannedMealsCompactSection(
                plannedDay = plannedDay,
                onOpenPlannerDay = { onOpenPlannerDay(plannedDay.date) }
            )
        }
    }
}

@Composable
private fun PlannedMealsCompactSection(
    plannedDay: PlannedDay,
    onOpenPlannerDay: () -> Unit
) {
    val (mealCount, slotsText) = plannedSummary(plannedDay)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 18.dp)
    ) {
        Text(
            text = "Planned meals",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))

        Text(
            text = "Meals: $mealCount" + if (slotsText.isNotBlank()) " ($slotsText)" else "",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = onOpenPlannerDay) {
                Text("Open planner")
            }
        }
    }
}

private fun plannedSummary(plannedDay: PlannedDay): Pair<Int, String> {
    val map = plannedDay.mealsBySlot
    val mealCount = map.values.sumOf { it.size }

    // Short labels: B/L/D/S/C
    val slots = map.entries
        .filter { it.value.isNotEmpty() }
        .map { (slot, _) -> slotShort(slot) }

    val slotsText = slots.joinToString(separator = ", ")
    return mealCount to slotsText
}

private fun slotShort(slot: MealSlot): String = when (slot) {
    MealSlot.BREAKFAST -> "B"
    MealSlot.LUNCH -> "L"
    MealSlot.DINNER -> "D"
    MealSlot.SNACK -> "S"
    MealSlot.CUSTOM -> "C"
}
