package com.example.adobongkangkong.ui.meal.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
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
    onMoveDown: (() -> Unit)?,
    onRecipeVariantSelected: ((Long?) -> Unit)? = null
) {
    var showVariantMenu by remember(
        item.lineId,
        item.recipeVariantId,
        item.recipeVariantOptions
    ) {
        mutableStateOf(false)
    }

    val selectedVariantLabel = item.selectedVariantLabel ?: "Base recipe"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = item.foodName,
                    style = MaterialTheme.typography.titleLarge
                )

                if (item.isRecipe) {
                    Text(
                        text = if (item.recipeVariantId == null) {
                            selectedVariantLabel
                        } else {
                            "Variant: $selectedVariantLabel"
                        },
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
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRemove) {
                    Icon(
                        painter = painterResource(R.drawable.ms_delete),
                        contentDescription = "Remove",
                        modifier = Modifier.size(AppIconSize.CardAction),
                    )
                }
                VerticalDivider()
                IconButton(onClick = onToggleExpanded) {
                    Icon(
                        painter = painterResource(
                            if (isExpanded) R.drawable.ms_collapse_content
                            else R.drawable.ms_expand_content
                        ),
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(AppIconSize.CardAction),
                    )
                }

                if (onMoveUp != null) {
                    IconButton(onClick = onMoveUp) {
                        Icon(
                            painter = painterResource(R.drawable.ms_keyboard_double_arrow_up),
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
                            painter = painterResource(R.drawable.ms_keyboard_double_arrow_down),
                            contentDescription = "Move down",
                            modifier = Modifier.size(AppIconSize.CardAction),
                        )
                    }
                }
            }
        }

        item.macroSummaryLine
            ?.takeIf { it.isNotBlank() }
            ?.let { summary ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

        if (isExpanded) {
            Spacer(modifier = Modifier.padding(top = 8.dp))

            if (item.isRecipe && item.recipeVariantOptions.isNotEmpty()) {
                Text(
                    text = "Recipe version",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { showVariantMenu = true },
                        enabled = onRecipeVariantSelected != null
                    ) {
                        Text(selectedVariantLabel)
                    }

                    DropdownMenu(
                        expanded = showVariantMenu,
                        onDismissRequest = { showVariantMenu = false }
                    ) {
                        item.recipeVariantOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.name) },
                                onClick = {
                                    showVariantMenu = false
                                    onRecipeVariantSelected?.invoke(option.id)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.padding(top = 8.dp))
            }

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