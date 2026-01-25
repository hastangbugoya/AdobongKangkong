package com.example.adobongkangkong.ui.common.bottomsheet

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BlockingBottomSheet(
    model: BlockingSheetModel,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(text = model.title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(text = model.message, style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = model.onPrimary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(model.primaryButtonText)
        }

        model.secondaryButtonText?.let { label ->
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { model.onSecondary?.invoke() },
                enabled = model.onSecondary != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(label)
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Close")
        }
    }
}
