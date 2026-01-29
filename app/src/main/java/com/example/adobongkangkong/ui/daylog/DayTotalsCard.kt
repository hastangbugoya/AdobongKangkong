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
import androidx.compose.ui.unit.dp

@Composable
fun DayTotalsCard(totals: Any /* replace with your DailyNutritionTotals type */) {
    // Adapt this to your real totals model.
    // You already show totals on dashboard — reuse that formatter logic.

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Daily totals", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))

            // Example layout – adapt to your real totals model:
            // Calories | Protein | Carbs | Fat

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Calories")
                Text("— kcal")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Protein")
                Text("— g")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Carbs")
                Text("— g")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Fat")
                Text("— g")
            }
        }
    }
}

