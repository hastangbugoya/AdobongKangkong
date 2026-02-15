package com.example.adobongkangkong.ui.food.editor

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.R
import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.domain.model.NutrientCategory
import com.example.adobongkangkong.domain.model.NutrientUnit
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.ui.camera.BannerCaptureController
import com.example.adobongkangkong.ui.common.food.GoalFlagsSection
import com.example.adobongkangkong.ui.common.sectionedByCategory

/**
 * FoodEditorScreen (stateless)
 *
 * UI-only form for creating/editing a Food.
 *
 * Design rules:
 * - **No ViewModel access**: all state is provided via [state] and all actions are delegated via callbacks.
 * - **All modal UI lives here** (dialogs / sheets) so it remains driven by [state] and local UI flags:
 *   - barcode candidate picker (only when multiple matches exist)
 *   - delete confirmation (soft delete + optional permanent delete)
 *   - grounding dialog (PER_100G vs PER_100ML choice)
 *   - barcode fallback dialog (USDA lookup failed → assign to existing or create minimal)
 *   - barcode remap confirmation (replace an existing barcode mapping)
 *   - exit dialog (unsaved changes)
 *   - alias-management bottom sheet
 *
 * Layout notes:
 * - Uses a [Scaffold] with a sticky bottom bar ([FoodEditorBottomBar]) so Save/Delete stay reachable.
 * - Long-form content is a single [LazyColumn] to avoid nested scrolling.
 *
 * Callback expectations:
 * - [onSave] should trigger persistence and eventually clear `state.hasUnsavedChanges`.
 * - [onDeleteFood] performs a soft delete; [onHardDeleteFood] performs a permanent delete (only when allowed).
 * - Barcode flow callbacks:
 *   - [onOpenBarcodeScanner]/[onCloseBarcodeScanner] toggle the scanner UI
 *   - [onBarcodeScanned] forwards the scanned string to the state owner
 *   - [onPickBarcodeCandidate] selects a USDA candidate when multiple results are present
 *   - [onBarcodeFallbackAssignExisting] routes to a picker to map barcode → existing food
 *   - [onBarcodeFallbackCreateMinimal] creates a minimal food entry then maps barcode → new food (may require remap confirm)
 *   - [onConfirmBarcodeRemap] confirms or cancels replacing an existing mapping
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
    onHardDeleteFood: (() -> Unit)? = null,

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

    // Barcode fallback UI + remap confirm
    onDismissBarcodeFallback: () -> Unit,
    onBarcodeFallbackAssignExisting: () -> Unit,
    onBarcodeFallbackCreateNameChange: (String) -> Unit,
    onBarcodeFallbackCreateMinimal: () -> Unit,
    onConfirmBarcodeRemap: (Boolean) -> Unit,
    onBarcodeFallbackOpenAssignedFood: (Long) -> Unit,
    onOpenFoodEditor: (Long) -> Unit,

    onUnassignBarcode: (String) -> Unit,

    ) {
    val attachedNutrientIds = remember(state.nutrientRows) {
        state.nutrientRows
            .map { it.nutrientId }
            .toSet()
    }

    // ---------------- Navigate-away warning (state-driven) ----------------
    val showExitDialog = rememberSaveable { mutableStateOf(false) }

    var showDeleteDialog by remember { mutableStateOf(false) }

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
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete food?") },
            text = {
                Text(
                    "Delete hides this food from lists, but keeps logs and history. " +
                            "Delete permanently is only allowed if it is unused."
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            onDeleteFood?.invoke()
                        }
                    ) { Text("Delete") }

                    if (onHardDeleteFood != null) {
                        TextButton(
                            onClick = {
                                showDeleteDialog = false
                                onHardDeleteFood()
                            }
                        ) { Text("Delete permanently") }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
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

    // ---------------- Barcode fallback: USDA failed ----------------
// ---------------- Barcode fallback: USDA failed ----------------
    if (state.isBarcodeFallbackOpen) {
        val alreadyAssignedFoodId = state.barcodeAlreadyAssignedFoodId
        val conflict = alreadyAssignedFoodId != null

        AlertDialog(
            onDismissRequest = onDismissBarcodeFallback,
            title = { Text(if (conflict) "Barcode already in use" else "Barcode not found") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val msg = state.barcodeFallbackMessage
                        ?: if (conflict) "This barcode is already assigned in your database."
                        else "This barcode could not be resolved from USDA."
                    Text(msg)

                    if (state.scannedBarcode.isNotBlank()) {
                        Text(
                            text = "Scanned: ${state.scannedBarcode}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider()

                    Text(
                        "Create minimal food (name required):",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = state.barcodeFallbackCreateName,
                        onValueChange = onBarcodeFallbackCreateNameChange,
                        label = { Text("Food name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !conflict
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                    // ✅ New: Open the food that already owns the barcode
                    TextButton(
                        onClick = { alreadyAssignedFoodId?.let(onOpenFoodEditor) },
                        enabled = conflict
                    ) { Text("Open food") }

                    // ❌ Disabled when conflict (no reassignment here)
                    TextButton(
                        onClick = onBarcodeFallbackAssignExisting,
                        enabled = !conflict
                    ) { Text("Assign to existing") }

                    // ❌ Disabled when conflict
                    Button(
                        onClick = onBarcodeFallbackCreateMinimal,
                        enabled = !conflict && state.barcodeFallbackCreateName.trim().isNotBlank()
                    ) { Text("Create") }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissBarcodeFallback) { Text("Cancel") }
            }
        )
    }

    // ---------------- Barcode remap confirm ----------------
    val remap = state.barcodeRemapDialog
    if (remap != null) {
        val replaceLabel =
            if (remap.fromSource == BarcodeMappingSource.USDA) "Replace USDA mapping"
            else "Replace mapping"
        AlertDialog(
            onDismissRequest = { onConfirmBarcodeRemap(false) },
            title = { Text("Replace existing barcode mapping?") },
            text = {
                Text(
                    "Barcode ${remap.barcode} is already mapped to foodId=${remap.fromFoodId} " +
                            "(${remap.fromSource}). Replace with foodId=${remap.toFoodId}?"
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirmBarcodeRemap(true) }) { Text(replaceLabel) }
            },
            dismissButton = {
                TextButton(onClick = { onConfirmBarcodeRemap(false) }) { Text("Cancel") }
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
                onDelete = {showDeleteDialog = true }, // ✅ only uses passed callback
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
            val initialAmounts = remember { mutableStateMapOf<Long, String>() }
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
                    if (state.foodId != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                            Text("Assigned barcodes", style = MaterialTheme.typography.titleMedium)

                            if (state.assignedBarcodes.isEmpty()) {
                                Text(
                                    "None",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                state.assignedBarcodes.forEach { code ->

                                    var confirmOpen by remember(code) { mutableStateOf(false) }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = code,
                                            modifier = Modifier.weight(1f)
                                        )

                                        TextButton(onClick = { confirmOpen = true }) {
                                            Text("Unassign")
                                        }
                                    }

                                    if (confirmOpen) {
                                        AlertDialog(
                                            onDismissRequest = { confirmOpen = false },
                                            title = { Text("Unassign barcode?") },
                                            text = { Text("Remove barcode $code from this food?") },
                                            confirmButton = {
                                                TextButton(
                                                    onClick = {
                                                        confirmOpen = false
                                                        onUnassignBarcode(code)
                                                    }
                                                ) { Text("Unassign") }
                                            },
                                            dismissButton = {
                                                TextButton(
                                                    onClick = { confirmOpen = false }
                                                ) { Text("Cancel") }
                                            }
                                        )
                                    }
                                }
                            }
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
                        onMlPerServingChange = onMlPerServingChange,
                        basisType = state.basisType,

                        )
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

                sectionedByCategory(
                    items = state.nutrientRows,
                    categoryOf = { it.category },
                    keyOf = { index, row -> "${row.nutrientId}_$index" },
                    header = { category -> /* header */ }
                ) { _, row ->
                    val initial = initialAmounts.getOrPut(row.nutrientId) { row.amount }
                    val isChanged = row.amount != initial

                    NutrientRowEditor(
                        row = row,
                        isChanged = isChanged,
                        onAmountChange = { newValue -> onNutrientAmountChange(row.nutrientId, newValue) },
                        onRemove = {
                            initialAmounts.remove(row.nutrientId)
                            onRemoveNutrient(row.nutrientId)
                        },
                        isTablet = isTablet
                    )
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
                Log.d("Meow", "${state.name} : ${state.toString()}")
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

/**
 * Simple section header used in the editor form.
 *
 * @param title Primary label for the section.
 * @param subtitle Optional supporting text shown below [title].
 */
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

/**
 * Editable nutrient row.
 *
 * Shows the nutrient name/unit and a single editable amount field, plus a remove action.
 *
 * Notes:
 * - [isChanged] is purely a visual hint and does not mutate state.
 * - [onAmountChange] should update the backing row value in the state owner.
 * - [onRemove] should remove the nutrient row from the state owner.
 */
@Composable
private fun NutrientRowEditor(
    row: NutrientRowUi,
    isChanged: Boolean,
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
            supportingText = {
                if (isChanged) {
                    Text(
                        text = "Value edited",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            label = { Text("Amount")},
            singleLine = true,
            modifier = if (isTablet) Modifier.fillMaxWidth(0.6f) else Modifier.fillMaxWidth()
        )
    }
}

/**
 * One row in the “Add nutrient” search results list.
 *
 * Supports:
 * - Adding the nutrient to the food (disabled when already added)
 * - Opening alias management via an overflow menu
 */
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

/**
 * Serving / measurement section.
 *
 * Shows serving size + unit, then either:
 * - mass bridge (grams per serving-unit) for PER_100G style foods, or
 * - volume bridge (mL per serving) for PER_100ML style foods.
 *
 * Volume bridge behavior:
 * - `mlPerServingUnit` is the canonical “mL per 1 serving-unit”.
 * - The UI exposes an editable “mL per serving” total, computed as:
 *   `servingSize × mlPerServingUnit`.
 * - When the user edits the total mL, this section converts it back into the canonical bridge:
 *   `mlPerServingUnit = (totalMl / servingSize)`.
 *
 * @param basisType Determines whether the UI should prefer the volume bridge (PER_100ML) vs mass.
 */
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
            val servingSizeD = servingSize.toDoubleOrNull()?.takeIf { it > 0.0 }
            val bridgeD = mlPerServingUnit.toDoubleOrNull()?.takeIf { it > 0.0 }

            val mlPerServingComputed: Double? =
                if (servingSizeD != null && bridgeD != null) servingSizeD * bridgeD else null

            // Keep a local editable text so the field doesn't "snap" while the user types.
            var mlPerServingText by rememberSaveable { mutableStateOf("") }
            var mlPerServingFocused by rememberSaveable { mutableStateOf(false) }

            // When not actively editing, reflect the computed value.
            // (This keeps the field accurate after servingSize/unit edits or imports.)
            if (!mlPerServingFocused) {
                val next = mlPerServingComputed?.toString().orEmpty()
                if (mlPerServingText != next) mlPerServingText = next
            }

            // Editable TOTAL mL per serving
            OutlinedTextField(
                value = mlPerServingText,
                onValueChange = { newTotalText ->
                    mlPerServingText = newTotalText
                    val newTotal = newTotalText.toDoubleOrNull()?.takeIf { it > 0.0 }
                    val s = servingSizeD

                    if (newTotal != null && s != null) {
                        val newBridge = newTotal / s
                        onMlPerServingChange(newBridge.toString())
                    } else {
                        // allow clearing / partial typing without forcing updates
                        // (do nothing, or optionally set bridge blank)
                    }
                },
                label = { Text("mL per serving") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focus ->
                        mlPerServingFocused = focus.isFocused
                        if (!focus.isFocused) {
                            // Snap back to computed value on blur (canonical display).
                            mlPerServingText = mlPerServingComputed?.toString().orEmpty()
                        }
                    }
            )

            // Derived BRIDGE (read-only)
            if (servingSizeD != null && mlPerServingComputed != null) {
                OutlinedTextField(
                    value = mlPerServingUnit,
                    onValueChange = {},
                    enabled = false,
                    label = { Text("mL per 1 ${servingUnit.display}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
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



//@Composable
//private fun ServingSection(
//    servingSize: String,
//    servingUnit: ServingUnit,
//    gramsPerServingUnit: String,
//    mlPerServingUnit: String,
//    servingsPerPackage: String,
//    onServingSizeChange: (String) -> Unit,
//    onServingUnitChange: (ServingUnit) -> Unit,
//    onGramsPerServingChange: (String) -> Unit,
//    onMlPerServingChange: (String) -> Unit,
//    onServingsPerPackageChange: (String) -> Unit,
//    basisType: BasisType?,
//    isTablet: Boolean
//) {
//    val servingSizeD = servingSize.toDoubleOrNull()
//    val mlPerUnitD = mlPerServingUnit.toDoubleOrNull()
//
//    val computedMlPerServing: Double? =
//        if (servingSizeD != null && servingSizeD > 0 &&
//            mlPerUnitD != null && mlPerUnitD > 0
//        ) servingSizeD * mlPerUnitD else null
//
//    OutlinedTextField(
//        value = mlPerServingUnit,
//        onValueChange = onMlPerServingChange,
//        label = { Text("mL per 1 ${servingUnit.display}") }, // see helper below
//        singleLine = true,
//        modifier = Modifier.fillMaxWidth()
//    )
//
//    if (computedMlPerServing != null) {
//        OutlinedTextField(
//            value = computedMlPerServing.toString(),
//            onValueChange = {},
//            enabled = false,
//            label = { Text("mL per serving (${servingSize} ${servingUnit.display})") },
//            singleLine = true,
//            modifier = Modifier.fillMaxWidth()
//        )
//    }
//    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
//        Text(text = "Serving", style = MaterialTheme.typography.titleMedium)
//
//        if (isTablet) {
//            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
//                OutlinedTextField(
//                    value = servingSize,
//                    onValueChange = onServingSizeChange,
//                    label = { Text("Serving size") },
//                    singleLine = true,
//                    modifier = Modifier.weight(1f)
//                )
//                ServingUnitDropdown(
//                    value = servingUnit,
//                    onValueChange = onServingUnitChange,
//                    modifier = Modifier.weight(1f),
//                    options = ServingUnit.values().toList()
//                )
//            }
//        } else {
//            OutlinedTextField(
//                value = servingSize,
//                onValueChange = onServingSizeChange,
//                label = { Text("Serving size") },
//                singleLine = true,
//                modifier = Modifier.fillMaxWidth()
//            )
//            ServingUnitDropdown(
//                value = servingUnit,
//                onValueChange = onServingUnitChange,
//                modifier = Modifier.fillMaxWidth(),
//                options = ServingUnit.values().toList()
//            )
//        }
//
//        val useMlBridge = basisType == BasisType.PER_100ML || mlPerServingUnit.isNotBlank()
//
//        if (useMlBridge) {
//            OutlinedTextField(
//                value = mlPerServingUnit,
//                onValueChange = onMlPerServingChange,
//                label = { Text("mL per serving") },
//                singleLine = true,
//                modifier = Modifier.fillMaxWidth()
//            )
//        } else {
//            OutlinedTextField(
//                value = gramsPerServingUnit,
//                onValueChange = onGramsPerServingChange,
//                label = { Text("Grams per serving") },
//                singleLine = true,
//                modifier = Modifier.fillMaxWidth()
//            )
//        }
//
//
//        OutlinedTextField(
//            value = servingsPerPackage,
//            onValueChange = onServingsPerPackageChange,
//            label = { Text("Servings per package") },
//            singleLine = true,
//            modifier = Modifier.fillMaxWidth()
//        )
//    }
//}

@OptIn(ExperimentalMaterial3Api::class)
/**
 * Dropdown for selecting the serving unit (e.g., serving, cup, tbsp).
 *
 * Uses the Material3 exposed dropdown pattern.
 */
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

/** UI-only label helpers (intentionally local to this file). */

private fun NutrientCategory.labelForUi(): String = name.replace('_', ' ').lowercase()
    .replaceFirstChar { it.uppercase() }

/** Formats a [NutrientUnit] enum name into a compact lowercase label for UI. */
private fun NutrientUnit.labelForUi(): String = name.lowercase()

/** Convenience label for a nutrient row's category in UI text. */
private fun NutrientRowUi.categoryLabelForUi(): String = category.labelForUi()