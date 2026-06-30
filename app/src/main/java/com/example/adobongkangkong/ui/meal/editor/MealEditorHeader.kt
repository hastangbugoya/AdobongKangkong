package com.example.adobongkangkong.ui.meal.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.data.local.db.entity.MealSlot

/**
 * Shared editor header.
 *
 * For developers:
 * - Name field is always shown.
 * - Template-only fields are rendered only when [MealEditorMode.TEMPLATE].
 * - Macro summary remains advisory text and should not block save.
 */
@Composable
fun MealEditorHeader(
    state: MealEditorUiState,
    onNameChanged: (String) -> Unit,
    onTemplateDefaultSlotChanged: (MealSlot?) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (!state.subtitle.isNullOrBlank()) {
            Text(
                text = state.subtitle,
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.name,
            onValueChange = onNameChanged,
            label = {
                Text(
                    if (state.mode == MealEditorMode.TEMPLATE) "Template name"
                    else "Meal name (optional)"
                )
            },
            singleLine = true
        )

        if (state.mode == MealEditorMode.TEMPLATE) {
            Spacer(modifier = Modifier.height(6.dp))

            TemplateDefaultSlotField(
                selected = state.templateDefaultSlot,
                onSelected = onTemplateDefaultSlotChanged
            )
        }
    }
}

@Composable
private fun TemplateDefaultSlotField(
    selected: MealSlot?,
    onSelected: (MealSlot?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Default meal slot",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f)
        )

        Box {
            TextButton(
                onClick = { expanded = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(selected?.display ?: "None")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("None") },
                    onClick = {
                        expanded = false
                        onSelected(null)
                    }
                )
                MealSlot.entries.forEach { slot ->
                    DropdownMenuItem(
                        text = { Text(slot.display) },
                        onClick = {
                            expanded = false
                            onSelected(slot)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Bottom KDoc for future AI assistant.
 *
 * Template default slot lives in the shared header because templates are edited through the shared
 * meal editor shell. Keep the planned-meal mode visually unchanged.
 */