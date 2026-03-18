package com.example.adobongkangkong.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.domain.nutrition.NutrientKey

@Composable
fun CalendarSettingsSheet(
    options: List<CalendarSuccessOption>,
    selectedKeys: Set<String>,
    onToggle: (NutrientKey, Boolean) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val normalizedSelectedKeys = remember(selectedKeys) {
        selectedKeys.map { it.trim().uppercase() }.toSet()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Calendar Success Settings",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Choose which nutrients determine monthly calendar day success. If none are selected, calendar success falls back to the current default behavior.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onClearAll,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Use Default")
        }

        Spacer(Modifier.height(16.dp))

        options.forEach { option ->
            val isChecked = option.key.value.trim().uppercase() in normalizedSelectedKeys

            ListItem(
                headlineContent = {
                    Text(option.displayName)
                },
                supportingContent = {
                    val unitText = option.unit.trim()
                    if (unitText.isNotBlank()) {
                        Text("${option.key.value} • $unitText")
                    } else {
                        Text(option.key.value)
                    }
                },
                trailingContent = {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { checked ->
                            onToggle(option.key, checked)
                        }
                    )
                }
            )

            HorizontalDivider()
        }

        Spacer(Modifier.height(16.dp))

        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Done")
        }

        Spacer(Modifier.height(8.dp))
    }
}