package com.example.adobongkangkong.ui.meal.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.R
import com.example.adobongkangkong.ui.theme.AppIconSize

@Composable
fun MealEditorItemRow(
    item: MealEditorUiState.Item,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onServingsChanged: (String) -> Unit,
    onGramsChanged: (String) -> Unit,
    onMillilitersChanged: (String) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.foodName,
                    style = MaterialTheme.typography.titleMedium
                )

                item.macroSummaryLine
                    ?.takeIf { it.isNotBlank() }
                    ?.let { summary ->
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                item.effectiveQuantityText
                    ?.takeIf { it.isNotBlank() }
                    ?.let { quantityText ->
                        Text(
                            text = quantityText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleExpanded) {
                    Icon(
                        painter = painterResource(
                            if (isExpanded) R.drawable.compress_alt
                            else R.drawable.expand_arrows
                        ),
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(AppIconSize.CardAction),
                    )
                }

                IconButton(onClick = onRemove) {
                    Icon(
                        painter = painterResource(R.drawable.trash),
                        contentDescription = "Remove",
                        modifier = Modifier.size(AppIconSize.CardAction),
                    )
                }

                if (onMoveUp != null) {
                    IconButton(onClick = onMoveUp) {
                        Icon(
                            painter = painterResource(R.drawable.angle_double_small_down),
                            contentDescription = "Move up",
                            modifier = Modifier.size(AppIconSize.CardAction),
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(12.dp))
                }

                if (onMoveDown != null) {
                    IconButton(onClick = onMoveDown) {
                        Icon(
                            painter = painterResource(R.drawable.angle_double_small_up),
                            contentDescription = "Move down",
                            modifier = Modifier.size(AppIconSize.CardAction),
                        )
                    }
                }
            }
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.padding(top = 8.dp))

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = item.servings,
                onValueChange = onServingsChanged,
                label = { Text("Servings") },
                singleLine = true
            )

            Spacer(modifier = Modifier.padding(top = 8.dp))

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = item.grams?.toString().orEmpty(),
                onValueChange = onGramsChanged,
                label = { Text("Grams override (optional)") },
                singleLine = true
            )

            Spacer(modifier = Modifier.padding(top = 8.dp))

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = item.milliliters?.toString().orEmpty(),
                onValueChange = onMillilitersChanged,
                label = { Text("mL override (optional)") },
                singleLine = true
            )
        }
    }
}