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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.BuildConfig
import com.example.adobongkangkong.domain.model.TargetDraft
import com.example.adobongkangkong.domain.model.UserNutrientPreference
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.trend.model.DashboardNutrientCard
import com.example.adobongkangkong.ui.dashboard.pinned.model.DashboardPinOption

enum class DashboardDebugResetDomain(
    val displayName: String
) {
    LOGS("Logs"),
    RECIPE_BATCHES("Recipe batches"),
    PLANNER("Planner data")
}

enum class DashboardDebugResetScope(
    val displayName: String
) {
    ALL("All"),
    BEFORE_SELECTED_DATE("Before selected date"),
    AFTER_SELECTED_DATE("After selected date")
}

@Composable
fun DashboardSettingsSheet(
    pinnedKeys: List<NutrientKey>,
    monitoredCards: List<DashboardNutrientCard>,
    targetDraft: TargetDraft?,
    onDismiss: () -> Unit,
    pinOptions: List<DashboardPinOption>,
    nutrientPreferences: List<UserNutrientPreference>,
    onApplyPins: (slot0: String?, slot1: String?) -> Unit,
    onPinnedChange: (NutrientKey, Boolean) -> Unit,
    onCriticalChange: (NutrientKey, Boolean) -> Unit,
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
    onOpenBackup: () -> Unit,
    onDebugReset: (
        domains: Set<DashboardDebugResetDomain>,
        scope: DashboardDebugResetScope
    ) -> Unit,
    onBuildSharedSnapshotJson: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Dashboard Settings", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        // ---------------- Nutrient preferences ----------------

        Text("Pinned nutrients (2)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        val preferenceByKey = remember(nutrientPreferences) {
            nutrientPreferences.associateBy { it.key.value }
        }

        pinOptions.forEach { option ->
            val preference = preferenceByKey[option.key.value]

            ListItem(
                headlineContent = { Text(option.displayName) },
                supportingContent = {
                    Text(
                        buildString {
                            append(option.key.value)
                            option.unit?.takeIf { it.isNotBlank() }?.let {
                                append(" • ")
                                append(it)
                            }
                        }
                    )
                },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Pin")
                            Switch(
                                checked = preference?.isPinned == true,
                                onCheckedChange = { checked ->
                                    onPinnedChange(option.key, checked)
                                }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Critical")
                            Switch(
                                checked = preference?.isCritical == true,
                                onCheckedChange = { checked ->
                                    onCriticalChange(option.key, checked)
                                }
                            )
                        }
                    }
                }
            )
            HorizontalDivider()
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
                ) {
                    Text("Save")
                }

                TextButton(onClick = onCancelTargetEdit) {
                    Text("Cancel")
                }
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

        Button(
            onClick = {
                onDismiss()
                onOpenBackup()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Backup & Restore")
        }

        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Debug Tools", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onBuildSharedSnapshotJson,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Build Shared Nutrition Snapshot (JSON)")
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Debug Reset", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Uses the dashboard selected date for before/after actions.",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(12.dp))

            var resetLogs by remember { mutableStateOf(true) }
            var resetRecipeBatches by remember { mutableStateOf(false) }
            var resetPlanner by remember { mutableStateOf(false) }

            DebugResetCheckboxRow(
                label = DashboardDebugResetDomain.LOGS.displayName,
                checked = resetLogs,
                onCheckedChange = { resetLogs = it }
            )

            DebugResetCheckboxRow(
                label = DashboardDebugResetDomain.RECIPE_BATCHES.displayName,
                checked = resetRecipeBatches,
                onCheckedChange = { resetRecipeBatches = it }
            )

            DebugResetCheckboxRow(
                label = DashboardDebugResetDomain.PLANNER.displayName,
                checked = resetPlanner,
                onCheckedChange = { resetPlanner = it }
            )

            Spacer(Modifier.height(12.dp))

            var resetScope by remember {
                mutableStateOf(DashboardDebugResetScope.ALL)
            }

            DebugResetScopeDropdown(
                selectedScope = resetScope,
                onSelect = { resetScope = it }
            )

            Spacer(Modifier.height(12.dp))

            val selectedDomains = remember(resetLogs, resetRecipeBatches, resetPlanner) {
                buildSet {
                    if (resetLogs) add(DashboardDebugResetDomain.LOGS)
                    if (resetRecipeBatches) add(DashboardDebugResetDomain.RECIPE_BATCHES)
                    if (resetPlanner) add(DashboardDebugResetDomain.PLANNER)
                }
            }

            Button(
                enabled = selectedDomains.isNotEmpty(),
                onClick = {
                    onDismiss()
                    onDebugReset(
                        selectedDomains,
                        resetScope
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Run Debug Reset")
            }
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

@Composable
private fun DebugResetCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugResetScopeDropdown(
    selectedScope: DashboardDebugResetScope,
    onSelect: (DashboardDebugResetScope) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedScope.displayName,
            onValueChange = { },
            readOnly = true,
            label = { Text("Reset scope") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DashboardDebugResetScope.entries.forEach { scope ->
                DropdownMenuItem(
                    text = { Text(scope.displayName) },
                    onClick = {
                        onSelect(scope)
                        expanded = false
                    }
                )
            }
        }
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