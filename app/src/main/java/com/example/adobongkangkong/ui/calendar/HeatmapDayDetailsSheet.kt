package com.example.adobongkangkong.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.ui.calendar.model.CalendarDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HeatmapDayDetailsSheet(
    day: CalendarDay,
    nutrientDisplayName: String?,
    nutrientUnit: String?,
    onViewLogs: (LocalDate) -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit
) {
    val dateFmt = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")
    val title = nutrientDisplayName ?: day.nutrientKey.value
    val unit = nutrientUnit?.takeIf { it.isNotBlank() } ?: ""

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 18.dp)
    ) {
        Text(
            text = day.date.format(dateFmt),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(6.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(12.dp))

        // Value
        val valueText = day.value?.let { v ->
            if (unit.isBlank()) v.format1() else "${v.format1()} $unit"
        } ?: "—"
        LabeledRow(label = "Value", value = valueText)

        // Targets
        LabeledRow(label = "Min", value = day.min?.format1()?.withUnit(unit) ?: "—")
        LabeledRow(label = "Target", value = day.target?.format1()?.withUnit(unit) ?: "—")
        LabeledRow(label = "Max", value = day.max?.format1()?.withUnit(unit) ?: "—")

        // Status
        Spacer(Modifier.height(6.dp))
        LabeledRow(label = "Status", value = day.status.name)

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = { onViewLogs(day.date) }
            ) {
                Text("View logs")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onShare
            ) {
                Text("Share")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onClose
            ) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(6.dp))
}

private fun Double.format1(): String = "%,.1f".format(this)

private fun String.withUnit(unit: String): String =
    if (unit.isBlank()) this else "$this $unit"

