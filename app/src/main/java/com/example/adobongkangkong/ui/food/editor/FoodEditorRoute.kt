package com.example.adobongkangkong.ui.food.editor

import android.util.Log
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.ui.camera.BannerCaptureController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEditorRoute(
    foodId: Long?,
    initialName: String?,
    bannerRefreshTick: Int,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: FoodEditorViewModel = hiltViewModel(),
    bannerCaptureController: BannerCaptureController,
) {
    val state by viewModel.state.collectAsState()
    val didDelete by viewModel.didDelete.collectAsState()
    LaunchedEffect(Unit) {
        Log.d("Meow", "FOOD_EDITOR_ROUTE vm=${System.identityHashCode(viewModel)}")
    }

    val aliasName by viewModel.aliasSheetNutrientName.collectAsState()
    val aliasMessage by viewModel.aliasSheetMessage.collectAsState()
    val aliases by viewModel.selectedAliases.collectAsState()

    LaunchedEffect(foodId, initialName) {
        viewModel.load(foodId = foodId, initialName = initialName)
    }

    LaunchedEffect(didDelete) {
        if (didDelete) onBack()
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

        onToggleFavorite = viewModel::onFavoriteChange,
        onToggleEatMore = viewModel::onEatMoreChange,
        onToggleLimit = viewModel::onLimitChange,

        onSave = {
            viewModel.save { _ -> onDone() }
        },
        onDeleteFood = { viewModel.deleteFood() },

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
 *   - loads the food/editor data based on nav args
 *   - passes BannerCaptureController + refresh tick into FoodEditorScreen
 *   - hosts BarcodeScannerSheet bottom sheet
 *
 * Key integration rules:
 * - Banner capture:
 *   - FoodEditorScreen calls bannerCaptureController.open(foodId) ONLY when foodId != null.
 *   - MainScreen’s BannerCaptureHost handles the actual sheet overlay.
 *
 * Barcode scanner:
 * - This route shows ModalBottomSheet when state.isBarcodeScannerOpen.
 * - Scanner must update VM state (barcode → search json → import → load(foodId)).
 * - IMPORTANT: BarcodeScannerSheet must unbind CameraX on dispose to avoid breaking banner capture.
 *
 * Debug sanity:
 * - We should see exactly ONE FoodEditorViewModel instance for the route.
 * - If state updates but UI doesn’t reflect, check collectAsState + load() gating.
 */
