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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.R
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.domain.model.NutrientCategory
import com.example.adobongkangkong.domain.model.NutrientUnit
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.ui.camera.BannerCaptureController
import com.example.adobongkangkong.ui.common.food.GoalFlagsSection

/**
 * FoodEditorScreen (stateless)
 *
 * @since 2026-02-05
 *
 * Notes for future-you:
 * - Keep this composable stateless (no ViewModel calls).
 * - Delete MUST be triggered only via `onDeleteFood?.invoke()`.
 * - Modal UI blocks (barcode picker, exit dialog, alias sheet) must live inside this function body.
 */
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

    // Basis
    onPickBasisType: (BasisType) -> Unit,
    onDismissGroundingDialog: () -> Unit,

    mlPerServingUnit: String,
    onMlPerServingChange: (String) -> Unit,
    basisType: BasisType?,
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

    // ---------------- Barcode picker overlay (must be inside composable) ---
    if (state.isBarcodeScannerOpen) {
        // If you have camera scanning UI elsewhere, keep it driven by state and callbacks.
        // This dialog is the "pick a match" flow when candidates > 1.
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

    if (state.isGroundingDialogOpen) {
        AlertDialog(
            onDismissRequest = onDismissGroundingDialog,
            title = { Text("Serving unit needs a basis") },
            text = { Text("Is this measured by mass (solids) or volume (liquids)?") },
            confirmButton = {
                TextButton(onClick = { onPickBasisType(BasisType.PER_100G) }) {
                    Text("Mass (PER 100g)")
                }
            },
            dismissButton = {
                TextButton(onClick = { onPickBasisType(BasisType.PER_100ML) }) {
                    Text("Volume (PER 100mL)")
                }
            }
        )
    }
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
                },
                navigationIcon = {
                    IconButton(onClick = ::requestExit) {
                        Icon(
                            painter = painterResource(R.drawable.angle_circle_left),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        bottomBar = {
            FoodEditorBottomBar(
                isSaving = state.isSaving,
                errorMessage = state.errorMessage,
                showDelete = (onDeleteFood != null && state.foodId != null),
                onDelete = { onDeleteFood?.invoke() }, // ✅ only uses passed callback
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

                    val bannerFile = if (canCaptureBanner) {
                        remember(bannerOwnerId) {
                            com.example.adobongkangkong.feature.camera.FoodImageStorage(context)
                                .bannerJpegFile(bannerOwnerId)
                        }
                    } else null

                    val hasStoredBanner = bannerFile?.exists() == true

                    val bannerBitmapState = if (canCaptureBanner) {
                        produceState<android.graphics.Bitmap?>(
                            initialValue = null,
                            key1 = bannerOwnerId,
                            key2 = bannerRefreshTick
                        ) {
                            val file = bannerFile!!
                            value = if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                        }
                    } else {
                        null
                    }

                    val bmp = bannerBitmapState?.value
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 1f)
                            .clipToBounds()
                    ) {
                        // Banner image (real banner if decoded; otherwise placeholder)
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                alignment = Alignment.Center,
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.FillWidth
                            )
                        } else {
                            Image(
                                painter = painterResource(R.drawable.foods_banner),
                                contentDescription = null,
                                alignment = Alignment.Center,
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.FillWidth
                            )
                        }

                        // Always allow changing banner once the item has an id.
                        if (canCaptureBanner) {
                            IconButton(
                                onClick = { bannerCaptureController.open(bannerOwnerId!!) },
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

                        // If a stored banner exists but decoding hasn't finished yet, show a subtle spinner
                        // so the user understands the placeholder will be replaced.
                        if (hasStoredBanner && bmp == null) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(10.dp)
                                    .size(50.dp),
                                strokeWidth = 2.dp
                            )
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
                        isTablet = isTablet,
                        mlPerServingUnit = state.mlPerServingUnit,
                        onMlPerServingChange = onGramsPerServingChange,
                        basisType = state.basisType,

                    )
                }

                item {
                    Text("BasisType: ${state.basisType?.name}")
                }

                item {
                    GoalFlagsSection(
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

                val grouped = state.nutrientRows.groupBy { it.category }

                grouped.entries
                    .sortedBy { it.key.name }
                    .forEach { (category, rows) ->
                        item {
                            Text(
                                text = category.labelForUi(),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        itemsIndexed(
                            items = rows,
                            key = { index: Int, row: NutrientRowUi -> "${row.nutrientId}_$index" }
                        ) { _: Int, row: NutrientRowUi ->
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
                if (state.foodId != null) {
                    item {
                        Text(
                            text = "FoodId: ${state.foodId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                item {
                    Text("State", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = state.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // ---------------- Exit dialog (must be inside composable) -------------
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
    // ----------------------------------------------------------------------

    // ---------------- Alias sheet (must be inside composable) --------------
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
    // ----------------------------------------------------------------------
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
private fun NutrientRowEditor(
    row: NutrientRowUi,
    onAmountChange: (String) -> Unit,
    onRemove: () -> Unit,
    isTablet: Boolean
) {
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
    mlPerServingUnit: String,
    servingsPerPackage: String,
    onServingSizeChange: (String) -> Unit,
    onServingUnitChange: (ServingUnit) -> Unit,
    onGramsPerServingChange: (String) -> Unit,
    onMlPerServingChange: (String) -> Unit,
    onServingsPerPackageChange: (String) -> Unit,
    basisType: BasisType?,
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

        val useMlBridge = basisType == BasisType.PER_100ML || mlPerServingUnit.isNotBlank()

        if (useMlBridge) {
            OutlinedTextField(
                value = mlPerServingUnit,
                onValueChange = onMlPerServingChange,
                label = { Text("mL per serving") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            OutlinedTextField(
                value = gramsPerServingUnit,
                onValueChange = onGramsPerServingChange,
                label = { Text("Grams per serving") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }


        OutlinedTextField(
            value = servingsPerPackage,
            onValueChange = onServingsPerPackageChange,
            label = { Text("Servings per package") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

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