package com.example.adobongkangkong.ui.templates

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import com.example.adobongkangkong.R

/**
 * Overflow actions for the meal template editor.
 *
 * For developers:
 * - This is intentionally template-only UI.
 * - Duplicate/Delete are editor actions, not shared meal editor actions.
 * - Delete confirmation lives here so AppNavHost and MealEditorScreen stay simpler.
 */
@Composable
fun MealTemplateEditorActions(
    enabled: Boolean,
    onDuplicate: () -> Unit,
    onDeleteConfirmed: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    IconButton(
        enabled = enabled,
        onClick = { menuExpanded = true }
    ) {
        Icon(
            painter = painterResource(android.R.drawable.ic_menu_more),
            contentDescription = "Template actions"
        )
    }

    DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = { menuExpanded = false }
    ) {
        DropdownMenuItem(
            text = { Text("Duplicate") },
            onClick = {
                menuExpanded = false
                onDuplicate()
            }
        )
        DropdownMenuItem(
            text = {
                Text(
                    text = "Delete",
                    color = MaterialTheme.colorScheme.error
                )
            },
            onClick = {
                menuExpanded = false
                confirmDelete = true
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete template?") },
            text = { Text("This will permanently delete this meal template.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDeleteConfirmed()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Bottom KDoc for future AI assistant.
 *
 * This overflow menu was restored after Step 2 wiring was lost while later template work was
 * being reconciled. Keep this composable self-contained:
 * - menu state here
 * - delete confirmation here
 * - business logic stays in the ViewModel
 */
