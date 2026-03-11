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
import com.example.adobongkangkong.domain.model.DailyNutritionTotals
import com.example.adobongkangkong.domain.nutrition.NutrientCodes
import com.example.adobongkangkong.domain.nutrition.NutrientKey

@Composable
fun DayTotalsCard(totals: DailyNutritionTotals) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(14.dp)) {

            Text(
                text = "Daily totals",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Calories", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${format3(totals.totalsByCode[NutrientKey(NutrientCodes.CALORIES_KCAL)])} kcal",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Protein", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${format3(totals.totalsByCode[NutrientKey(NutrientCodes.PROTEIN_G)])} g",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Carbs", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${format3(totals.totalsByCode[NutrientKey(NutrientCodes.CARBS_G)])} g",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Fat", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${format3(totals.totalsByCode[NutrientKey(NutrientCodes.FAT_G)])} g",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

private fun format3(value: Double?): String {
    val v = value ?: return "0"
    return "%.3f".format(v)
}
