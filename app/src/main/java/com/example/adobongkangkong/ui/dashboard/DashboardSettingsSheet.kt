package com.example.adobongkangkong.ui.dashboard

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.domain.model.TargetDraft
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.trend.model.DashboardNutrientCard
import com.example.adobongkangkong.ui.dashboard.pinned.model.DashboardPinOption

@Composable
fun DashboardSettingsSheet(
    pinnedKeys: List<NutrientKey>,
    monitoredCards: List<DashboardNutrientCard>,
    targetDraft: TargetDraft?,
    onDismiss: () -> Unit,
    pinOptions: List<DashboardPinOption>,
    onApplyPins: (slot0: String?, slot1: String?) -> Unit,
    onStartTargetEditPrefilled: (NutrientKey, String, String, String) -> Unit,
    onDraftMinChange: (String) -> Unit,
    onDraftTargetChange: (String) -> Unit,
    onDraftMaxChange: (String) -> Unit,
    onCancelTargetEdit: () -> Unit,
    onSaveTargetDraft: () -> Unit,

    onSync: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onOpenMeowLogs: () -> Unit,
    onOpenPlanner: () -> Unit,

    // NEW: Full backup/restore screen (DB + images)
    onOpenBackup: () -> Unit
) {
    var slot0Code by remember(pinnedKeys) { mutableStateOf(pinnedKeys.getOrNull(0)?.value) }
    var slot1Code by remember(pinnedKeys) { mutableStateOf(pinnedKeys.getOrNull(1)?.value) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Dashboard Settings", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        // ---------------- Pins ----------------

        Text("Pinned nutrients (2)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        PinDropdown(
            title = "Slot 1",
            selectedCode = slot0Code,
            options = pinOptions,
            disabledCodes = setOfNotNull(slot1Code),
            onSelect = { slot0Code = it }
        )

        Spacer(Modifier.height(8.dp))

        PinDropdown(
            title = "Slot 2",
            selectedCode = slot1Code,
            options = pinOptions,
            disabledCodes = setOfNotNull(slot0Code),
            onSelect = { slot1Code = it }
        )

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                onApplyPins(slot0Code, slot1Code)
            }) { Text("Apply pins") }

            TextButton(onClick = {
                slot0Code = null
                slot1Code = null
                onApplyPins(null, null)
            }) { Text("Clear") }
        }

        Spacer(Modifier.height(20.dp))

        // ---------------- Targets ----------------

        targetDraft?.let { draft ->
            Text("Targets (min / target / max)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Text("Edit: ${draft.key.value}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = draft.min,
                onValueChange = onDraftMinChange,
                label = { Text("Min (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.target,
                onValueChange = onDraftTargetChange,
                label = { Text("Target (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.max,
                onValueChange = onDraftMaxChange,
                label = { Text("Max (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            if (draft.error != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = draft.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !draft.isSaving && draft.isDirty,
                    onClick = onSaveTargetDraft
                ) { Text("Save") }

                TextButton(onClick = onCancelTargetEdit) { Text("Cancel") }
            }
        }

        Spacer(Modifier.height(8.dp))

        monitoredCards.forEach { card ->
            ListItem(
                headlineContent = { Text(card.displayName) },
                supportingContent = {
                    Text(
                        buildString {
                            append("min="); append(card.minPerDay ?: "—")
                            append("  target="); append(card.targetPerDay ?: "—")
                            append("  max="); append(card.maxPerDay ?: "—")
                            if (!card.unit.isNullOrBlank()) {
                                append(" "); append(card.unit)
                            }
                        }
                    )
                },
                modifier = Modifier.clickable {
                    Log.d("Meow", "SettingsSheet tap: ${card.displayName}")
                    onStartTargetEditPrefilled(
                        NutrientKey(card.code),
                        card.minPerDay?.toString() ?: "",
                        card.targetPerDay?.toString() ?: "",
                        card.maxPerDay?.toString() ?: ""
                    )
                }
            )
            HorizontalDivider()
        }
        Text("DEBUG targetDraft = ${targetDraft != null}")

        Spacer(Modifier.height(20.dp))

        // ---------------- Data ----------------

        Text("Data", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        ListItem(
            headlineContent = { Text("Sync nutrient catalog") },
            supportingContent = { Text("Refresh nutrient database") },
            modifier = Modifier.clickable {
                onDismiss()
                onSync()
            }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("Export foods + recipes") },
            supportingContent = { Text("Save ZIP backup") },
            modifier = Modifier.clickable {
                onDismiss()
                onExport()
            }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("Import foods + recipes") },
            supportingContent = { Text("Restore from ZIP") },
            modifier = Modifier.clickable {
                onDismiss()
                onImport()
            }
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                onDismiss()
                onOpenMeowLogs()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Meow Logs")
        }

        Spacer(Modifier.height(12.dp))

        // NEW: Full app backup/restore (DB + images)
        Button(
            onClick = {
                onDismiss()
                onOpenBackup()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Backup & Restore")
        }

//        Spacer(Modifier.height(16.dp))
//
//        Button(
//            onClick = {
//                onDismiss()
//                onOpenPlanner()
//            },
//            modifier = Modifier.fillMaxWidth()
//        ) {
//            Text("Meal Planner")
//        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinDropdown(
    title: String,
    selectedCode: String?,
    options: List<DashboardPinOption>,
    disabledCodes: Set<String>,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val selectedLabel = remember(selectedCode, options) {
        options.firstOrNull { it.key.value.equals(selectedCode, ignoreCase = true) }?.let { opt ->
            val unit = opt.unit?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            "${opt.displayName} — ${opt.key.value}$unit"
        } ?: (selectedCode ?: "None")
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = { /* read-only */ },
            readOnly = true,
            label = { Text(title) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("None") },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            HorizontalDivider()

            options.forEach { opt ->
                val code = opt.key.value
                val unit = opt.unit?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                val label = "${opt.displayName} — $code$unit"

                val disabled = disabledCodes.any { it.equals(code, ignoreCase = true) }

                DropdownMenuItem(
                    text = { Text(label) },
                    enabled = !disabled,
                    onClick = {
                        onSelect(code)
                        expanded = false
                    }
                )
            }
        }
    }
}