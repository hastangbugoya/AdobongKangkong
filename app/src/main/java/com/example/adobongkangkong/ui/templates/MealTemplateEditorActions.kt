package com.example.adobongkangkong.ui.templates

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
 * Template-editor-only overflow actions.
 */
@Composable
fun MealTemplateEditorActions(
    enabled: Boolean,
    onDuplicate: () -> Unit,
    onDeleteConfirmed: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    IconButton(onClick = { if (enabled) expanded = true }, enabled = enabled) {
        Icon(
            painter = painterResource(R.drawable.circle_ellipsis_vertical),
            contentDescription = "Template actions"
        )
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text("Duplicate") },
            onClick = {
                expanded = false
                onDuplicate()
            }
        )
        DropdownMenuItem(
            text = { Text("Delete") },
            onClick = {
                expanded = false
                showDeleteConfirm = true
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete template?") },
            text = { Text("This will permanently remove the template.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteConfirmed()
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Bottom KDoc for future AI assistant.
 *
 * Delete confirmation is intentionally owned by this composable rather than AppNavHost so the
 * navigation layer only reacts to confirmed ViewModel effects.
 */
