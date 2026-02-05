package com.example.adobongkangkong.ui.food.editor

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.R
import com.example.adobongkangkong.domain.model.NutrientCategory
import com.example.adobongkangkong.domain.model.NutrientUnit
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.ui.camera.BannerCaptureController

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEditorScreen(
    state: FoodEditorState,

    // Navigation
    onBack: () -> Unit,

    // Core fields
    onNameChange: (String) -> Unit,
    onBrandChange: (String) -> Unit,
    onServingSizeChange: (String) -> Unit,
    onServingUnitChange: (ServingUnit) -> Unit,
    onGramsPerServingChange: (String) -> Unit,
    onServingsPerPackageChange: (String) -> Unit,

    // Nutrient rows
    onNutrientAmountChange: (nutrientId: Long, newAmount: String) -> Unit,
    onRemoveNutrient: (nutrientId: Long) -> Unit,

    // Nutrient search/add
    onNutrientSearchQueryChange: (String) -> Unit,
    onAddNutrientFromSearch: (nutrientId: Long) -> Unit,

    // Flags
    onToggleFavorite: (Boolean) -> Unit,
    onToggleEatMore: (Boolean) -> Unit,
    onToggleLimit: (Boolean) -> Unit,

    // Save/Delete
    onSave: () -> Unit,
    onDeleteFood: (() -> Unit)? = null,

    // Alias sheet
    aliasSheetNutrientName: String?,
    aliasSheetAliases: List<String>,
    aliasSheetMessage: String?,
    onOpenAliasSheet: (nutrientId: Long, nutrientName: String) -> Unit,
    onAddAlias: (String) -> Unit,
    onDeleteAlias: (String) -> Unit,
    onDismissAliasSheet: () -> Unit,

    // Camera
    bannerCaptureController: BannerCaptureController,
    bannerRefreshTick: Int,

    // Barcode
    onOpenBarcodeScanner: () -> Unit,
    onCloseBarcodeScanner: () -> Unit,
    onBarcodeScanned: (String) -> Unit,
    onPickBarcodeCandidate: (Long) -> Unit,

    ) {
    val attachedNutrientIds = remember(state.nutrientRows) {
        state.nutrientRows
            .map { it.nutrientId }
            .toSet()
    }

    // ---------------- Navigate-away warning (state-driven) ----------------
    val showExitDialog = rememberSaveable { mutableStateOf(false) }

    fun requestExit() {
        if (state.hasUnsavedChanges && !state.isSaving) {
            showExitDialog.value = true
        } else {
            onBack()
        }
    }

    BackHandler { requestExit() }
    // ----------------------------------------------------------------------

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.foodId == null) "New Food" else "Edit Food",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val canCaptureBanner = state.foodId != null
                    Spacer(Modifier.width(16.dp))
//                    TextButton(
//                        enabled = canCaptureBanner,
//                        onClick = { bannerCaptureController.open(state.foodId!!) }
//                    ) {
//                        Text("Open banner camera")
//                    }

                    if (state.foodId == null) {
                        Text(
                            "Save the food first to enable banner capture.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                },
                navigationIcon = {
                    IconButton(onClick = ::requestExit) {
                        Icon(painter = painterResource(R.drawable.angle_circle_left), contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            FoodEditorBottomBar(
                isSaving = state.isSaving,
                errorMessage = state.errorMessage,
                showDelete = (onDeleteFood != null && state.foodId != null),
                onDelete = { onDeleteFood?.invoke() },
                onSave = onSave,
                bannerCaptureController = bannerCaptureController
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val isTablet = maxWidth > 600.dp

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 96.dp // space for bottom bar
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    val context = LocalContext.current

                    val bannerOwnerId = state.foodId
                    val canCaptureBanner = bannerOwnerId != null

                    if (canCaptureBanner) {
                        val file = remember(bannerOwnerId ) {
                            com.example.adobongkangkong.feature.camera.FoodImageStorage(context)
                                .bannerJpegFile(bannerOwnerId )
                        }

                        val bannerBitmapState = produceState<android.graphics.Bitmap?>(
                            initialValue = null,
                            key1 = bannerOwnerId ,
                            key2 = bannerRefreshTick
                        ) {
                            value = if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                        }

                        bannerBitmapState.value?.let { bmp ->
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(3f / 1f)
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.matchParentSize(),
                                    contentScale = ContentScale.Crop
                                )

                                IconButton(
                                    onClick = { bannerCaptureController.open(bannerOwnerId ) },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(android.R.drawable.ic_menu_camera),
                                        contentDescription = "Change banner"
                                    )
                                }
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                enabled = canCaptureBanner,
                                onClick = { bannerCaptureController.open(bannerOwnerId!!) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Change banner",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        if (!canCaptureBanner) {
                            Text(
                                text = "Save first to enable banner image.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                }



                item {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = onNameChange,
                        label = { Text("Food name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.brand,
                        onValueChange = onBrandChange,
                        label = { Text("Brand") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = onOpenBarcodeScanner) {
                            Text("Scan barcode")
                        }
                        Spacer(Modifier.width(12.dp))
                        if (state.scannedBarcode.isNotBlank()) {
                            Text(
                                text = "Scanned: ${state.scannedBarcode}",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                item {
                    ServingSection(
                        servingSize = state.servingSize,
                        servingUnit = state.servingUnit,
                        gramsPerServingUnit = state.gramsPerServingUnit,
                        servingsPerPackage = state.servingsPerPackage,
                        onServingSizeChange = onServingSizeChange,
                        onServingUnitChange = onServingUnitChange,
                        onGramsPerServingChange = onGramsPerServingChange,
                        onServingsPerPackageChange = onServingsPerPackageChange,
                        isTablet = isTablet
                    )
                }

                item {
                    FlagsSection(
                        favorite = state.favorite,
                        eatMore = state.eatMore,
                        limit = state.limit,
                        onToggleFavorite = onToggleFavorite,
                        onToggleEatMore = onToggleEatMore,
                        onToggleLimit = onToggleLimit,
                        isTablet = isTablet
                    )
                }

                item { HorizontalDivider() }

                item {
                    SectionHeader(
                        title = "Nutrients",
                        subtitle = "Edit amounts (as entered) per serving unit."
                    )
                }

                // Group nutrient rows by category for readability and large-font safety.
                val grouped = state.nutrientRows.groupBy { it.category }

                grouped.entries
                .sortedBy { it.key.name } // optional: stable ordering by enum name
                .forEach { (category, rows) ->
                    item {
                        Text(
                            text = category.labelForUi(),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    items(
                        items = rows,
                        key = { it.nutrientId }
                    ) { row ->
                        NutrientRowEditor(
                            row = row,
                            onAmountChange = { newValue ->
                                onNutrientAmountChange(row.nutrientId, newValue)
                            },
                            onRemove = { onRemoveNutrient(row.nutrientId) },
                            isTablet = isTablet
                        )
                    }
                }

                item { HorizontalDivider() }

                item {
                    SectionHeader(
                        title = "Add nutrient",
                        subtitle = "Search nutrients and add them to this food."
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.nutrientSearchQuery,
                        onValueChange = onNutrientSearchQueryChange,
                        label = { Text("Search nutrients") },
//                        leadingIcon = { Icon(painter = painterResource(R.drawable.circle_ellipsis_vertical), contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (state.nutrientSearchResults.isNotEmpty()) {
                    item {
                        Text(
                            text = "Results",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(
                        items = state.nutrientSearchResults,
                        key = { it.id }
                    ) { item ->
                        val alreadyAdded = item.id in attachedNutrientIds

                        NutrientSearchResultRow(
                            item = item,
                            alreadyAdded = alreadyAdded,
                            onAdd = { onAddNutrientFromSearch(item.id) },
                            onManageAliases = { onOpenAliasSheet(item.id, item.name) }
                        )
                    }

                } else if (state.nutrientSearchQuery.isNotBlank()) {
                    item {
                        Text(
                            text = "No results.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Optional debugging info (stableId) — keep subtle.
                if (!state.stableId.isNullOrBlank()) {
                    item {
                        Text(
                            text = "StableId: ${state.stableId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }




    if (state.isBarcodeScannerOpen) {
        // Picker overlay (no frills) once we have candidates and > 1
        if (state.barcodePickItems.size > 1) {
            AlertDialog(
                onDismissRequest = onCloseBarcodeScanner,
                title = { Text("Pick a match") },
                text = {
                    LazyColumn {
                        items(state.barcodePickItems, key = { it.fdcId }) { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPickBarcodeCandidate(item.fdcId) }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(item.description.ifBlank { "(No description)" })
                                val line2 = listOf(item.brand, item.servingText, item.gtinUpc)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" • ")
                                if (line2.isNotBlank()) {
                                    Text(
                                        line2,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = onCloseBarcodeScanner) { Text("Cancel") }
                }
            )
        }
    }


    if (showExitDialog.value) {
        AlertDialog(
            onDismissRequest = { showExitDialog.value = false },
            title = { Text("Unsaved changes") },
            text = { Text("What would you like to do with your changes?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog.value = false
                        onSave()
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showExitDialog.value = false
                            onBack()
                        }
                    ) { Text("Discard") }

                    TextButton(onClick = { showExitDialog.value = false }) { Text("Cancel") }
                }
            }
        )
    }

    if (aliasSheetNutrientName != null) {
        ManageNutrientAliasesBottomSheet(
            nutrientDisplayName = aliasSheetNutrientName,
            aliases = aliasSheetAliases,
            message = aliasSheetMessage,
            onAddAlias = onAddAlias,
            onDeleteAlias = onDeleteAlias,
            onDismiss = onDismissAliasSheet
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FlagsSection(
    favorite: Boolean,
    eatMore: Boolean,
    limit: Boolean,
    onToggleFavorite: (Boolean) -> Unit,
    onToggleEatMore: (Boolean) -> Unit,
    onToggleLimit: (Boolean) -> Unit,
    isTablet: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Flags", style = MaterialTheme.typography.titleMedium)

        if (isTablet) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FlagCheck("Favorite", favorite, onToggleFavorite, Modifier.weight(1f))
                FlagCheck("Eat more", eatMore, onToggleEatMore, Modifier.weight(1f))
                FlagCheck("Limit", limit, onToggleLimit, Modifier.weight(1f))
            }
        } else {
            FlagCheck("Favorite", favorite, onToggleFavorite, Modifier.fillMaxWidth())
            FlagCheck("Eat more", eatMore, onToggleEatMore, Modifier.fillMaxWidth())
            FlagCheck("Limit", limit, onToggleLimit, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun FlagCheck(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    modifier: Modifier
) {
    Row(
        modifier = modifier
            .clickable { onChecked(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
    }
}

@Composable
private fun NutrientRowEditor(
    row: NutrientRowUi,
    onAmountChange: (String) -> Unit,
    onRemove: () -> Unit,
    isTablet: Boolean
) {
    // Layout that survives large fonts:
    // Line 1: name + unit + delete icon
    // Line 2: amount field (full width on phone; half width on tablet)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = row.unit.labelForUi(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onRemove) {
                Icon(painter = painterResource(R.drawable.trash), contentDescription = "Remove nutrient")
            }
        }

        OutlinedTextField(
            value = row.amount,
            onValueChange = onAmountChange,
            label = { Text("Amount") },
            singleLine = true,
            modifier = if (isTablet) Modifier.fillMaxWidth(0.6f) else Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun NutrientSearchResultRow(
    item: NutrientSearchResultUi,
    alreadyAdded: Boolean,
    onAdd: () -> Unit,
    onManageAliases: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (alreadyAdded)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "${item.unit.labelForUi()} • ${item.category.labelForUi()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TextButton(
            onClick = onAdd,
            enabled = !alreadyAdded
        ) {
            Text(if (alreadyAdded) "Added" else "Add")
        }

        IconButton(onClick = { menuExpanded = true }) {
            Icon(painter = painterResource(R.drawable.circle_ellipsis_vertical), contentDescription = "More")
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Manage aliases") },
                onClick = {
                    menuExpanded = false
                    onManageAliases()
                }
            )
        }
    }
}



@Composable
private fun ServingSection(
    servingSize: String,
    servingUnit: ServingUnit,
    gramsPerServingUnit: String,
    servingsPerPackage: String,
    onServingSizeChange: (String) -> Unit,
    onServingUnitChange: (ServingUnit) -> Unit,
    onGramsPerServingChange: (String) -> Unit,
    onServingsPerPackageChange: (String) -> Unit,
    isTablet: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "Serving", style = MaterialTheme.typography.titleMedium)

        if (isTablet) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = servingSize,
                    onValueChange = onServingSizeChange,
                    label = { Text("Serving size") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                ServingUnitDropdown(
                    value = servingUnit,
                    onValueChange = onServingUnitChange,
                    modifier = Modifier.weight(1f),
                    options = ServingUnit.values().toList()
                )
            }
        } else {
            OutlinedTextField(
                value = servingSize,
                onValueChange = onServingSizeChange,
                label = { Text("Serving size") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ServingUnitDropdown(
                value = servingUnit,
                onValueChange = onServingUnitChange,
                modifier = Modifier.fillMaxWidth(),
                options = ServingUnit.values().toList()
            )
        }

        // gramsPerServingUnit is critical for non-gram units; keep it prominent and full-width.
        OutlinedTextField(
            value = gramsPerServingUnit,
            onValueChange = onGramsPerServingChange,
            label = { Text("Grams per serving") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = servingsPerPackage,
            onValueChange = onServingsPerPackageChange,
            label = { Text("Servings per package") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Replace this dropdown with your existing ServingUnit picker implementation.
 * I’m keeping it minimal and compile-safe: a clickable text that cycles is NOT ideal.
 * If you already have a proper dropdown composable, swap it in here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServingUnitDropdown(
    value: ServingUnit,
    onValueChange: (ServingUnit) -> Unit,
    modifier: Modifier = Modifier,
    options: List<ServingUnit>,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value.display,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Serving unit") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit.display) },
                    onClick = {
                        expanded = false
                        if (unit != value) onValueChange(unit)
                    }
                )
            }
        }
    }
}


/**
 * Sticky bottom bar: always reachable with big fonts and long forms.
 */
@Composable
private fun FoodEditorBottomBar(
    isSaving: Boolean,
    errorMessage: String?,
    showDelete: Boolean,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    bannerCaptureController: BannerCaptureController
) {
    Surface(tonalElevation = 3.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showDelete) {
                    OutlinedButton(
                        onClick = onDelete,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f)
                    ) { Text("Delete") }
                }

                Button(
                    onClick = onSave,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f)
                ) { Text(if (isSaving) "Saving…" else "Save") }
            }
        }
    }
}

// --- UI labels (keep these local; change later if you want nicer names) ---

private fun NutrientCategory.labelForUi(): String = name.replace('_', ' ').lowercase()
    .replaceFirstChar { it.uppercase() }

private fun NutrientUnit.labelForUi(): String = name.lowercase()

private fun NutrientRowUi.categoryLabelForUi(): String = category.labelForUi()
