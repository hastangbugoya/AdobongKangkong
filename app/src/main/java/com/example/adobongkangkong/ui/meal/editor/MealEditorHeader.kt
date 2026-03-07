package com.example.adobongkangkong.ui.meal.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared header block for planned-meal and template editing.
 *
 * ## For developers
 * This composable owns only header presentation:
 * - subtitle / warnings
 * - editable name field
 * - optional live macro guidance for template mode
 *
 * Keep it passive:
 * - formatting/computation of macro guidance belongs upstream
 * - this composable only renders `state.liveMacroSummaryLine` when applicable
 */
@Composable
fun MealEditorHeader(
    state: MealEditorUiState,
    onNameChanged: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        if (!state.subtitle.isNullOrBlank()) {
            Text(
                text = state.subtitle,
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        state.warnings.forEach { warning ->
            Text(
                text = warning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (state.mode == MealEditorMode.TEMPLATE && !state.liveMacroSummaryLine.isNullOrBlank()) {
            Text(
                text = state.liveMacroSummaryLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.name,
            onValueChange = onNameChanged,
            label = {
                Text(
                    if (state.mode == MealEditorMode.TEMPLATE) {
                        "Template name"
                    } else {
                        "Meal name (optional)"
                    }
                )
            },
            singleLine = true
        )
    }
}

/**
 * Bottom KDoc for future AI assistant.
 *
 * Template live macros intentionally render here because this sits near the top of the editor and
 * matches the previously agreed Phase 3A UX: visible guidance, non-blocking save, no extra screen
 * section. If future work adds goal comparisons, keep this header render-only and pass richer
 * display strings/state from the ViewModel instead of doing comparison logic here.
 */
