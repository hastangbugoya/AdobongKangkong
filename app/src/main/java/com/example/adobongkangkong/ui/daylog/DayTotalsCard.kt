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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.domain.model.DailyNutritionTotals
import com.example.adobongkangkong.domain.nutrition.NutrientCodes
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import kotlin.math.roundToInt

@Composable
fun DayTotalsCard(
    totals: DailyNutritionTotals,
    sodiumLimitMg: Double = 0.0,
    sugarLimitG: Double = 0.0
) {
    val sodiumMg = totals.totalsByCode[NutrientKey(NutrientCodes.SODIUM_MG)]
    val sugarG = totals.totalsByCode[NutrientKey(NutrientCodes.SUGARS_G)]

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

            DailyTotalsRow(
                label = "Calories",
                value = "${formatWhole(totals.totalsByCode[NutrientKey(NutrientCodes.CALORIES_KCAL)])} kcal"
            )

            DailyTotalsRow(
                label = "Protein",
                value = "${formatWhole(totals.totalsByCode[NutrientKey(NutrientCodes.PROTEIN_G)])} g"
            )

            DailyTotalsRow(
                label = "Carbs",
                value = "${formatWhole(totals.totalsByCode[NutrientKey(NutrientCodes.CARBS_G)])} g"
            )

            DailyTotalsRow(
                label = "Fat",
                value = "${formatWhole(totals.totalsByCode[NutrientKey(NutrientCodes.FAT_G)])} g"
            )

            if (sodiumLimitMg > 0.0 && sodiumMg != null && sodiumMg > sodiumLimitMg) {
                DailyTotalsRow(
                    label = "Sodium",
                    value = "${formatWhole(sodiumMg)} mg",
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (sugarLimitG > 0.0 && sugarG != null && sugarG > sugarLimitG) {
                DailyTotalsRow(
                    label = "Total sugar",
                    value = "${formatWhole(sugarG)} g",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DailyTotalsRow(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = color
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = color
        )
    }
}

private fun formatWhole(value: Double?): String {
    val v = value ?: return "0"
    return v.roundToInt().toString()
}