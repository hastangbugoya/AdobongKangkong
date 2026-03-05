package com.example.adobongkangkong.ui.planner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun IouEditorDialog(
    state: IouEditorState,
    onDismiss: () -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSave: () -> Unit
) {
    val isEdit = state.iouId != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = if (isEdit) "Edit IOU" else "New IOU")
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = state.description,
                    onValueChange = onDescriptionChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    placeholder = { Text("Describe what you ate…") },
                    supportingText = {
                        if (state.errorMessage != null) {
                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = !state.isSaving
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSaving) {
                Text("Cancel")
            }
        },
        modifier = Modifier.padding(8.dp)
    )
}
