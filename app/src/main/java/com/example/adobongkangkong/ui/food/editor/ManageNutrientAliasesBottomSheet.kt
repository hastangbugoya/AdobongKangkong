package com.example.adobongkangkong.ui.food.editor

import android.os.Message
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageNutrientAliasesBottomSheet(
    nutrientDisplayName: String,
    aliases: List<String>,
    message: String?,
    onAddAlias: (String) -> Unit,
    onDeleteAlias: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    var newAlias by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(scrollState)
                .navigationBarsPadding()
        ) {
            Text("Aliases", style = MaterialTheme.typography.titleLarge)
            Text(nutrientDisplayName, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))

            if (aliases.isEmpty()) {
                Text("No aliases yet.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
            } else {
                aliases.forEach { a ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = a,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        TextButton(onClick = { onDeleteAlias(a) }) {
                            Text("Remove")
                        }
                    }
                    HorizontalDivider()
                }
                Spacer(Modifier.height(12.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newAlias,
                    onValueChange = { newAlias = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Add alias (e.g. pyridoxine)") }
                )
                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = newAlias.trim().isNotBlank(),
                    onClick = {
                        val v = newAlias.trim()
                        newAlias = ""
                        onAddAlias(v)
                    }
                ) {
                    Text("Add")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

