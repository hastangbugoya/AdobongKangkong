package com.example.adobongkangkong.ui.heatmap

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
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.domain.model.DailyNutritionTotals
import com.example.adobongkangkong.domain.nutrition.MacroKeys

@Composable
fun DayTotalsSummary(
    totals: DailyNutritionTotals
) {
    val n = totals.totalsByCode

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Daily totals", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            TotalsRow("Calories", n[MacroKeys.CALORIES.value], "kcal", decimals = 0)
            TotalsRow("Protein", n[MacroKeys.PROTEIN.value], "g")
            TotalsRow("Carbs", n[MacroKeys.CARBS.value], "g")
            TotalsRow("Fat", n[MacroKeys.FAT.value], "g")
        }
    }
}

@Composable
private fun TotalsRow(
    label: String,
    value: Double?,
    unit: String,
    decimals: Int = 1
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value?.format(decimals)?.let { "$it $unit" } ?: "—",
            style = MaterialTheme.typography.bodyMedium
        )
    }
    Spacer(Modifier.height(6.dp))
}

private fun Double.format(decimals: Int): String =
    "%,.${decimals}f".format(this)
