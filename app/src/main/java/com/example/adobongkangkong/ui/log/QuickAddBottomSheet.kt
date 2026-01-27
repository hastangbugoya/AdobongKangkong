package com.example.adobongkangkong.ui.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.domain.logging.model.BatchSummary
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.nutrition.gramsPerServingResolved
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddBottomSheet(
    onDismiss: () -> Unit,
    onCreateFood: (String) -> Unit,
    vm: QuickAddViewModel = hiltViewModel()
) {
    val focus = LocalFocusManager.current
    val state by vm.state.collectAsState()

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Text("Quick Add", style = MaterialTheme.typography.titleLarge)

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search foods") },
                singleLine = true
            )



            Spacer(Modifier.height(12.dp))

            if (state.selectedFood == null) {

                if (state.query.isNotBlank() && state.results.isEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        Text(
                            text = "No matching foods",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(12.dp))

                        TextButton(
                            onClick = {
                                onDismiss()
                                onCreateFood(state.query)
                            }
                        ) {
                            Text("Create food \"${state.query}\"")
                        }
                    }

                } else {
                    FoodSearchResults(
                        results = state.results,
                        onPick = {
                            focus.clearFocus()
                            vm.onFoodSelected(it)
                        }
                    )
                }

            } else {
                // Selected food: scroll this section so the Log button is reachable
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectedFoodPanel(
                        food = state.selectedFood!!,
                        servings = state.servings,
                        servingUnitAmount = state.servingUnitAmount
                            ?: state.selectedFood!!.servingSize,
                        gramsAmount = state.gramsAmount,
                        inputMode = state.inputMode,
                        batches = state.batches,
                        selectedBatchId = state.selectedBatchId,
                        onBatchSelected = vm::onBatchSelected,
                        onCreateBatch = vm::openCreateBatchDialog,
                        onBack = vm::clearSelection,
                        onServingsChanged = vm::onServingsChanged,
                        onServingUnitAmountChanged = vm::onServingUnitAmountChanged,
                        onGramsChanged = vm::onGramsChanged,
                        onPackage = vm::onPackageClicked,
                        onSave = { vm.save(onDone = onDismiss) },
                        errorMessage = state.errorMessage
                    )

                    Spacer(Modifier.height(12.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            if (state.isCreateBatchDialogOpen) {
                CreateBatchDialog(
                    yieldGramsText = state.yieldGramsText,
                    servingsYieldText = state.servingsYieldText,
                    onYieldChange = vm::onYieldGramsTextChange,
                    onServingsYieldChange = vm::onServingsYieldTextChange,
                    onDismiss = vm::closeCreateBatchDialog,
                    onConfirm = vm::createBatchForSelectedRecipe
                )
            }
        }
    }

}

@Composable
private fun FoodSearchResults(
    results: List<Food>,
    onPick: (Food) -> Unit
) {
    if (results.isEmpty()) {
        Text(
            "Type to search your foods…",
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(results, key = { it.id }) { food ->
            ListItem(
                headlineContent = { Text(food.name) },
                supportingContent = {
                    val subtitle = buildString {
                        if (!food.brand.isNullOrBlank()) append(food.brand).append(" • ")
                        append("${food.servingSize.clean()} ${food.servingUnit}")
                        food.gramsPerServingResolved()?.let { append(" (${it.clean()} g)") }
                    }
                    Text(subtitle)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(food) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun SelectedFoodPanel(
    food: Food,
    servings: Double,
    servingUnitAmount: Double,
    gramsAmount: Double?,
    inputMode: InputMode,
    batches: List<BatchSummary>,
    selectedBatchId: Long?,
    errorMessage: String?,
    onBatchSelected: (Long?) -> Unit,
    onCreateBatch: () -> Unit,
    onBack: () -> Unit,
    onServingsChanged: (Double) -> Unit,
    onServingUnitAmountChanged: (Double) -> Unit,
    onGramsChanged: (Double) -> Unit,
    onPackage: (Double) -> Unit,
    onSave: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(food.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${food.servingSize.clean()} ${food.servingUnit}" +
                        (food.gramsPerServingResolved()?.let { " (${it.clean()} g)" } ?: ""),
                style = MaterialTheme.typography.bodySmall
            )
        }
        TextButton(onClick = onBack) { Text("Change") }
    }

    Spacer(Modifier.height(12.dp))

    // Servings input (canonical)
    AmountRow(
        label = "Servings",
        value = servings,
        unit = "",
        onMinus = { onServingsChanged(max(0.0, servings - 0.5)) },
        onPlus = { onServingsChanged(servings + 0.5) }
    )

    Spacer(Modifier.height(10.dp))

    // Serving-unit amount input (volume/unit)
    NumberField(
        label = "Amount (${food.servingUnit})",
        value = servingUnitAmount,
        onValue = onServingUnitAmountChanged
    )

    Spacer(Modifier.height(10.dp))

    // Grams input if available
    val canLogGrams = food.gramsPerServingResolved() != null
    val gramsDefault = servings * (food.gramsPerServingResolved() ?: 0.0)
    if (canLogGrams) {
        NumberField(
            label = "Grams (g)",
            value = gramsAmount ?: gramsDefault,
            onValue = onGramsChanged
        )
    }

    // Package buttons
    if (food.servingsPerPackage != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { onPackage(0.5) },
                label = { Text("½ package") }
            )
            AssistChip(
                onClick = { onPackage(1.0) },
                label = { Text("1 package") }
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    if (food.isRecipe) {

        Spacer(Modifier.height(16.dp))

        Text("Cooked batch", style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(8.dp))

        BatchSelector(
            batches = batches,
            selectedBatchId = selectedBatchId,
            onSelected = onBatchSelected,
            onCreate = onCreateBatch
        )

        Spacer(Modifier.height(12.dp))
    }

    val isLoggingByGrams = inputMode == InputMode.GRAMS

    val isLogEnabled =
        !food.isRecipe ||
                !isLoggingByGrams ||
                selectedBatchId != null

    errorMessage?.let { msg ->
        Spacer(Modifier.height(8.dp))
        Text(
            msg,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }

    if (food.isRecipe && isLoggingByGrams && selectedBatchId == null && errorMessage == null) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Select or create a cooked batch to log by grams.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Button(
        onClick = onSave,
        enabled = isLogEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text("Log")
    }

}

@Composable
private fun AmountRow(
    label: String,
    value: Double,
    unit: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onMinus) { Text("–") }
            Text("${value.clean()} $unit", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onPlus) { Text("+") }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Double,
    onValue: (Double) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.clean()) }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toDoubleOrNull()?.let(onValue)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun BatchSelector(
    batches: List<BatchSummary>,
    selectedBatchId: Long?,
    onSelected: (Long?) -> Unit,
    onCreate: () -> Unit
) {
    Column {

        if (batches.isEmpty()) {
            Text(
                "No cooked batches yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            OutlinedButton(onClick = onCreate) {
                Text("Create cooked batch")
            }
            return
        }

        var expanded by remember { mutableStateOf(false) }

        val selected =
            batches.firstOrNull { it.batchId == selectedBatchId }

        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                selected?.let {
                    "Batch: ${it.cookedYieldGrams.clean()} g"
                } ?: "Select batch"
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            batches.forEach { batch ->
                DropdownMenuItem(
                    text = {
                        Text("${batch.cookedYieldGrams.clean()} g")
                    },
                    onClick = {
                        expanded = false
                        onSelected(batch.batchId)
                    }
                )
            }

            Divider()

            DropdownMenuItem(
                text = { Text("➕ New cooked batch") },
                onClick = {
                    expanded = false
                    onCreate()
                }
            )
        }
    }
}

@Composable
private fun CreateBatchDialog(
    yieldGramsText: String,
    servingsYieldText: String,
    onYieldChange: (String) -> Unit,
    onServingsYieldChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New cooked batch") },
        text = {
            Column {
                OutlinedTextField(
                    value = yieldGramsText,
                    onValueChange = onYieldChange,
                    label = { Text("Cooked yield (grams)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = servingsYieldText,
                    onValueChange = onServingsYieldChange,
                    label = { Text("Servings used (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


private fun Double.clean(): String =
    if (this % 1.0 == 0.0) this.toInt().toString() else "%,.2f".format(this)

