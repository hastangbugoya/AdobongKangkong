package com.example.adobongkangkong.ui.food.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.domain.model.ServingUnit

private val ScreenPadding = 16.dp
private val CardPadding = 12.dp
private val FieldSpacing = 10.dp
private val SectionSpacing = 16.dp

/**
 * Food editor screen (create/edit).
 *
 * Responsibilities:
 * - Edit food basics (name, brand, serving size/unit, grams/serving, servings/package).
 * - Show food nutrient rows grouped by category.
 * - Add nutrients via a bottom-sheet search (smart search includes aliases).
 * - Manage nutrient aliases via a dedicated bottom sheet opened from a nutrient row menu.
 *
 * Alias UX (polished, discrete):
 * - Each nutrient row has an overflow menu (⋮) containing:
 *   - "Aliases…" → opens alias management sheet
 *   - "Remove" → removes the nutrient row from the food
 *
 * Notes:
 * - This screen is intentionally “single-file” for v1 iteration speed.
 *   You can split sub-composables into separate files later once it stabilizes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEditorScreen(
    foodId: Long?,
    initialName: String?,
    onBack: () -> Unit,
    vm: FoodEditorViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    // One-time load for create/edit
    LaunchedEffect(foodId, initialName) {
        vm.load(foodId, initialName)
    }

    val canSave = state.name.trim().isNotEmpty() && !state.isSaving
    var showAddNutrient by remember { mutableStateOf(false) }

    // Alias sheet state
    val selectedAliases by vm.selectedAliases.collectAsState()
    val aliasSheetNutrientName by vm.aliasSheetNutrientName.collectAsState()

    val aliasMessage by vm.aliasSheetMessage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (foodId == null) "New Food" else "Edit Food") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    TextButton(
                        onClick = { vm.save { onBack() } },
                        enabled = canSave
                    ) {
                        Text(if (state.isSaving) "Saving…" else "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(ScreenPadding)
        ) {
            ErrorBanner(message = state.errorMessage)

            BasicsCard(
                state = state,
                onNameChange = vm::onNameChange,
                onBrandChange = vm::onBrandChange,
                onServingSizeChange = vm::onServingSizeChange,
                onServingUnitChange = vm::onServingUnitChange,
                onGramsPerServingChange = vm::onGramsPerServingChange,
                onServingsPerPackageChange = vm::onServingsPerPackageChange,
                onFavoriteChange = vm::onFavoriteChange,
                onEatMoreChange = vm::onEatMoreChange,
                onLimitChange = vm::onLimitChange,
                onOpenLbDialog = vm::openLbDialog
            )

            Spacer(Modifier.height(SectionSpacing))

            NutrientsCard(
                rows = state.nutrientRows,
                onAddClick = { showAddNutrient = true },
                onAmountChange = { nutrientId, value -> vm.onNutrientAmountChange(nutrientId, value) },
                onRemove = { nutrientId -> vm.removeNutrientRow(nutrientId) },
                onAliases = { nutrientId, name -> vm.openAliasSheet(nutrientId, name) }
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    if (state.isLbDialogOpen) {
        AlertDialog(
            onDismissRequest = vm::closeLbDialog,
            title = { Text("Enter pounds (lb)") },
            text = {
                OutlinedTextField(
                    value = state.lbInputText,
                    onValueChange = vm::onLbInputChange,
                    label = { Text("Pounds") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = vm::confirmLbToGrams) { Text("Convert") }
            },
            dismissButton = {
                TextButton(onClick = vm::closeLbDialog) { Text("Cancel") }
            }
        )
    }

    // Alias management sheet (opened from nutrient row overflow menu)
    if (aliasSheetNutrientName != null) {
        ManageNutrientAliasesBottomSheet(
            nutrientDisplayName = aliasSheetNutrientName ?: "Nutrient",
            aliases = selectedAliases,
            message = aliasMessage,
            onAddAlias = vm::addAlias,
            onDeleteAlias = vm::deleteAlias,
            onDismiss = vm::closeAliasSheet
        )
    }

    // Add nutrient search sheet
    if (showAddNutrient) {
        AddNutrientBottomSheet(
            query = state.nutrientSearchQuery,
            results = state.nutrientSearchResults,
            onQueryChange = vm::onNutrientSearchQueryChange,
            onPick = { picked ->
                vm.addNutrient(picked)
                showAddNutrient = false
            },
            onDismiss = { showAddNutrient = false }
        )
    }
}

/**
 * Simple error banner shown at the top of the screen when a user-visible error occurs.
 */
@Composable
private fun ErrorBanner(message: String?) {
    if (message.isNullOrBlank()) return
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error
    )
    Spacer(Modifier.height(12.dp))
}

/**
 * Card section for basic food metadata.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BasicsCard(
    state: FoodEditorState,
    onNameChange: (String) -> Unit,
    onBrandChange: (String) -> Unit,
    onServingSizeChange: (String) -> Unit,
    onServingUnitChange: (ServingUnit) -> Unit,
    onGramsPerServingChange: (String) -> Unit,
    onServingsPerPackageChange: (String) -> Unit,
    onFavoriteChange: (Boolean) -> Unit,
    onEatMoreChange: (Boolean) -> Unit,
    onLimitChange: (Boolean) -> Unit,
    onOpenLbDialog: () -> Unit
    ) {
    Card {
        Column(Modifier.padding(CardPadding)) {
            Text("Basics", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(FieldSpacing))

            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name*") },
                singleLine = true
            )

            Spacer(Modifier.height(FieldSpacing))

            OutlinedTextField(
                value = state.brand,
                onValueChange = onBrandChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Brand (optional)") },
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.servingSize,
                    onValueChange = onServingSizeChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Serving size") },
                    singleLine = true
                )

                ServingUnitDropdown(
                    value = state.servingUnit,
                    onChange = onServingUnitChange,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(FieldSpacing))

            OutlinedTextField(
                value = state.gramsPerServing,
                onValueChange = onGramsPerServingChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Grams per serving (optional)") },
                singleLine = true,
                trailingIcon = {
                    // Tap opens lb dialog (or use long-press on this icon if you want)
                    IconButton(onClick = onOpenLbDialog) {
                        Text("lb")
                    }
                }
            )


//            Box(
//                modifier = Modifier.combinedClickable(
//                    onClick = {},
//                    onLongClick = { onOpenLbDialog() }
//                )
//            ) {
//                OutlinedTextField(
//                    value = state.gramsPerServing,
//                    onValueChange = onGramsPerServingChange,
//                    modifier = Modifier.fillMaxWidth(),
//                    label = { Text("Grams per serving (optional)") },
//                    singleLine = true
//                )
//            }


            Spacer(Modifier.height(FieldSpacing))

            OutlinedTextField(
                value = state.servingsPerPackage,
                onValueChange = onServingsPerPackageChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Servings per package (optional)") },
                singleLine = true
            )

            Spacer(Modifier.height(SectionSpacing))
            Text("Flags", style = MaterialTheme.typography.titleMedium)
            FlagRow(
                label = "⭐ Favorite",
                checked = state.favorite,
                onCheckedChange = onFavoriteChange
            )

            FlagRow(
                label = "➕ Eat more of this",
                checked = state.eatMore,
                onCheckedChange = onEatMoreChange
            )

            FlagRow(
                label = "⚠ Limit this",
                checked = state.limit,
                onCheckedChange = onLimitChange
            )
        }
    }
}

/**
 * Card section for nutrient rows.
 *
 * - Rows are grouped by category.
 * - Each row supports inline amount edit.
 * - Overflow menu provides discrete actions:
 *   - "Aliases…" (opens alias sheet)
 *   - "Remove"
 */
@Composable
private fun NutrientsCard(
    rows: List<NutrientRowUi>,
    onAddClick: () -> Unit,
    onAmountChange: (nutrientId: Long, value: String) -> Unit,
    onRemove: (nutrientId: Long) -> Unit,
    onAliases: (nutrientId: Long, nutrientName: String) -> Unit
) {
    Card {
        Column(Modifier.padding(CardPadding)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Nutrients", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onAddClick) { Text("Add") }
            }

            Spacer(Modifier.height(8.dp))

            if (rows.isEmpty()) {
                Text("No nutrients yet. Tap Add.", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }

            rows
                .groupBy { it.category }
                .forEach { (category, group) ->
                    Text(category.displayName, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))

                    group.forEach { row ->
                        NutrientRow(
                            row = row,
                            onAmountChange = { value -> onAmountChange(row.nutrientId, value) },
                            onRemove = { onRemove(row.nutrientId) },
                            onAliases = { onAliases(row.nutrientId, row.name) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                }
        }
    }
}

/**
 * Single nutrient line item:
 * - label + unit
 * - editable amount field
 * - overflow actions (Aliases/Remove)
 */
@Composable
private fun NutrientRow(
    row: NutrientRowUi,
    onAmountChange: (String) -> Unit,
    onRemove: () -> Unit,
    onAliases: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                row.unit.symbol,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedTextField(
            value = row.amount,
            onValueChange = onAmountChange,
            modifier = Modifier.width(120.dp),
            label = { Text("Amt") },
            singleLine = true
        )

        NutrientRowOverflowMenu(
            onAliases = onAliases,
            onRemove = onRemove
        )
    }
}

/**
 * Overflow menu for a nutrient row.
 *
 * Keeping this as a separate composable avoids repeating menuExpanded state and
 * keeps the nutrient row readable.
 */
@Composable
private fun NutrientRowOverflowMenu(
    onAliases: () -> Unit,
    onRemove: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    IconButton(onClick = { menuExpanded = true }) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "More"
        )
    }

    DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = { menuExpanded = false }
    ) {
        DropdownMenuItem(
            text = { Text("Aliases…") },
            onClick = {
                menuExpanded = false
                onAliases()
            }
        )
        DropdownMenuItem(
            text = { Text("Remove") },
            onClick = {
                menuExpanded = false
                onRemove()
            }
        )
    }
}

/**
 * Serving unit dropdown for food logging (e.g., G, ML, TBSP).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServingUnitDropdown(
    value: ServingUnit,
    onChange: (ServingUnit) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
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
                    onClick = {
                        onChange(u)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Bottom sheet used to add a nutrient row to the food.
 *
 * - User types a query
 * - Results are shown (smart search includes aliases)
 * - Each result has a compact "Add" action
 *
 * This sheet intentionally does NOT expose alias editing to keep it focused.
 * Alias editing remains in the nutrient row overflow menu (discrete UX).
 */
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
        Column(
            Modifier
                .fillMaxWidth()
                .padding(ScreenPadding)
        ) {
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
                        supportingContent = {
                            // Keep this compact and human-friendly.
                            Text("${n.category.displayName} • ${n.unit.symbol}")
                        },
                        trailingContent = {
                            TextButton(onClick = { onPick(n) }) { Text("Add") }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider()
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FlagRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
    Spacer(Modifier.height(8.dp))
}

