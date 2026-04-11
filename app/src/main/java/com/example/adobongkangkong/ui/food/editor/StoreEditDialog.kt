package com.example.adobongkangkong.ui.food.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * First-pass store editor dialog used from Food Editor.
 *
 * Current persistence scope:
 * - Only store name is real saved data.
 *
 * Preview-only fields:
 * - address
 * - contact
 *
 * Those extra fields are intentionally UI-only for now so we can preview what a
 * future dedicated store editor might feel like without forcing a DB change yet.
 */
@Composable
fun StoreEditDialog(
    state: StoreEditorState,
    onNameChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onContactChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(state.title)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text("Store name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Preview-only fields for future store editor direction. These do not save yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = state.previewAddress,
                    onValueChange = onAddressChange,
                    label = { Text("Address (preview only)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                OutlinedTextField(
                    value = state.previewContact,
                    onValueChange = onContactChange,
                    label = { Text("Contact (preview only)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = state.canConfirm
            ) {
                Text(state.confirmButtonLabel)
            }
        },
        dismissButton = {
            if (onDelete != null && state.canDelete) {
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
