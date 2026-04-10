package com.example.adobongkangkong.ui.daylog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.R
import com.example.adobongkangkong.ui.daylog.model.DayLogIouRow
import com.example.adobongkangkong.ui.theme.AppIconSize
import kotlin.math.roundToInt

@Composable
fun DayLogIouRowCard(
    row: DayLogIouRow,
    onDelete: () -> Unit
) {
    val macroLine = buildIouMacroLine(row)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "IOU",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = row.description,
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                if (macroLine != null) {
                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = macroLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.trash),
                    contentDescription = "Delete IOU",
                    modifier = Modifier.size(AppIconSize.CardAction)
                )
            }
        }
    }
}

private fun buildIouMacroLine(row: DayLogIouRow): String? {
    val parts = buildList {
        row.estimatedCaloriesKcal?.let { add("${it.roundToInt()} kcal") }
        row.estimatedProteinG?.let { add("${formatMacroGrams(it)} P") }
        row.estimatedCarbsG?.let { add("${formatMacroGrams(it)} C") }
        row.estimatedFatG?.let { add("${formatMacroGrams(it)} F") }
    }

    return parts.takeIf { it.isNotEmpty() }?.joinToString("  ")
}

private fun formatMacroGrams(value: Double): String {
    val rounded = value.roundToInt()
    return if (kotlin.math.abs(value - rounded.toDouble()) < 0.001) {
        "${rounded}g"
    } else {
        "${((value * 10.0).roundToInt() / 10.0)}g"
    }
}