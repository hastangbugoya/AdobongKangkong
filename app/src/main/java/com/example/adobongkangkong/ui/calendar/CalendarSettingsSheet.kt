package com.example.adobongkangkong.ui.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
        selectedKeys.toNormalizedKeySet()
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
            text = "Choose which dashboard nutrients determine calendar day success. If none are selected, all dashboard nutrients are used by default.",
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
            CalendarSuccessOptionRow(
                option = option,
                isChecked = option.key.value.normalizedKey() in normalizedSelectedKeys,
                onToggle = onToggle
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

@Composable
private fun CalendarSuccessOptionRow(
    option: CalendarSuccessOption,
    isChecked: Boolean,
    onToggle: (NutrientKey, Boolean) -> Unit
) {
    ListItem(
        headlineContent = {
            Text(option.displayName)
        },
        supportingContent = {
            Text(option.supportingText())
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
}

private fun CalendarSuccessOption.supportingText(): String {
    val unitText = unit.trim()
    return if (unitText.isNotBlank()) {
        "${key.value} • $unitText"
    } else {
        key.value
    }
}

private fun Set<String>.toNormalizedKeySet(): Set<String> =
    map { it.normalizedKey() }.toSet()

private fun String.normalizedKey(): String =
    trim().uppercase()