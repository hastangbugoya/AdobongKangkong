package com.example.adobongkangkong.ui.log

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.data.local.db.entity.FoodGoalFlagsEntity
import com.example.adobongkangkong.domain.logging.model.BatchSummary
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.nutrition.gramsPerServingUnitResolved
import com.example.adobongkangkong.feature.camera.FoodImageStorage
import com.example.adobongkangkong.ui.food.FoodGoalFlagsStrip
import com.example.adobongkangkong.ui.food.FoodListItemUiModel
import com.example.adobongkangkong.ui.food.SelectedFoodPanel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Quick-add logging sheet.
 *
 * Shows food search + a compact logging form.
 * If the selected food uses a non-gram serving unit and is missing grams-per-serving,
 * an **Edit food** button is shown to jump to the food editor (so the user can fill in grams-per-serving).
 *
 * @param onOpenFoodEditor Navigate to the food editor for the given food id.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddBottomSheet(
    onDismiss: () -> Unit,
    onCreateFood: (String) -> Unit,
    onOpenFoodEditor: (foodId: Long) -> Unit = {},
    logDate: LocalDate,
    vm: QuickAddViewModel = hiltViewModel()
) {
    val focus = LocalFocusManager.current
    val state by vm.state.collectAsState()

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    val formatter = DateTimeFormatter.ofPattern(
        "EEEE MMMM d, yyyy",
        Locale.getDefault()
    )

    rememberScrollState()
    Log.d("Meow", "QuickAddBottomSheet> resolveMassOpen=${state.isResolveMassDialogOpen}")
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
            Spacer(Modifier.height(8.dp))
            Text("${logDate.format(formatter)}", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(12.dp))

            if (state.selectedFood == null) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search foods") },
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))
            }

            if (state.isResolveMassDialogOpen) {
                val food = state.selectedFood
                val ml = food?.mlPerServingUnit

                AlertDialog(
                    onDismissRequest = vm::closeResolveMassDialog,
                    title = { Text("Mass needed for servings") },
                    text = {
                        Column {
                            Text("To log by servings, we need grams per serving.")
                            Spacer(Modifier.height(12.dp))

                            OutlinedTextField(
                                value = state.gramsPerServingText,
                                onValueChange = vm::onGramsPerServingTextChange,
                                label = { Text("Grams per serving") },
                                singleLine = true
                            )

                            if (ml != null) {
                                Spacer(Modifier.height(12.dp))
                                Text("Or use an estimate: 1 mL = 1 g → ${ml} g per serving.")
                            }
                        }
                    },
                    confirmButton = {
                        Column {
                            Button(
                                onClick = vm::confirmEnteredGramsPerServing,
                                enabled = state.gramsPerServingText.toDoubleOrNull()?.let { it > 0.0 } == true
                            ) { Text("Save grams & log") }

                            if (ml != null) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = vm::useEstimateJustOnce) {
                                    Text("Use estimate just once")
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = vm::useEstimateAlways) {
                                    Text("Use estimate always for this food")
                                }
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = vm::closeResolveMassDialog) { Text("Cancel") }
                    }
                )
            }

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
                val selected = state.selectedFood!!
                val context = LocalContext.current

                // Proof-of-concept: show the saved banner JPG as a subtle background behind the panel
                // (no extra vertical space).
                val bannerBitmapState = produceState<android.graphics.Bitmap?>(
                    initialValue = null,
                    key1 = selected.id
                ) {
                    val storage = FoodImageStorage(context)
                    val file = storage.bannerJpegFile(selected.id)
                    value = if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                }

                val isLoggingByGrams = state.inputMode == InputMode.GRAMS
                val isLogEnabled =
                    !selected.isRecipe ||
                            !isLoggingByGrams ||
                            state.selectedBatchId != null

                Box(Modifier.fillMaxWidth()) {
                    bannerBitmapState.value?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop,
                            alpha = 0.18f
                        )
                    }

                    SelectedFoodPanel(
                        food = selected,
                        servings = state.servings,
                        servingUnitAmount = state.servingUnitAmount
                            ?: selected.servingSize,
                        gramsAmount = state.gramsAmount,
                        inputUnit = state.inputUnit,
                        inputAmount = state.inputAmount,
                        errorMessage = state.errorMessage,
                        onBack = vm::clearSelection,
                        onServingsChanged = vm::onServingsChanged,
                        onServingUnitAmountChanged = vm::onServingUnitAmountChanged,
                        onGramsChanged = vm::onGramsChanged,
                        onInputUnitChanged = vm::onInputUnitChanged,
                        onInputAmountChanged = { amount ->
                            // keep behavior identical to before: only forward when non-null
                            amount?.let { vm.onInputAmountChanged(it) }
                        },
                        onPackage = vm::onPackageClicked,
                        onEditFoodInEditor = { onOpenFoodEditor(selected.id) },
                        primaryButtonLabel = "Log",
                        isPrimaryEnabled = isLogEnabled,
                        onPrimaryAction = { vm.save(onDone = onDismiss, logDate = logDate) },
                        extraContent = {
                            if (selected.isRecipe) {
                                Spacer(Modifier.height(16.dp))
                                Text("Cooked batch", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                BatchSelector(
                                    batches = state.batches,
                                    selectedBatchId = state.selectedBatchId,
                                    onSelected = vm::onBatchSelected,
                                    onCreate = vm::openCreateBatchDialog
                                )
                                if (isLoggingByGrams && state.selectedBatchId == null && state.errorMessage == null) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Select or create a cooked batch to log by grams.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    )
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
    results: List<FoodListItemUiModel>,
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
        items(results, key = { it.food.id }) { item ->
            val food = item.food
            Log.d("Meow", "QuickAdd search > ${item.food.name} flags:${item.goalFlags.toString()}")
            ListItem(
                headlineContent = { Text(food.name) },
                supportingContent = {
                    val subtitle = buildString {
                        if (!food.brand.isNullOrBlank()) append(food.brand).append(" • ")
                        append("${food.servingSize.clean()} ${food.servingUnit}")
                        food.gramsPerServingUnitResolved()?.let { append(" (${it.clean()} g)") }
                    }
                    Text(subtitle)
                },
                trailingContent = {
                    FoodGoalFlagsStrip(item.goalFlags)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(item.food) }
            )
            HorizontalDivider()
        }
    }
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
                    label = { Text("Servings for batch (optional)") },
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
