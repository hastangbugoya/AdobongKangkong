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
import androidx.compose.material3.ModalBottomSheet
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
import com.example.adobongkangkong.ui.common.category.CategoryAssignmentSection
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
 * Serving bridge visibility rule:
 * - Deterministic units already carry their own conversion basis through [ServingUnit.asG] / [ServingUnit.asMl].
 * - Therefore:
 *   - hide manual grams-per-unit input when the selected serving unit has built-in mass grounding
 *   - hide manual mL-per-unit input when the selected serving unit has built-in volume grounding
 *   - hide BOTH bridge inputs when the selected serving unit is already deterministic
 * - Manual bridge inputs are only shown for units that are not already deterministic.
 * - This prevents the user from entering values the domain layer intentionally ignores for units like lb, oz, g, mL, or L.
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
 *
 * Category manager wiring:
 * - Assignment uses the shared [CategoryAssignmentSection].
 * - Global category maintenance (rename/delete) stays screen-local through the dialog below.
 * - The dialog is UI-local; actual rename/delete behavior is delegated through callbacks.
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
    onRenameCategory: ((Long, String) -> Unit)? = null,
    onDeleteCategory: ((Long) -> Unit)? = null,

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
    onUseScannedBarcodeForCurrentNewFood: () -> Unit,
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

    // Store price / store editor
    storePriceStoreNames: List<String> = emptyList(),
    onUpdateStorePrice: ((
        storeName: String,
        gramsText: String,
        gramsPriceText: String,
        mlText: String,
        mlPriceText: String
    ) -> Unit)? = null,
    onOpenCreateStoreEditor: (() -> Unit)? = null,
    onOpenEditStoreEditor: ((storeName: String) -> Unit)? = null,
    onDismissStoreEditor: (() -> Unit)? = null,
    onStoreEditorNameChange: ((String) -> Unit)? = null,
    onStoreEditorAddressChange: ((String) -> Unit)? = null,
    onStoreEditorContactChange: ((String) -> Unit)? = null,
    onConfirmStoreEditor: (() -> Unit)? = null,
    onDeleteStoreEditor: (() -> Unit)? = null,

    onRecomputeDisplayedNutrients: () -> Unit,
    onConfirmDiscardNutrientEditsAndRecompute: () -> Unit,
    onDismissDiscardNutrientEditsDialog: () -> Unit,
) {
    val (primaryNutrientRows, secondaryNutrientRows) = remember(state.nutrientRows) {
        state.nutrientRows.partition { it.code in EditorDefaultNutrients.codes }
    }

    var showMoreNutrients by rememberSaveable { mutableStateOf(false) }

    val showExitDialog = rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var showStorePriceSheet by remember { mutableStateOf(false) }
    var storePriceExpanded by remember { mutableStateOf(false) }
    var storeActionsExpanded by remember { mutableStateOf(false) }
    var selectedStoreName by rememberSaveable { mutableStateOf(storePriceStoreNames.firstOrNull().orEmpty()) }
    var gramsInput by rememberSaveable { mutableStateOf("") }
    var gramsPriceInput by rememberSaveable { mutableStateOf("") }
    var mlInput by rememberSaveable { mutableStateOf("") }
    var mlPriceInput by rememberSaveable { mutableStateOf("") }

    var actionMenuExpanded by remember { mutableStateOf(false) }

    var showCategoryManagerDialog by rememberSaveable { mutableStateOf(false) }
    var categoryBeingEditedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var categoryEditText by rememberSaveable { mutableStateOf("") }
    var categoryPendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    val multiplePackages = state.assignedBarcodes.size > 1

    fun requestExit() {
        if (state.hasUnsavedChanges && !state.isSaving) {
            showExitDialog.value = true
        } else {
            onBack()
        }
    }

    fun sanitizeDecimalInput(value: String): String {
        val filtered = value.filter { it.isDigit() || it == '.' }
        val firstDot = filtered.indexOf('.')
        return if (firstDot == -1) {
            filtered
        } else {
            filtered.substring(0, firstDot + 1) +
                    filtered.substring(firstDot + 1).replace(".", "")
        }
    }

    BackHandler { requestExit() }

    if (selectedStoreName.isBlank() && storePriceStoreNames.isNotEmpty()) {
        selectedStoreName = storePriceStoreNames.first()
    }

    if (
        state.storeEditor != null &&
        onDismissStoreEditor != null &&
        onStoreEditorNameChange != null &&
        onStoreEditorAddressChange != null &&
        onStoreEditorContactChange != null &&
        onConfirmStoreEditor != null
    ) {
        StoreEditDialog(
            state = state.storeEditor,
            onNameChange = onStoreEditorNameChange,
            onAddressChange = onStoreEditorAddressChange,
            onContactChange = onStoreEditorContactChange,
            onDismiss = onDismissStoreEditor,
            onConfirm = onConfirmStoreEditor,
            onDelete = if (state.storeEditor.canDelete && onDeleteStoreEditor != null) {
                onDeleteStoreEditor
            } else {
                null
            }
        )
    }

    if (showCategoryManagerDialog) {
        CategoryManagerDialog(
            categories = state.categories,
            editingCategoryId = categoryBeingEditedId,
            editText = categoryEditText,
            onEditTextChange = { categoryEditText = it },
            onStartEdit = { category ->
                categoryBeingEditedId = category.id
                categoryEditText = category.name
            },
            onCancelEdit = {
                categoryBeingEditedId = null
                categoryEditText = ""
            },
            onConfirmEdit = { categoryId ->
                onRenameCategory?.invoke(categoryId, categoryEditText)
                categoryBeingEditedId = null
                categoryEditText = ""
            },
            onRequestDelete = { category ->
                categoryPendingDelete = category.id
            },
            onDismiss = {
                showCategoryManagerDialog = false
                categoryBeingEditedId = null
                categoryEditText = ""
            }
        )
    }

    categoryPendingDelete?.let { categoryId ->
        val category = state.categories.firstOrNull { it.id == categoryId }
        if (category != null) {
            AlertDialog(
                onDismissRequest = { categoryPendingDelete = null },
                title = { Text("Delete category?") },
                text = {
                    Text(
                        "Delete category \"${category.name}\"?\n\n" +
                                "This removes the category itself and removes it from foods and recipes that use it."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteCategory?.invoke(categoryId)
                            categoryPendingDelete = null
                            if (categoryBeingEditedId == categoryId) {
                                categoryBeingEditedId = null
                                categoryEditText = ""
                            }
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { categoryPendingDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        } else {
            categoryPendingDelete = null
        }
    }

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
        val isNewFood = state.foodId == null

        AlertDialog(
            onDismissRequest = onDismissBarcodeFallback,
            title = { Text(if (conflict) "Barcode already in use" else "Barcode not found") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val msg = state.barcodeFallbackMessage
                        ?: if (conflict) {
                            "This barcode is already assigned in your database."
                        } else if (isNewFood) {
                            "USDA did not find a matching food for this barcode. You can still attach it to the food you are editing."
                        } else {
                            "This barcode could not be resolved from USDA."
                        }
                    Text(msg)

                    if (state.scannedBarcode.isNotBlank()) {
                        Text(
                            text = "Scanned: ${state.scannedBarcode}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!conflict && isNewFood) {
                        HorizontalDivider()
                        Text(
                            text = "Use this barcode for this new food?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "It will be assigned when you save the food.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (!conflict) {
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
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = onBarcodeFallbackAssignExisting,
                            enabled = !conflict
                        ) {
                            Text("Assign to existing")
                        }

                        Button(
                            onClick = onUseScannedBarcodeForCurrentNewFood,
                            enabled = !conflict && isNewFood
                        ) {
                            Text("Use barcode")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp)) // 👈 THIS is the key

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
                        text = "Barcode/package was adopted into this food."
                    )
                    Text(
                        text = "USDA item: ${pendingBackfillPrompt.candidateLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Only missing nutrients will be added. Existing nutrient values on this food will be kept."
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

    if (state.nutritionEditorStatus.showDiscardNutrientEditsDialog) {
        AlertDialog(
            onDismissRequest = onDismissDiscardNutrientEditsDialog,
            title = { Text("Discard edited nutrient values?") },
            text = {
                Text(
                    "Recompute will replace your manually edited per-serving nutrient values with values recalculated from canonical nutrition."
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmDiscardNutrientEditsAndRecompute) {
                    Text("Recompute")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDiscardNutrientEditsDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showStorePriceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStorePriceSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Store price",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "Enter a package quantity and total price. The app will normalize and save an approximate estimate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    ExposedDropdownMenuBox(
                        expanded = storePriceExpanded,
                        onExpandedChange = { storePriceExpanded = !storePriceExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedStoreName,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("Store") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = storePriceExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )

                        ExposedDropdownMenu(
                            expanded = storePriceExpanded,
                            onDismissRequest = { storePriceExpanded = false }
                        ) {
                            storePriceStoreNames.forEach { storeName ->
                                DropdownMenuItem(
                                    text = { Text(storeName) },
                                    onClick = {
                                        selectedStoreName = storeName
                                        storePriceExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    BoxWithConstraints {
                        IconButton(
                            onClick = { storeActionsExpanded = true }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.circle_ellipsis_vertical),
                                contentDescription = "Store actions",
                                modifier = Modifier.size(AppIconSize.CardAction)
                            )
                        }

                        DropdownMenu(
                            expanded = storeActionsExpanded,
                            onDismissRequest = { storeActionsExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("New") },
                                onClick = {
                                    storeActionsExpanded = false
                                    onOpenCreateStoreEditor?.invoke()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Edit") },
                                enabled = selectedStoreName.isNotBlank(),
                                onClick = {
                                    storeActionsExpanded = false
                                    if (selectedStoreName.isNotBlank()) {
                                        onOpenEditStoreEditor?.invoke(selectedStoreName)
                                    }
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Delete") },
                                enabled = selectedStoreName.isNotBlank(),
                                onClick = {
                                    storeActionsExpanded = false
                                    if (selectedStoreName.isNotBlank()) {
                                        onOpenEditStoreEditor?.invoke(selectedStoreName)
                                    }
                                }
                            )
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Weight-based package",
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = gramsInput,
                    onValueChange = { newValue ->
                        gramsInput = sanitizeDecimalInput(newValue)
                    },
                    label = { Text("Quantity in grams") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = gramsPriceInput,
                    onValueChange = { newValue ->
                        gramsPriceInput = sanitizeDecimalInput(newValue)
                    },
                    label = { Text("Total price") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                Text(
                    text = "Volume-based package",
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = mlInput,
                    onValueChange = { newValue ->
                        mlInput = sanitizeDecimalInput(newValue)
                    },
                    label = { Text("Quantity in mL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = mlPriceInput,
                    onValueChange = { newValue ->
                        mlPriceInput = sanitizeDecimalInput(newValue)
                    },
                    label = { Text("Total price") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Enter either path or both. The app will not convert grams and mL between each other.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = {
                        onUpdateStorePrice?.invoke(
                            selectedStoreName,
                            gramsInput,
                            gramsPriceInput,
                            mlInput,
                            mlPriceInput
                        )
                        showStorePriceSheet = false
                    },
                    enabled = selectedStoreName.isNotBlank() && onUpdateStorePrice != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Update")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
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
                },
                actions = {
                    BoxWithConstraints {
                        IconButton(
                            onClick = { actionMenuExpanded = true }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.circle_ellipsis_vertical),
                                contentDescription = "More actions",
                                modifier = Modifier.size(AppIconSize.CardAction)
                            )
                        }

                        DropdownMenu(
                            expanded = actionMenuExpanded,
                            onDismissRequest = { actionMenuExpanded = false }
                        ) {
                            if (state.foodId != null && onDeleteFood != null) {
                                DropdownMenuItem(
                                    text = { Text("Delete food") },
                                    onClick = {
                                        actionMenuExpanded = false
                                        showDeleteDialog = true
                                    }
                                )
                            }

                            if (state.foodId != null) {
                                DropdownMenuItem(
                                    text = { Text("Store price") },
                                    onClick = {
                                        actionMenuExpanded = false
                                        showStorePriceSheet = true
                                    }
                                )
                            }

                            if (onRenameCategory != null && onDeleteCategory != null) {
                                DropdownMenuItem(
                                    text = { Text("Manage categories") },
                                    onClick = {
                                        actionMenuExpanded = false
                                        showCategoryManagerDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            FoodEditorBottomBar(
                isSaving = state.isSaving,
                errorMessage = state.errorMessage ?: state.barcodeActionMessage,
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
                        } else if (state.scannedBarcode.isNotBlank()) {
                            PendingBarcodeNotice(
                                barcode = state.scannedBarcode,
                                modifier = Modifier.fillMaxWidth()
                            )
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

                        if (isExistingFood && state.scannedBarcode.isNotBlank()) {
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
                    CategoryAssignmentSection(
                        title = "Categories",
                        subtitle = "Create categories and assign them to this food.",
                        categories = state.categories,
                        selectedCategoryIds = state.selectedCategoryIds,
                        newCategoryName = state.newCategoryName,
                        onCategoryCheckedChange = onCategoryCheckedChange,
                        onNewCategoryNameChange = onNewCategoryNameChange,
                        onCreateCategory = onCreateCategory,
                        onOpenManageCategories = if (
                            onRenameCategory != null &&
                            onDeleteCategory != null &&
                            state.categories.isNotEmpty()
                        ) {
                            { showCategoryManagerDialog = true }
                        } else {
                            null
                        }
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

                if (state.hasPendingRecompute) {
                    item(key = "recompute_warning") {
                        NeedsRecomputeBanner()
                    }

                    item(key = "recompute_button") {
                        Button(
                            onClick = onRecomputeDisplayedNutrients,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Recompute nutrient values")
                        }
                    }
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
private fun CategoryManagerDialog(
    categories: List<FoodCategoryUi>,
    editingCategoryId: Long?,
    editText: String,
    onEditTextChange: (String) -> Unit,
    onStartEdit: (FoodCategoryUi) -> Unit,
    onCancelEdit: () -> Unit,
    onConfirmEdit: (Long) -> Unit,
    onRequestDelete: (FoodCategoryUi) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage categories") },
        text = {
            if (categories.isEmpty()) {
                Text("No categories yet.")
            } else {
                LazyColumn {
                    items(categories, key = { it.id }) { category ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (editingCategoryId == category.id) {
                                OutlinedTextField(
                                    value = editText,
                                    onValueChange = onEditTextChange,
                                    label = { Text("Category name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = onCancelEdit) {
                                        Text("Cancel")
                                    }
                                    TextButton(
                                        onClick = { onConfirmEdit(category.id) },
                                        enabled = editText.trim().isNotBlank()
                                    ) {
                                        Text("Save")
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = category.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f)
                                    )

                                    TextButton(
                                        onClick = { onStartEdit(category) }
                                    ) {
                                        Text("Rename")
                                    }

                                    TextButton(
                                        onClick = { onRequestDelete(category) },
                                        enabled = !category.isSystem
                                    ) {
                                        Text("Delete")
                                    }
                                }
                            }

                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {}
    )
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
private fun NeedsRecomputeBanner() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
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
                modifier = Modifier.size(AppIconSize.CardAction)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Serving changed",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Nutrient values may be out of sync. Recompute to update per-serving values.",
                    style = MaterialTheme.typography.bodySmall
                )
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

        val isDeterministicUnit = servingUnit.asG != null || servingUnit.asMl != null
        val showGramsBridge = !isDeterministicUnit
        val showMlBridge = !isDeterministicUnit

        val servingSizeD = servingSize.toDoubleOrNull()?.takeIf { it > 0.0 }
        val gramsPerUnitD = gramsPerServingUnit.toDoubleOrNull()?.takeIf { it > 0.0 }
        val mlPerUnitD = mlPerServingUnit.toDoubleOrNull()?.takeIf { it > 0.0 }

        val gramsPerServingComputed: Double? =
            if (servingSizeD != null && gramsPerUnitD != null) servingSizeD * gramsPerUnitD else null

        val mlPerServingComputed: Double? =
            if (servingSizeD != null && mlPerUnitD != null) servingSizeD * mlPerUnitD else null

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

        if (showMlBridge) {
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
        }

        if (showGramsBridge) {
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

            Button(
                onClick = onSave,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isSaving) "Saving…" else "Save")
            }
        }
    }
}

@Composable
private fun PendingBarcodeNotice(
    barcode: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 0.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Pending barcode",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = barcode,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "This barcode will be assigned when you save this food.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
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
    val sizeValue = servingSize.trim().toDoubleOrNull()?.takeIf { it > 0.0 }

    val gramsPerUnitManual = gramsPerServingUnit.trim().toDoubleOrNull()?.takeIf { it > 0.0 }
    val mlPerUnitManual = mlPerServingUnit.trim().toDoubleOrNull()?.takeIf { it > 0.0 }

    val gramsPerUnit = servingUnit.asG ?: gramsPerUnitManual
    val mlPerUnit = servingUnit.asMl ?: mlPerUnitManual

    val amountPart = when {
        basisType == BasisType.PER_100G && sizeValue != null && gramsPerUnit != null -> {
            val grams = sizeValue * gramsPerUnit
            " Current serving = $sizeText ${servingUnit.display} (${grams.toUiCompactNumber()} g)."
        }

        basisType == BasisType.PER_100ML && sizeValue != null && mlPerUnit != null -> {
            val ml = sizeValue * mlPerUnit
            " Current serving = $sizeText ${servingUnit.display} (${ml.toUiCompactNumber()} mL)."
        }

        else -> " Current serving = $sizeText ${servingUnit.display}."
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
    return if (this == whole) {
        whole.toLong().toString()
    } else {
        "%,.2f".format(this).replace(",", "").trimEnd('0').trimEnd('.')
    }
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
 * Serving bridge visibility rule:
 * - Do not show manual grams bridge input for units with built-in mass grounding (`asG != null`).
 * - Do not show manual mL bridge input for units with built-in volume grounding (`asMl != null`).
 * - Do not show either bridge input when the selected unit is already deterministic.
 * - The domain layer already treats deterministic units as grounded, so showing editable bridge fields here is misleading.
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