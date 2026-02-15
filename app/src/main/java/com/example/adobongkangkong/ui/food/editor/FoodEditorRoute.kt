package com.example.adobongkangkong.ui.food.editor

import android.util.Log
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.ui.camera.BannerCaptureController
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEditorRoute(
    foodId: Long?,
    initialName: String?,
    initialBarcode: String? = null,
    bannerRefreshTick: Int,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onAssignBarcodeToExisting: (String) -> Unit = {},
    viewModel: FoodEditorViewModel = hiltViewModel(),
    bannerCaptureController: BannerCaptureController,
    onOpenFoodEditor: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val didDelete by viewModel.didDelete.collectAsState()
    val assignExistingBarcode by viewModel.assignBarcodeToExistingBarcode.collectAsState()

    LaunchedEffect(Unit) {
        Log.d("Meow", "FOOD_EDITOR_ROUTE vm=${System.identityHashCode(viewModel)}")
    }

    val aliasName by viewModel.aliasSheetNutrientName.collectAsState()
    val aliasMessage by viewModel.aliasSheetMessage.collectAsState()
    val aliases by viewModel.selectedAliases.collectAsState()

// FoodEditorRoute.kt (inside FoodEditorRoute composable)

// Load + initialBarcode sequencing (single source of truth)
    val didApplyInitialBarcode = androidx.compose.runtime.remember(foodId, initialName, initialBarcode) {
        mutableStateOf(false)
    }

    LaunchedEffect(foodId, initialName, initialBarcode) {
        // 1) Always load editor data for this route args
        viewModel.load(foodId = foodId, initialName = initialName, force = true)

        // 2) Apply initialBarcode once (only if provided)
        val code = initialBarcode?.trim().orEmpty()
        if (code.isBlank()) return@LaunchedEffect
        if (didApplyInitialBarcode.value) return@LaunchedEffect
        didApplyInitialBarcode.value = true

        if (foodId == null) {
            // New food: treat as scan (USDA flow)
            viewModel.onBarcodeScanned(code)
        } else {
            // Edit existing: wait until VM state reflects this foodId, then assign
            snapshotFlow { state.foodId }
                .filter { it == foodId }
                .first()

            viewModel.assignBarcodeToCurrentFood(code)
        }
    }

    LaunchedEffect(didDelete) {
        if (didDelete) onBack()
    }

    LaunchedEffect(assignExistingBarcode) {
        val code = assignExistingBarcode?.trim().orEmpty()
        if (code.isNotBlank()) {
            onAssignBarcodeToExisting(code)
            viewModel.consumeAssignBarcodeToExistingRequest()
        }
    }
    FoodEditorScreen(
        state = state,
        onBack = onBack,

        onNameChange = viewModel::onNameChange,
        onBrandChange = viewModel::onBrandChange,
        onServingSizeChange = viewModel::onServingSizeChange,
        onServingUnitChange = viewModel::onServingUnitChange,
        onGramsPerServingChange = viewModel::onGramsPerServingChange,
        onServingsPerPackageChange = viewModel::onServingsPerPackageChange,

        onNutrientAmountChange = viewModel::onNutrientAmountChange,
        onRemoveNutrient = viewModel::removeNutrientRow,

        onNutrientSearchQueryChange = viewModel::onNutrientSearchQueryChange,
        onAddNutrientFromSearch = { nutrientId ->
            val item = state.nutrientSearchResults.firstOrNull { it.id == nutrientId }
                ?: return@FoodEditorScreen
            viewModel.addNutrient(item)
        },

        onDeleteFood = { viewModel.deleteFood() },
        onHardDeleteFood = { viewModel.hardDeleteFoodPermanently() },

        onToggleFavorite = viewModel::onFavoriteChange,
        onToggleEatMore = viewModel::onEatMoreChange,
        onToggleLimit = viewModel::onLimitChange,

        onSave = {
            viewModel.save { _ -> onDone() }
        },

        aliasSheetNutrientName = aliasName,
        aliasSheetAliases = aliases,
        aliasSheetMessage = aliasMessage,
        onOpenAliasSheet = { id, name -> viewModel.openAliasSheet(id, name) },
        onAddAlias = { viewModel.addAlias(it) },
        onDeleteAlias = { viewModel.deleteAlias(it) },
        onDismissAliasSheet = { viewModel.closeAliasSheet() },

        bannerCaptureController = bannerCaptureController,
        bannerRefreshTick = bannerRefreshTick,
        onOpenBarcodeScanner = viewModel::openBarcodeScanner,
        onCloseBarcodeScanner = viewModel::closeBarcodeScanner,
        onBarcodeScanned = viewModel::onBarcodeScanned,
        onPickBarcodeCandidate = viewModel::onPickBarcodeCandidate,
        onUnassignBarcode = viewModel::unassignBarcode,
        onPickBasisType = viewModel::onPickBasisType,
        onDismissGroundingDialog = viewModel::closeGroundingDialog,
        mlPerServingUnit = state.mlPerServingUnit,
        onMlPerServingChange = viewModel::onMlPerServingChange,
        basisType = state.basisType,
        onDismissBarcodeFallback = viewModel::dismissBarcodeFallback,
        onBarcodeFallbackAssignExisting = viewModel::barcodeFallbackAssignExisting,
        onBarcodeFallbackCreateNameChange = viewModel::onBarcodeFallbackCreateNameChange,
        onBarcodeFallbackCreateMinimal = viewModel::barcodeFallbackCreateMinimalFood,
        onConfirmBarcodeRemap = viewModel::onConfirmBarcodeRemap,
        onBarcodeFallbackOpenAssignedFood = { foodId ->
            viewModel.dismissBarcodeFallback()
            onOpenFoodEditor(foodId)
        },
        onOpenFoodEditor = { foodId ->
            viewModel.dismissBarcodeFallback()
            onOpenFoodEditor(foodId)
        },
        onDismissBarcodeCollision = viewModel::dismissBarcodeCollision,
        onOpenExistingFromCollision = viewModel::openExistingFromCollision,
        onRemapFromCollisionProceedImport = viewModel::remapFromCollisionProceedImport,
        onResolveBarcodeCollision = { action ->
            when (action) {
                BarcodeCollisionAction.Cancel -> viewModel.dismissBarcodeCollision()
                BarcodeCollisionAction.OpenExisting -> viewModel.openExistingFromCollision()
                BarcodeCollisionAction.Replace -> viewModel.remapFromCollisionProceedImport()
            }
        }
    )

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (state.isBarcodeScannerOpen && state.barcodePickItems.size <= 1) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeBarcodeScanner,
            sheetState = sheetState
        ) {
            BarcodeScannerSheet(
                onClose = viewModel::closeBarcodeScanner,
                onBarcode = { code ->
                    viewModel.onBarcodeScanned(code)
                }
            )
        }
    }
}

/**
 * FOR-FUTURE-ME (FoodEditorRoute)
 *
 * Purpose:
 * - Route-level wrapper:
 *   - owns FoodEditorViewModel via hiltViewModel()
 *   - loads the food/editor data based on
 */
