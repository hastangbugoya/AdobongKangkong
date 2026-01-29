package com.example.adobongkangkong.ui.daylog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.ui.daylog.model.DayLogRow

@Composable
fun DayLogRowCard(row: DayLogRow) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = row.itemName,
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MacroText("Cal", row.caloriesKcal)
                MacroText("P", row.proteinG)
                MacroText("C", row.carbsG)
                MacroText("F", row.fatG)
            }
        }
    }
}

@Composable
private fun MacroText(label: String, value: Double?) {
    Text(
        text = "$label: ${value?.let { "%.1f".format(it) } ?: "—"}",
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center
    )
}
