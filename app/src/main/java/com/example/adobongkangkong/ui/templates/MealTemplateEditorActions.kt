package com.example.adobongkangkong.ui.templates

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 * Overflow actions for an existing meal template editor session.
 *
 * ## For developers
 * - Duplicate opens a newly created copy of the current template.
 * - Delete always asks for confirmation before invoking [onDeleteConfirmed].
 * - This composable is editor-only and should not be reused for planned meal editing.
 */
@Composable
fun RowScope.MealTemplateEditorActions(
    enabled: Boolean,
    onDuplicate: () -> Unit,
    onDeleteConfirmed: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    IconButton(
        enabled = enabled,
        onClick = { menuExpanded = true }
    ) {
        Icon(
            painter = painterResource(R.drawable.menu_dots_vertical),
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
            text = { Text("Delete") },
            onClick = {
                menuExpanded = false
                showDeleteConfirm = true
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete template?") },
            text = { Text("This will permanently delete the meal template.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteConfirmed()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Bottom KDoc for future AI assistant.
 *
 * Keep delete confirmation here rather than in AppNavHost so the route remains focused on
 * navigation/effect collection while this composable owns the template-specific top-bar UI.
 */
