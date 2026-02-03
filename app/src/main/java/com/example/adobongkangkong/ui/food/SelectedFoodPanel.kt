package com.example.adobongkangkong.ui.food

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.isVolumeUnit
import com.example.adobongkangkong.domain.model.isMassUnit
import com.example.adobongkangkong.domain.model.convertVolume
import com.example.adobongkangkong.domain.nutrition.gramsPerServingResolved
import kotlin.math.max
import kotlin.math.abs
import java.util.Locale
import com.example.adobongkangkong.R

/**
 * Shared, QuickAdd-canonical "selected food" input panel.
 *
 * This composable intentionally stays UI-focused and pushes state management to the caller
 * (typically a ViewModel), so it can be reused by both QuickAdd and RecipeBuilder.
 *
 * ## UX contract (must remain identical across callers)
 * - Selected food header + Change
 * - Servings row with +/- (canonical)
 * - Amount in food.servingUnit (number field)
 * - Grams field + unit button opens UnitToGramsDialog
 * - If missing grams-per-serving (and unit isn't grams), show blocking card with “Edit food”
 * - Package chips (½ package, 1 package) if servingsPerPackage exists
 * - Primary action button label is provided by caller (e.g., "Log" vs "Add ingredient")
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
    Column(
        modifier = modifier
            .fillMaxWidth()

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

        // Amount in the food's own serving unit.
        // Amount in the food's own serving unit.
        // Add a unit button (like the grams row) that opens a volume-input dialog for convertible volume units.
        val canOpenServingVolumeDialog = food.servingUnit.isVolumeUnit() && (food.gramsPerServingResolved() != null)
        var isServingUnitDialogOpen by rememberSaveable { mutableStateOf(false) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                NumberField(
                    label = "Amount (${food.servingUnit})",
                    value = servingUnitAmount,
                    onValue = { amt ->
                        onServingUnitAmountChanged(amt)
                        // Clear "entered as" once user provides a valid canonical amount.
                        onInputUnitChanged(food.servingUnit)
                        onInputAmountChanged(amt)
                    }
                )
            }

            Spacer(Modifier.width(8.dp))

            IconButton(
                onClick = { isServingUnitDialogOpen = true }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.exchange),
                    contentDescription = "Change serving unit"
                )
            }
        }


        val showEnteredAsVolume = inputAmount != null &&
                inputAmount > 0.0 &&
                inputUnit.isVolumeUnit() &&
                !inputUnit.isMassUnit() &&
                inputUnit != food.servingUnit

        if (showEnteredAsVolume) {
            Spacer(Modifier.height(4.dp))
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
                    // Keep the process identical to the grams unit flow:
                    // we store the user's entered (unit, amount) in inputUnit/inputAmount,
                    // and let the ViewModel derive servings/grams/canonical amounts from it.
                    onInputUnitChanged(unit)
                    onInputAmountChanged(amount)
                    isServingUnitDialogOpen = false
                }
            )
        }

        Spacer(Modifier.height(10.dp))

        // Grams input if available
        val canLogGrams = food.gramsPerServingResolved() != null
        val gramsDefault = servings * (food.gramsPerServingResolved() ?: 0.0)
        if (canLogGrams) {
            var isUnitDialogOpen by rememberSaveable { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    NumberField(
                        label = "Grams (g)",
                        value = gramsAmount ?: gramsDefault,
                        onValue = { grams ->
                            onGramsChanged(grams)
                            // When user types canonical grams, clear the 'entered as' hint by setting input to (g, grams).
                            onInputUnitChanged(ServingUnit.G)
                            onInputAmountChanged(grams)
                        }
                    )
                }

                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = { isUnitDialogOpen = true }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.exchange),
                        contentDescription = "Change serving unit"
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

        if (showEnteredAsMass) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Entered as ${inputAmount.cleanDynamic()} ${inputUnit.display}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // If the unit is not grams and we don't have grams-per-serving, offer a shortcut to the editor.
        val needsGramsPerServing = (food.servingUnit != ServingUnit.G) && (food.gramsPerServingResolved() == null)
        if (needsGramsPerServing) {
            Spacer(Modifier.height(8.dp))
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
                            text = "Missing grams-per-serving",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Add grams-per-serving in the food editor to enable gram-based logging and accurate conversions.",
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

        // Package buttons
        if (food.servingsPerPackage != null) {
            Spacer(Modifier.height(8.dp))
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

        // Optional caller-specific content (e.g., recipe cooked batch selector in QuickAdd)
        extraContent()

        errorMessage?.let { msg ->
            Spacer(Modifier.height(8.dp))
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

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (${selectedUnit.display})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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

    var expanded by remember { mutableStateOf(false) }
    val coercedInitial = remember(unitOptions, food) {
        if (initialUnit in unitOptions) initialUnit else food.servingUnit.coerceToAvailable(unitOptions)
    }
    var selectedUnit by remember { mutableStateOf(coercedInitial) }
    var amountText by remember { mutableStateOf(initialAmount?.toString().orEmpty()) }

    LaunchedEffect(unitOptions) {
        selectedUnit = if (selectedUnit in unitOptions) selectedUnit else food.servingUnit.coerceToAvailable(unitOptions)
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

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (${selectedUnit.display})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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

private fun buildQuickAddServingVolumeUnits(food: Food): List<ServingUnit> {
    // Only show the ladder when the food's serving unit is a volume unit; otherwise return empty.
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
    // Keep only units that are actually convertible with existing rules.
    return ladder.filter { it.isVolumeUnit() }
}

private fun buildQuickAddInputUnits(food: Food): List<ServingUnit> {
    val massUnits = listOf(
        ServingUnit.G,
        ServingUnit.OZ,
        ServingUnit.LB,
        ServingUnit.KG
    )

    val canVolumeInput = food.gramsPerServingResolved() != null && food.servingUnit.isVolumeUnitForDensity()
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

        // Legacy aliases treated as US volume (but not offered in picker)
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

    // Default: 2 decimals. For very small values, show enough decimals to include at least one non-zero digit.
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