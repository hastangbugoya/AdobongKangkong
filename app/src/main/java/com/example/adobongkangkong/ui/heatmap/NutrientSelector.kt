package com.example.adobongkangkong.ui.heatmap

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.ui.dashboard.pinned.model.DashboardPinOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutrientSelector(
    options: List<DashboardPinOption>,
    selected: NutrientKey?,
    modifier: Modifier = Modifier,
    onSelect: (NutrientKey) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val filtered = remember(options, query) {
        if (query.isBlank()) options
        else {
            val q = query.trim().lowercase()
            options.filter { opt ->
                opt.displayName.lowercase().contains(q) ||
                        opt.key.value.lowercase().contains(q)
            }
        }
    }

    val selectedLabel = remember(options, selected) {
        val opt = options.firstOrNull { it.key == selected }
        opt?.let { formatOptionLabel(it) } ?: "Select nutrient"
    }

    Column(modifier = modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Nutrient") },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    expanded = false
                    query = ""
                }
            ) {
                // search box inside menu
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                )

                filtered.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(formatOptionLabel(opt)) },
                        onClick = {
                            onSelect(opt.key)
                            expanded = false
                            query = ""
                        }
                    )
                }

                if (filtered.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No matches") },
                        onClick = { /* no-op */ },
                        enabled = false
                    )
                }
            }
        }
    }
}

private fun formatOptionLabel(opt: DashboardPinOption): String {
    val unit = opt.unit?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
    // “Sodium — SODIUM_MG (mg)”
    return "${opt.displayName} — ${opt.key.value}$unit"
}

