package com.example.adobongkangkong.ui.food.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.domain.model.ServingUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEditorScreen(
    foodId: Long?,
    initialName: String?,
    onBack: () -> Unit,
    vm: FoodEditorViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(foodId, initialName) {
        vm.load(foodId, initialName)
    }

    val canSave = state.name.trim().isNotEmpty() && !state.isSaving

    var showAddNutrient by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (foodId == null) "New Food" else "Edit Food") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    TextButton(
                        onClick = { vm.save { onBack() } },
                        enabled = canSave
                    ) { Text(if (state.isSaving) "Saving…" else "Save") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {

            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage!!,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(12.dp))
            }

            // Basics
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("Basics", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = state.name,
                        onValueChange = vm::onNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Name*") },
                        singleLine = true
                    )

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = state.brand,
                        onValueChange = vm::onBrandChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Brand (optional)") },
                        singleLine = true
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = state.servingSize,
                            onValueChange = vm::onServingSizeChange,
                            modifier = Modifier.weight(1f),
                            label = { Text("Serving size") },
                            singleLine = true
                        )

                        ServingUnitDropdown(
                            value = state.servingUnit,
                            onChange = vm::onServingUnitChange,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = state.gramsPerServing,
                        onValueChange = vm::onGramsPerServingChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Grams per serving (optional)") },
                        singleLine = true
                    )

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = state.servingsPerPackage,
                        onValueChange = vm::onServingsPerPackageChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Servings per package (optional)") },
                        singleLine = true
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Nutrients
            Card {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Nutrients", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { showAddNutrient = true }) { Text("Add") }
                    }

                    Spacer(Modifier.height(8.dp))

                    if (state.nutrientRows.isEmpty()) {
                        Text("No nutrients yet. Tap Add.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        state.nutrientRows
                            .groupBy { it.category }
                            .forEach { (category, rows) ->
                                Text(category.displayName, style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.height(6.dp))

                                rows.forEach { row ->
                                    NutrientRow(
                                        row = row,
                                        onAmountChange = { vm.onNutrientAmountChange(row.nutrientId, it) },
                                        onRemove = { vm.removeNutrientRow(row.nutrientId) }
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }

                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(8.dp))
                            }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAddNutrient) {
        AddNutrientBottomSheet(
            query = state.nutrientSearchQuery,
            results = state.nutrientSearchResults,
            onQueryChange = vm::onNutrientSearchQueryChange,
            onPick = {
                vm.addNutrient(it)
                showAddNutrient = false
            },
            onDismiss = { showAddNutrient = false }
        )
    }
}

@Composable
private fun NutrientRow(
    row: NutrientRowUi,
    onAmountChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.bodyMedium)
            Text(row.unit.symbol, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = row.amount,
            onValueChange = onAmountChange,
            modifier = Modifier.width(120.dp),
            label = { Text("Amt") },
            singleLine = true
        )
        TextButton(onClick = onRemove) { Text("Remove") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServingUnitDropdown(
    value: ServingUnit,
    onChange: (ServingUnit) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = modifier) {
        OutlinedTextField(
            readOnly = true,
            value = value.name,
            onValueChange = {},
            label = { Text("Unit") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ServingUnit.entries.forEach { u ->
                DropdownMenuItem(
                    text = { Text(u.name) },
                    onClick = { onChange(u); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddNutrientBottomSheet(
    query: String,
    results: List<NutrientSearchResultUi>,
    onQueryChange: (String) -> Unit,
    onPick: (NutrientSearchResultUi) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Add nutrient", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search nutrients") },
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            if (query.isNotBlank() && results.isEmpty()) {
                Text("No matching nutrients.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                results.forEach { n ->
                    ListItem(
                        headlineContent = { Text(n.name) },
                        supportingContent = { Text("${n.category} • ${n.unit}") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onPick(n) }) { Text("Add") }
                    }
                    HorizontalDivider()
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

