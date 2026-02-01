package com.example.adobongkangkong.domain.export


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

@Composable
fun ConfirmRestoreDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "This will delete and replace all current foods, attached nutrients, recipes, and ingredients.\n\n" +
                            "This action cannot be undone."
                )

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.uppercase() },
                    label = { Text("Type RESTORE to continue") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrect = false)
                )
            }
        },
        confirmButton = {
            Button(
                enabled = input == "RESTORE",
                onClick = onConfirm
            ) {
                Text("Restore")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
