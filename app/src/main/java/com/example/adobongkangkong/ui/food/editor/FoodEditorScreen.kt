package com.example.adobongkangkong.ui.food.editor

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.util.Log
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
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
import com.example.adobongkangkong.domain.usda.model.CollisionReason
import com.example.adobongkangkong.ui.camera.BannerCaptureController
import com.example.adobongkangkong.ui.common.food.GoalFlagsSection
import com.example.adobongkangkong.ui.theme.AppIconSize

/**
 * FoodEditorScreen (stateless)
 *
 * UI-only form for creating/editing a Food.
 *
 * Design rules:
 * - **No ViewModel access**: all state is provided via [state] and all actions are delegated via callbacks.
 * - **All modal UI lives here** (dialogs / sheets) so it remains driven by [state] and local UI flags.
 *
 * Nutrient editor contract:
 * - Nutrient input fields shown by this screen are **per current serving UI values**.
 * - They are not raw canonical PER_100G / PER_100ML values.
 * - Canonical storage remains a viewmodel/domain concern and must not leak into labels that look like editable UI semantics.
 * - The current serving definition comes from:
 *   - serving size + serving unit
 *   - and, when needed, grams/mL backing shown in the Serving section
 *
 * USDA interpretation prompt:
 * - When [state.pendingUsdaInterpretationPrompt] is non-null, the app does not yet assume whether
 *   the chosen USDA nutrient values should be treated as per-100 or per-serving.
 * - This screen shows the raw macro preview and lets the user choose the interpretation.
 *
 * Merge wiring:
 * - Food merge remains a route/viewmodel concern.
 * - This screen only exposes an optional [onMergeFood] callback when editing an existing food.
 * - The actual picker/navigation flow is owned by the caller so the screen stays stateless.
 * - Merge is intentionally placed near the bottom of the scrollable content, not in the persistent bottom bar.
 *
 * USDA backfill wiring:
 * - Barcode/package adoption remains separate from nutrient backfill.
 * - When [state.pendingUsdaBackfillPrompt] is non-null, this screen offers a follow-up prompt:
 *   "Fill missing nutrients from USDA?"
 * - The screen does not decide nutrient logic; it only delegates confirmation/cancel actions.
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

    // Categories
    onCategoryCheckedChange: (Long, Boolean) -> Unit,
    onNewCategoryNameChange: (String) -> Unit,
    onCreateCategory: () -> Unit,

    // Flags
    onToggleFavorite: (Boolean) -> Unit,
    onToggleEatMore: (Boolean) -> Unit,
    onToggleLimit: (Boolean) -> Unit,

    // Save/Delete
    onSave: () -> Unit,
    onDeleteFood: (() -> Unit)? = null,
    onHardDeleteFood: (() -> Unit)? = null,
    onMergeFood: (() -> Unit)? = null,

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
    onDismissBarcodeCollision: () -> Unit,
    onOpenExistingFromCollision: () -> Unit,
    onRemapFromCollisionProceedImport: () -> Unit,

    onResolveBarcodeCollision: (BarcodeCollisionAction) -> Unit,

    // Package override editor
    onOpenBarcodePackageEditor: (String) -> Unit,
    onDismissBarcodePackageEditor: () -> Unit,
    onBarcodePackageOverrideServingsPerPackageChange: (String) -> Unit,
    onBarcodePackageOverrideHouseholdServingTextChange: (String) -> Unit,
    onBarcodePackageOverrideServingSizeChange: (String) -> Unit,
    onBarcodePackageOverrideServingUnitChange: (ServingUnit?) -> Unit,
    onSaveBarcodePackageOverrides: () -> Unit,

    // USDA interpretation prompt
    onConfirmUsdaInterpretationPrompt: (UsdaNutrientInterpretationChoice) -> Unit,
    onDismissUsdaInterpretationPrompt: () -> Unit,

    // USDA backfill prompt/result
    onConfirmUsdaBackfillPrompt: () -> Unit,
    onDismissUsdaBackfillPrompt: () -> Unit,
    onDismissUsdaBackfillMessage: () -> Unit,

    // Optional dismiss action for needs-fix banner
    onDismissNeedsFixBanner: (() -> Unit)? = null,
) {
    val (primaryNutrientRows, secondaryNutrientRows) = remember(state.nutrientRows) {
        state.nutrientRows.partition { it.code in EditorDefaultNutrients.codes }
    }

    var showMoreNutrients by rememberSaveable { mutableStateOf(false) }

    val showExitDialog = rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val multiplePackages = state.assignedBarcodes.size > 1

    fun requestExit() {
        if (state.hasUnsavedChanges && !state.isSaving) {
            showExitDialog.value = true
        } else {
            onBack()
        }
    }

    BackHandler { requestExit() }

    if (state.barcodePickItems.isNotEmpty()) {
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
                    TextButton(
                        onClick = { alreadyAssignedFoodId?.let(onOpenFoodEditor) },
                        enabled = conflict
                    ) { Text("Open food") }

                    TextButton(
                        onClick = onBarcodeFallbackAssignExisting,
                        enabled = !conflict
                    ) { Text("Assign to existing") }

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

    val collision = state.barcodeCollisionDialog
    if (collision != null) {
        AlertDialog(
            onDismissRequest = onDismissBarcodeCollision,
            title = { Text("Barcode conflict") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Barcode: ${collision.barcode}")

                    when (collision.reason) {
                        CollisionReason.ExistingUserAssignedMapping -> {
                            Text("This barcode is already assigned to a manually created food.")
                        }

                        CollisionReason.ExistingUsdaFdcIdMismatch -> {
                            Text(
                                "This barcode is mapped to a different USDA item.\n\n" +
                                        "Incoming:\n${collision.incomingLabel}\n\n" +
                                        "Existing foodId: ${collision.existingFoodId}"
                            )
                        }

                        CollisionReason.ExistingMappingCorruptMissingFood -> {
                            Text("Existing barcode mapping is invalid.")
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onOpenExistingFromCollision) { Text("Open existing") }
                    TextButton(onClick = onRemapFromCollisionProceedImport) { Text("Replace") }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissBarcodeCollision) { Text("Cancel") }
            }
        )
    }

    val packageEditor = state.barcodePackageEditor
    if (packageEditor != null) {
        AlertDialog(
            onDismissRequest = onDismissBarcodePackageEditor,
            title = { Text("Edit package") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Barcode: ${packageEditor.barcode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = packageEditor.overrideServingsPerPackage,
                        onValueChange = onBarcodePackageOverrideServingsPerPackageChange,
                        label = { Text("Override servings per package") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = packageEditor.overrideHouseholdServingText,
                        onValueChange = onBarcodePackageOverrideHouseholdServingTextChange,
                        label = { Text("Override household serving text") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = packageEditor.overrideServingSize,
                        onValueChange = onBarcodePackageOverrideServingSizeChange,
                        label = { Text("Override serving size") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    NullableServingUnitDropdown(
                        value = packageEditor.overrideServingUnit,
                        onValueChange = onBarcodePackageOverrideServingUnitChange,
                        modifier = Modifier.fillMaxWidth(),
                        options = ServingUnit.values().toList()
                    )
                }
            },
            confirmButton = {
                Button(onClick = onSaveBarcodePackageOverrides) {
                    Text("Save package")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissBarcodePackageEditor) { Text("Cancel") }
            }
        )
    }

    val pendingInterpretationPrompt = state.pendingUsdaInterpretationPrompt
    if (pendingInterpretationPrompt != null) {
        AlertDialog(
            onDismissRequest = onDismissUsdaInterpretationPrompt,
            title = { Text("How should USDA nutrients be interpreted?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "The USDA item was imported, but the nutrient values may represent either per 100 or per serving."
                    )

                    Text(
                        "USDA item: ${pendingInterpretationPrompt.candidateLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    pendingInterpretationPrompt.servingText
                        ?.takeIf { it.isNotBlank() }
                        ?.let { servingText ->
                            Text(
                                "USDA serving text: $servingText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                    HorizontalDivider()

                    Text("Raw USDA macro preview:")

                    Text(
                        text = buildString {
                            append("Calories: ")
                            append(pendingInterpretationPrompt.calories?.toUiNumber() ?: "—")
                            append(" • Carbs: ")
                            append(pendingInterpretationPrompt.carbs?.toUiNumber() ?: "—")
                            append(" g")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = buildString {
                            append("Protein: ")
                            append(pendingInterpretationPrompt.protein?.toUiNumber() ?: "—")
                            append(" g • Fat: ")
                            append(pendingInterpretationPrompt.fat?.toUiNumber() ?: "—")
                            append(" g")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()

                    Text(
                        "Choose how the app should treat these USDA nutrient values for this food."
                    )
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onConfirmUsdaInterpretationPrompt(
                                UsdaNutrientInterpretationChoice.PER_100
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Treat USDA nutrients as per 100")
                    }

                    OutlinedButton(
                        onClick = {
                            onConfirmUsdaInterpretationPrompt(
                                UsdaNutrientInterpretationChoice.PER_SERVING
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Treat USDA nutrients as per serving")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissUsdaInterpretationPrompt) {
                    Text("Decide later")
                }
            }
        )
    }

    val pendingBackfillPrompt = state.pendingUsdaBackfillPrompt
    if (pendingBackfillPrompt != null) {
        AlertDialog(
            onDismissRequest = onDismissUsdaBackfillPrompt,
            title = { Text("Fill missing nutrients from USDA?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Barcode/package was adopted into this food."
                    )
                    Text(
                        "USDA item: ${pendingBackfillPrompt.candidateLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Only missing nutrients will be added. Existing nutrient values on this food will be kept."
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmUsdaBackfillPrompt) {
                    Text("Fill missing")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissUsdaBackfillPrompt) {
                    Text("Not now")
                }
            }
        )
    }

    val backfillMessage = state.usdaBackfillMessage
    if (backfillMessage != null) {
        AlertDialog(
            onDismissRequest = onDismissUsdaBackfillMessage,
            title = { Text("USDA nutrient backfill") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(backfillMessage.message)
                    Text(
                        "Added: ${backfillMessage.insertedCount} • Already present: ${backfillMessage.skippedExistingCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissUsdaBackfillMessage) {
                    Text("OK")
                }
            },
            dismissButton = {}
        )
    }

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
                errorMessage = state.errorMessage ?: state.barcodeActionMessage,
                showDelete = (onDeleteFood != null && state.foodId != null),
                onDelete = { showDeleteDialog = true },
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
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state.needsFix && !state.fixMessage.isNullOrBlank() && !state.fixBannerDismissed) {
                    item(key = "needs_fix_banner") {
                        NeedsFixBannerRow(
                            message = state.fixMessage!!,
                            onDismiss = onDismissNeedsFixBanner
                        )
                    }
                }

                item {
                    val context = LocalContext.current

                    val bannerOwnerId = state.foodId
                    val canCaptureBanner = bannerOwnerId != null

                    val bannerFile = if (canCaptureBanner) {
                        remember(bannerOwnerId) {
                            com.example.adobongkangkong.feature.camera.FoodImageStorage(context)
                                .bannerJpegFile(bannerOwnerId)
                        }
                    } else {
                        null
                    }

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
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 1f)
                            .clipToBounds()
                    ) {
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
                    val isExistingFood = state.foodId != null
                    val addBarcodeLabel = when {
                        !isExistingFood -> "Scan barcode"
                        state.assignedBarcodes.isEmpty() -> "Add barcode"
                        else -> "Add another barcode"
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Barcodes", style = MaterialTheme.typography.titleMedium)

                        if (isExistingFood) {
                            if (state.assignedBarcodes.isEmpty()) {
                                Text("None", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                state.assignedBarcodes.forEach { barcodeRow ->
                                    var confirmUnassign by remember(barcodeRow.barcode) { mutableStateOf(false) }

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(text = barcodeRow.barcode)

                                                val packageSummary = barcodeOverrideSummary(barcodeRow)
                                                if (packageSummary.isNotBlank()) {
                                                    Text(
                                                        text = packageSummary,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            TextButton(
                                                onClick = { onOpenBarcodePackageEditor(barcodeRow.barcode) },
                                                enabled = multiplePackages
                                            ) {
                                                Text("Edit package")
                                            }

                                            TextButton(onClick = { confirmUnassign = true }) {
                                                Text("Unassign")
                                            }
                                        }

                                        if (confirmUnassign) {
                                            AlertDialog(
                                                onDismissRequest = { confirmUnassign = false },
                                                title = { Text("Unassign barcode?") },
                                                text = { Text("Remove barcode ${barcodeRow.barcode} from this food?") },
                                                confirmButton = {
                                                    TextButton(
                                                        onClick = {
                                                            confirmUnassign = false
                                                            onUnassignBarcode(barcodeRow.barcode)
                                                        }
                                                    ) { Text("Unassign") }
                                                },
                                                dismissButton = {
                                                    TextButton(onClick = { confirmUnassign = false }) { Text("Cancel") }
                                                }
                                            )
                                        }
                                    }

                                    HorizontalDivider()
                                }
                            }
                        } else {
                            Text(
                                text = "Scan a barcode to search USDA or start barcode-based import.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenBarcodeScanner,
                            enabled = !state.isSaving
                        ) {
                            Text(addBarcodeLabel)
                        }

                        if (state.scannedBarcode.isNotBlank()) {
                            Text(
                                text = "Scanned: ${state.scannedBarcode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                item(key = "serving_section") {
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
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(
                            title = "Categories",
                            subtitle = "Create categories and assign them to this food."
                        )

                        if (state.categories.isEmpty()) {
                            Text(
                                text = "No categories yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            state.categories.forEach { category ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = state.selectedCategoryIds.contains(category.id),
                                        onCheckedChange = { checked ->
                                            onCategoryCheckedChange(category.id, checked)
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(category.name)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = state.newCategoryName,
                                onValueChange = onNewCategoryNameChange,
                                label = { Text("New category") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = onCreateCategory) {
                                Text("Add")
                            }
                        }
                    }
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
                        subtitle = "Amounts below are per current serving, not raw PER 100g / PER 100mL values."
                    )
                }

                item {
                    Text(
                        text = nutrientEditorContextText(
                            servingSize = state.servingSize,
                            servingUnit = state.servingUnit,
                            gramsPerServingUnit = state.gramsPerServingUnit,
                            mlPerServingUnit = state.mlPerServingUnit,
                            basisType = state.basisType
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(
                    items = primaryNutrientRows,
                    key = { row -> row.nutrientId }
                ) { row ->
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

                if (secondaryNutrientRows.isNotEmpty()) {
                    item {
                        TextButton(onClick = { showMoreNutrients = !showMoreNutrients }) {
                            Text(if (showMoreNutrients) "Hide extra nutrients" else "Show more nutrients")
                        }
                    }
                }

                if (showMoreNutrients) {
                    items(
                        items = secondaryNutrientRows,
                        key = { row -> row.nutrientId }
                    ) { row ->
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
                }

                item { HorizontalDivider() }

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

                if (state.foodId != null && onMergeFood != null) {
                    item {
                        HorizontalDivider()
                    }

                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Advanced",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Text(
                                text = "Merge this food into another existing food. This keeps history but retires this food.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedButton(
                                onClick = onMergeFood,
                                enabled = !state.isSaving
                            ) {
                                Text("Merge into another food")
                            }
                        }
                    }
                }

                Log.d("Meow", "FoodEditorScreen state dump: ${state.name} : $state")

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
private fun NeedsFixBannerRow(
    message: String,
    onDismiss: (() -> Unit)?,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.employee_handbook),
                contentDescription = null,
                tint = Color.Red,
                modifier = Modifier.size(AppIconSize.CardAction),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            if (onDismiss != null) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss, contentPadding = PaddingValues(0.dp)) {
                    Text("Dismiss")
                }
            }
        }
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
private fun NutrientRowEditor(
    row: NutrientRowUi,
    isChanged: Boolean,
    onAmountChange: (String) -> Unit,
    onRemove: () -> Unit,
    isTablet: Boolean
) {
    val supportAlias = remember(row.aliases, row.name) {
        row.aliases.firstOrNull { alias ->
            alias.isNotBlank() && !alias.equals(row.name, ignoreCase = true)
        }
    }

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

                if (!supportAlias.isNullOrBlank()) {
                    Text(
                        text = supportAlias,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = row.unit.labelForUi(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onRemove) {
                Icon(
                    painter = painterResource(R.drawable.trash),
                    contentDescription = "Remove nutrient",
                    modifier = Modifier.size(AppIconSize.CardAction)
                )
            }
        }

        OutlinedTextField(
            value = row.amount,
            onValueChange = onAmountChange,
            supportingText = {
                if (isChanged) {
                    Text(
                        text = "Per-serving value edited",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            label = { Text("Amount per serving") },
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
    val supportAlias = remember(item.aliases, item.name) {
        item.aliases.firstOrNull { alias ->
            alias.isNotBlank() && !alias.equals(item.name, ignoreCase = true)
        }
    }

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

            if (!supportAlias.isNullOrBlank()) {
                Text(
                    text = supportAlias,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

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
            Icon(
                painter = painterResource(R.drawable.circle_ellipsis_vertical),
                contentDescription = "More",
                modifier = Modifier.size(AppIconSize.CardAction)
            )
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

        val useMlBridge = when (basisType) {
            BasisType.PER_100ML -> true
            BasisType.PER_100G -> false
            BasisType.USDA_REPORTED_SERVING, null -> {
                mlPerServingUnit.isNotBlank() && gramsPerServingUnit.isBlank()
            }
        }

        val servingSizeD = servingSize.toDoubleOrNull()?.takeIf { it > 0.0 }
        val gramsPerUnitD = gramsPerServingUnit.toDoubleOrNull()?.takeIf { it > 0.0 }
        val mlPerUnitD = mlPerServingUnit.toDoubleOrNull()?.takeIf { it > 0.0 }

        val gramsPerServingComputed: Double? =
            if (servingSizeD != null && gramsPerUnitD != null) servingSizeD * gramsPerUnitD else null

        val mlPerServingComputed: Double? =
            if (servingSizeD != null && mlPerUnitD != null) servingSizeD * mlPerUnitD else null

        // 🔴 FIX: stable helper text (no layout jump)
        @Composable
        fun StableSupportingText(text: String) {
            Text(
                text = text,
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            )
        }

        if (useMlBridge) {
            OutlinedTextField(
                value = mlPerServingUnit,
                onValueChange = onMlPerServingChange,
                label = { Text("mL per 1 ${servingUnit.display}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    val helper = when {
                        servingSizeD != null && mlPerServingComputed != null ->
                            "Current serving = ${servingSizeD.toUiCompactNumber()} ${servingUnit.display} = ${mlPerServingComputed.toUiCompactNumber()} mL"
                        else ->
                            "Used when this serving unit is volume-grounded (PER 100mL)."
                    }
                    StableSupportingText(helper)
                }
            )
        } else {
            OutlinedTextField(
                value = gramsPerServingUnit,
                onValueChange = onGramsPerServingChange,
                label = { Text("Grams per 1 ${servingUnit.display}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    val helper = when {
                        servingSizeD != null && gramsPerServingComputed != null ->
                            "Current serving = ${servingSizeD.toUiCompactNumber()} ${servingUnit.display} = ${gramsPerServingComputed.toUiCompactNumber()} g"
                        else ->
                            "Used when this serving unit is mass-grounded (PER 100g)."
                    }
                    StableSupportingText(helper)
                }
            )
        }

        Text(
            text = nutrientEditorContextText(
                servingSize = servingSize,
                servingUnit = servingUnit,
                gramsPerServingUnit = gramsPerServingUnit,
                mlPerServingUnit = mlPerServingUnit,
                basisType = basisType
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NullableServingUnitDropdown(
    value: ServingUnit?,
    onValueChange: (ServingUnit?) -> Unit,
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
            value = value?.display ?: "Use food default",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Override serving unit") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Use food default") },
                onClick = {
                    expanded = false
                    onValueChange(null)
                }
            )

            options.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit.display) },
                    onClick = {
                        expanded = false
                        onValueChange(unit)
                    }
                )
            }
        }
    }
}

/**
 * Bottom action bar for the food editor.
 *
 * Merge support:
 * - Merge is intentionally not surfaced here.
 * - Rare or destructive advanced actions belong in the scrollable content area instead of the persistent bottom bar.
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

private fun barcodeOverrideSummary(row: AssignedBarcodeUi): String {
    val parts = mutableListOf<String>()

    row.overrideServingSize?.let { size ->
        val servingText = row.overrideServingUnit?.let { unit ->
            "Serving: $size ${unit.display}"
        } ?: "Serving size: $size"
        parts += servingText
    } ?: row.overrideServingUnit?.let { unit ->
        parts += "Serving unit: ${unit.display}"
    }

    row.overrideServingsPerPackage?.let {
        parts += "$it per package"
    }

    row.overrideHouseholdServingText
        ?.takeIf { it.isNotBlank() }
        ?.let {
            parts += it
        }

    if (parts.isEmpty()) {
        parts += when (row.source) {
            BarcodeMappingSource.USDA -> "USDA barcode"
            BarcodeMappingSource.USER_ASSIGNED -> "User-assigned barcode"
        }
    }

    return parts.joinToString(" • ")
}

private fun nutrientEditorContextText(
    servingSize: String,
    servingUnit: ServingUnit,
    gramsPerServingUnit: String,
    mlPerServingUnit: String,
    basisType: BasisType?
): String {
    val sizeText = servingSize.trim().ifBlank { "1" }
    val gramsPerUnit = gramsPerServingUnit.trim().toDoubleOrNull()?.takeIf { it > 0.0 }
    val mlPerUnit = mlPerServingUnit.trim().toDoubleOrNull()?.takeIf { it > 0.0 }
    val sizeValue = servingSize.trim().toDoubleOrNull()?.takeIf { it > 0.0 }

    val amountPart = when {
        basisType == BasisType.PER_100ML && sizeValue != null && mlPerUnit != null ->
            "Current serving = $sizeText ${servingUnit.display} (${sizeValue * mlPerUnit} mL)."
        basisType == BasisType.PER_100G && sizeValue != null && gramsPerUnit != null ->
            "Current serving = $sizeText ${servingUnit.display} (${sizeValue * gramsPerUnit} g)."
        basisType == BasisType.PER_100ML ->
            "Current serving = $sizeText ${servingUnit.display}."
        basisType == BasisType.PER_100G ->
            "Current serving = $sizeText ${servingUnit.display}."
        else ->
            "Current serving = $sizeText ${servingUnit.display}."
    }

    val basisPart = when (basisType) {
        BasisType.PER_100G -> " Storage stays canonical PER 100g."
        BasisType.PER_100ML -> " Storage stays canonical PER 100mL."
        BasisType.USDA_REPORTED_SERVING -> " Storage stays per serving."
        null -> ""
    }

    return "Nutrient amounts you edit below are per current serving.$amountPart$basisPart"
}

private fun Double.toUiNumber(): String {
    val whole = toLong().toDouble()
    return if (this == whole) whole.toLong().toString() else toString()
}

private fun Double.toUiCompactNumber(): String {
    val whole = toLong().toDouble()
    return if (this == whole) whole.toLong().toString() else "%,.2f".format(this).replace(",", "").trimEnd('0').trimEnd('.')
}

private fun NutrientCategory.labelForUi(): String =
    name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

private fun NutrientUnit.labelForUi(): String = name.lowercase()

/**
 * FUTURE-YOU / FUTURE-AI NOTE
 *
 * Food editor nutrient semantics in this screen are intentionally explicit:
 * - Editable nutrient fields are labeled as per-serving UI values.
 * - This screen should not visually imply that editable nutrient fields are raw PER_100G / PER_100ML values.
 * - Canonical storage basis remains internal and is only surfaced here as explanatory text.
 *
 * USDA interpretation prompt rule:
 * - When USDA nutrient semantics are unclear, this screen must ask the user how to interpret them.
 * - The prompt shows raw USDA macro preview values only.
 * - The screen does not apply conversions; it only routes the user's choice.
 *
 * Why this matters:
 * - USDA-imported foods are often stored canonically as PER_100G / PER_100ML.
 * - Some branded USDA search results look like label-serving values instead.
 * - Without a user choice, canonical-looking numbers can be misread or mis-imported.
 *
 * Screen rule:
 * - Always make it obvious that nutrient inputs are tied to the current serving definition shown above.
 * - Do not add grams↔mL conversions here.
 * - Do not move canonical scaling math into this file.
 */