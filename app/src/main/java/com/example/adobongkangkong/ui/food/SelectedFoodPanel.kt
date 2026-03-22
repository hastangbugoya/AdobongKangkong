package com.example.adobongkangkong.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.R
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.isMassUnit
import com.example.adobongkangkong.domain.model.isVolumeUnit
import com.example.adobongkangkong.domain.nutrition.gramsPerServingResolved
import com.example.adobongkangkong.domain.nutrition.gramsPerServingUnitResolved
import com.example.adobongkangkong.ui.theme.AppIconSize
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Shared, QuickAdd-canonical "selected food" input panel.
 *
 * This composable intentionally stays UI-focused and pushes state management to the caller
 * (typically a ViewModel), so it can be reused by both QuickAdd and RecipeBuilder.
 *
 * ## UX contract (must remain identical across callers)
 * - Selected food header + Change
 * - Servings row with +/- (canonical)
 * - Amount in food.servingUnit (number field) when it adds distinct value
 * - Grams field + unit button opens UnitToGramsDialog when grams logging is available
 * - mL field + unit button opens UnitToMillilitersDialog when mL logging is available
 * - If missing grams-per-serving (and unit isn't grams), show blocking card with “Edit food”
 * - Package chips (½ package, 1 package) if servingsPerPackage exists
 * - Primary action button label is provided by caller (e.g., "Log" vs "Add ingredient")
 *
 * Quantity presentation rules:
 * - If the serving unit is already mass-based (e.g. G / OZ), do not also show a separate
 *   "Amount (UNIT)" field because it is redundant with grams.
 * - If the serving unit is non-mass, the serving-unit amount field remains available.
 *
 * Callback rules:
 * - A direct edit in a visible field should emit one primary semantic callback.
 * - Unit/amount dialog apply paths still use the generic unit + amount callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectedFoodPanel(
    food: Food,
    servings: Double,
    servingUnitAmount: Double,
    gramsAmount: Double?,
    inputUnit: ServingUnit,
    inputAmount: Double?,
    errorMessage: String?,
    onBack: () -> Unit,
    onServingsChanged: (Double) -> Unit,
    onServingUnitAmountChanged: (Double) -> Unit,
    onGramsChanged: (Double) -> Unit,
    onInputUnitChanged: (ServingUnit) -> Unit,
    onInputAmountChanged: (Double?) -> Unit,
    onPackage: (Double) -> Unit,
    onEditFoodInEditor: () -> Unit,
    primaryButtonLabel: String,
    isPrimaryEnabled: Boolean = true,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
    extraContent: @Composable ColumnScope.() -> Unit = {}
) {
    val servingUnitIsMass = food.servingUnit.isMassUnit()
    val canLogGrams = food.gramsPerServingUnitResolved() != null
    val canLogMilliliters = food.mlPerServingUnit != null
    val showServingUnitAmountField = !servingUnitIsMass
    val showGramsField = canLogGrams
    val showMillilitersField = canLogMilliliters

    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(food.name, style = MaterialTheme.typography.titleMedium)

                val gramsPerServing: Double? = food.gramsPerServingResolved()

                Text(
                    "${food.servingSize.clean()} ${food.servingUnit.display}" +
                            (gramsPerServing?.let { " (${it.clean()} g)" } ?: ""),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onBack) { Text("Change") }
        }

        Spacer(Modifier.size(12.dp))

        AmountRow(
            label = "Servings",
            value = servings,
            unit = "",
            onMinus = { onServingsChanged(max(0.0, servings - 0.5)) },
            onPlus = { onServingsChanged(servings + 0.5) }
        )

        Spacer(Modifier.size(10.dp))

        if (showServingUnitAmountField) {
            val canOpenServingVolumeDialog =
                food.servingUnit.isVolumeUnit() &&
                        (food.gramsPerServingUnitResolved() != null || food.mlPerServingUnit != null)
            var isServingUnitDialogOpen by rememberSaveable { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    NumberField(
                        label = "Amount (${food.servingUnit.display})",
                        value = servingUnitAmount,
                        onValue = onServingUnitAmountChanged
                    )
                }

                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = { isServingUnitDialogOpen = true },
                    enabled = canOpenServingVolumeDialog
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.exchange),
                        contentDescription = "Change serving unit",
                        modifier = Modifier.size(AppIconSize.CardAction),
                    )
                }
            }

            val showEnteredAsVolumeForServingUnit = !showMillilitersField &&
                    inputAmount != null &&
                    inputAmount > 0.0 &&
                    inputUnit.isVolumeUnit() &&
                    !inputUnit.isMassUnit() &&
                    inputUnit != food.servingUnit

            if (showEnteredAsVolumeForServingUnit) {
                Spacer(Modifier.size(4.dp))
                Text(
                    text = "Entered as ${inputAmount.cleanDynamic()} ${inputUnit.display}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isServingUnitDialogOpen) {
                UnitToServingUnitDialog(
                    food = food,
                    initialUnit = inputUnit,
                    initialAmount = inputAmount,
                    onDismiss = { isServingUnitDialogOpen = false },
                    onApply = { unit, amount ->
                        onInputUnitChanged(unit)
                        onInputAmountChanged(amount)
                        isServingUnitDialogOpen = false
                    }
                )
            }

            Spacer(Modifier.size(10.dp))
        }

        if (showGramsField) {
            val gramsDefault = servings * (food.gramsPerServingResolved() ?: 0.0)
            var isUnitDialogOpen by rememberSaveable { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    NumberField(
                        label = "Grams (g)",
                        value = gramsAmount ?: gramsDefault,
                        onValue = onGramsChanged
                    )
                }

                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = { isUnitDialogOpen = true }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.exchange),
                        contentDescription = "Change gram input unit",
                        modifier = Modifier.size(AppIconSize.CardAction),
                    )
                }
            }

            if (isUnitDialogOpen) {
                UnitToGramsDialog(
                    food = food,
                    initialUnit = inputUnit,
                    initialAmount = inputAmount,
                    onDismiss = { isUnitDialogOpen = false },
                    onApply = { unit, amount ->
                        onInputUnitChanged(unit)
                        onInputAmountChanged(amount)
                        isUnitDialogOpen = false
                    }
                )
            }
        }

        val showEnteredAsMass = inputAmount != null &&
                inputAmount > 0.0 &&
                inputUnit.isMassUnit() &&
                inputUnit != ServingUnit.G

        if (showEnteredAsMass && showGramsField) {
            Spacer(Modifier.size(4.dp))
            Text(
                text = "Entered as ${inputAmount.cleanDynamic()} ${inputUnit.display}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (showMillilitersField) {
            val millilitersDefault = servings * (food.mlPerServingUnit ?: 0.0)
            val displayedMilliliters =
                if (inputUnit == ServingUnit.ML && inputAmount != null && inputAmount > 0.0) {
                    inputAmount
                } else {
                    millilitersDefault
                }

            var isMillilitersDialogOpen by rememberSaveable { mutableStateOf(false) }

            Spacer(Modifier.size(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    NumberField(
                        label = "Milliliters (mL)",
                        value = displayedMilliliters,
                        onValue = { ml ->
                            onInputUnitChanged(ServingUnit.ML)
                            onInputAmountChanged(ml)
                        }
                    )
                }

                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = { isMillilitersDialogOpen = true }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.exchange),
                        contentDescription = "Change milliliter input unit",
                        modifier = Modifier.size(AppIconSize.CardAction),
                    )
                }
            }

            val showEnteredAsVolumeForMilliliters = inputAmount != null &&
                    inputAmount > 0.0 &&
                    inputUnit.isVolumeUnit() &&
                    !inputUnit.isMassUnit() &&
                    inputUnit != ServingUnit.ML

            if (showEnteredAsVolumeForMilliliters) {
                Spacer(Modifier.size(4.dp))
                Text(
                    text = "Entered as ${inputAmount.cleanDynamic()} ${inputUnit.display}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isMillilitersDialogOpen) {
                UnitToMillilitersDialog(
                    initialUnit = inputUnit,
                    initialAmount = inputAmount,
                    onDismiss = { isMillilitersDialogOpen = false },
                    onApply = { unit, amount ->
                        onInputUnitChanged(unit)
                        onInputAmountChanged(amount)
                        isMillilitersDialogOpen = false
                    }
                )
            }
        }

        val hasGramBridge = food.gramsPerServingUnitResolved() != null
        val hasMlBridge = food.mlPerServingUnit != null

        val needsRecipeBridge = !hasGramBridge && !hasMlBridge

        if (needsRecipeBridge) {
            Spacer(Modifier.size(8.dp))
            Surface(
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Missing recipe bridge",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Add grams-per-serving or mL-per-serving to enable recipe conversion.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = onEditFoodInEditor) {
                        Text("Edit food")
                    }
                }
            }
        }

        if (food.servingsPerPackage != null) {
            Spacer(Modifier.size(8.dp))
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
            Spacer(Modifier.size(12.dp))
        }

        extraContent()

        errorMessage?.let { msg ->
            Spacer(Modifier.size(8.dp))
            Text(
                msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Button(
            onClick = onPrimaryAction,
            enabled = isPrimaryEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(primaryButtonLabel)
        }
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
        horizontalArrangement = Arrangement.SpaceBetween,
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
    var text by rememberSaveable(label) { mutableStateOf(value.clean()) }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(value, isFocused) {
        if (!isFocused) {
            text = value.clean()
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            newText.toDoubleOrNull()?.let(onValue)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                val nowFocused = focusState.isFocused
                if (isFocused && !nowFocused) {
                    val parsed = text.toDoubleOrNull()
                    text = if (parsed != null) {
                        parsed.clean()
                    } else {
                        value.clean()
                    }
                }
                isFocused = nowFocused
            }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitToGramsDialog(
    food: Food,
    initialUnit: ServingUnit,
    initialAmount: Double?,
    onDismiss: () -> Unit,
    onApply: (ServingUnit, Double?) -> Unit
) {
    val unitOptions = remember { buildQuickAddMassInputUnits() }

    var expanded by remember { mutableStateOf(false) }
    var selectedUnit by remember { mutableStateOf(initialUnit.coerceToAvailable(unitOptions)) }
    var amountText by remember { mutableStateOf(initialAmount?.toString().orEmpty()) }

    LaunchedEffect(unitOptions) {
        selectedUnit = selectedUnit.coerceToAvailable(unitOptions)
    }

    val parsedAmount: Double? = amountText.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Input amount") },
        text = {
            Column {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedUnit.display,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Unit") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        unitOptions.forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unit.display) },
                                onClick = {
                                    selectedUnit = unit
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.size(12.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (${selectedUnit.display})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(selectedUnit, parsedAmount) },
                enabled = parsedAmount != null && parsedAmount > 0.0
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitToMillilitersDialog(
    initialUnit: ServingUnit,
    initialAmount: Double?,
    onDismiss: () -> Unit,
    onApply: (ServingUnit, Double?) -> Unit
) {
    val unitOptions = remember { buildQuickAddVolumeInputUnits() }

    var expanded by remember { mutableStateOf(false) }
    var selectedUnit by remember { mutableStateOf(initialUnit.coerceToAvailable(unitOptions)) }
    var amountText by remember { mutableStateOf(initialAmount?.toString().orEmpty()) }

    LaunchedEffect(unitOptions) {
        selectedUnit = selectedUnit.coerceToAvailable(unitOptions)
    }

    val parsedAmount: Double? = amountText.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Input amount") },
        text = {
            Column {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedUnit.display,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Unit") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        unitOptions.forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unit.display) },
                                onClick = {
                                    selectedUnit = unit
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.size(12.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (${selectedUnit.display})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(selectedUnit, parsedAmount) },
                enabled = parsedAmount != null && parsedAmount > 0.0
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitToServingUnitDialog(
    food: Food,
    initialUnit: ServingUnit,
    initialAmount: Double?,
    onDismiss: () -> Unit,
    onApply: (ServingUnit, Double?) -> Unit
) {
    val unitOptions = remember(food) { buildQuickAddServingVolumeUnits(food) }

    if (unitOptions.isEmpty()) {
        onDismiss()
        return
    }

    var expanded by remember { mutableStateOf(false) }
    val coercedInitial = remember(unitOptions, food) {
        if (initialUnit in unitOptions) initialUnit else food.servingUnit.coerceToAvailable(unitOptions)
    }
    var selectedUnit by remember { mutableStateOf(coercedInitial) }
    var amountText by remember { mutableStateOf(initialAmount?.toString().orEmpty()) }

    LaunchedEffect(unitOptions) {
        selectedUnit =
            if (selectedUnit in unitOptions) selectedUnit
            else food.servingUnit.coerceToAvailable(unitOptions)
    }

    val parsedAmount: Double? = amountText.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Input amount") },
        text = {
            Column {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedUnit.display,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Unit") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        unitOptions.forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unit.display) },
                                onClick = {
                                    selectedUnit = unit
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.size(12.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (${selectedUnit.display})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(selectedUnit, parsedAmount) },
                enabled = parsedAmount != null && parsedAmount > 0.0
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun buildQuickAddMassInputUnits(): List<ServingUnit> = listOf(
    ServingUnit.G,
    ServingUnit.OZ,
    ServingUnit.LB,
    ServingUnit.KG
)

private fun buildQuickAddVolumeInputUnits(): List<ServingUnit> = listOf(
    ServingUnit.ML,
    ServingUnit.L,
    ServingUnit.TSP_US,
    ServingUnit.TBSP_US,
    ServingUnit.FL_OZ_US,
    ServingUnit.CUP_US,
    ServingUnit.PINT_US,
    ServingUnit.QUART_US,
    ServingUnit.GALLON_US,
    ServingUnit.CUP_METRIC,
    ServingUnit.CUP_JP,
    ServingUnit.RCCUP,
    ServingUnit.FL_OZ_IMP,
    ServingUnit.PINT_IMP,
    ServingUnit.QUART_IMP,
    ServingUnit.GALLON_IMP
)

private fun buildQuickAddServingVolumeUnits(food: Food): List<ServingUnit> {
    if (!food.servingUnit.isVolumeUnit()) return emptyList()

    val ladder = listOf(
        ServingUnit.GALLON_US,
        ServingUnit.QUART_US,
        ServingUnit.PINT_US,
        ServingUnit.CUP_US,
        ServingUnit.RCCUP,
        ServingUnit.TBSP_US,
        ServingUnit.TSP_US
    )
    return ladder.filter { it.isVolumeUnit() }
}

private fun buildQuickAddInputUnits(food: Food): List<ServingUnit> {
    val massUnits = listOf(
        ServingUnit.G,
        ServingUnit.OZ,
        ServingUnit.LB,
        ServingUnit.KG
    )

    val canVolumeInput =
        food.gramsPerServingUnitResolved() != null && food.servingUnit.isVolumeUnitForDensity()
    if (!canVolumeInput) return massUnits

    val volumeUnits = listOf(
        ServingUnit.ML,
        ServingUnit.L,
        ServingUnit.TSP_US,
        ServingUnit.TBSP_US,
        ServingUnit.FL_OZ_US,
        ServingUnit.CUP_US,
        ServingUnit.PINT_US,
        ServingUnit.QUART_US,
        ServingUnit.GALLON_US,
        ServingUnit.CUP_METRIC,
        ServingUnit.CUP_JP,
        ServingUnit.RCCUP,
        ServingUnit.FL_OZ_IMP,
        ServingUnit.PINT_IMP,
        ServingUnit.QUART_IMP,
        ServingUnit.GALLON_IMP
    )

    return massUnits + volumeUnits
}

private fun ServingUnit.isVolumeUnitForDensity(): Boolean = when (this) {
    ServingUnit.ML,
    ServingUnit.L,
    ServingUnit.TSP_US,
    ServingUnit.TBSP_US,
    ServingUnit.FL_OZ_US,
    ServingUnit.CUP_US,
    ServingUnit.PINT_US,
    ServingUnit.QUART_US,
    ServingUnit.GALLON_US,
    ServingUnit.CUP_METRIC,
    ServingUnit.CUP_JP,
    ServingUnit.RCCUP,
    ServingUnit.FL_OZ_IMP,
    ServingUnit.PINT_IMP,
    ServingUnit.QUART_IMP,
    ServingUnit.GALLON_IMP,
    ServingUnit.TSP,
    ServingUnit.TBSP,
    ServingUnit.CUP,
    ServingUnit.QUART -> true

    else -> false
}

private fun ServingUnit.coerceToAvailable(options: List<ServingUnit>): ServingUnit {
    if (this in options) return this
    return options.firstOrNull { it == ServingUnit.G } ?: options.first()
}

private fun Double.clean(): String =
    if (this % 1.0 == 0.0) this.toInt().toString() else cleanDynamic()

private fun Double.cleanDynamic(): String {
    val v = this
    val absV = abs(v)

    val decimals = if (absV == 0.0) {
        2
    } else if (absV >= 0.01) {
        2
    } else {
        var d = 3
        while (d <= 6) {
            val scaled = absV * Math.pow(10.0, d.toDouble())
            if (kotlin.math.round(scaled) != 0.0) break
            d++
        }
        d.coerceAtMost(6)
    }

    val raw = String.format(Locale.US, "%.${decimals}f", v)
    return raw.trimEnd('0').trimEnd('.')
}